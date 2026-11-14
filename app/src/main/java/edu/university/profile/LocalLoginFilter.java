package edu.university.profile;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.Principal;
import java.security.SecureRandom;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;

/** Password authentication for the separate local lab only; this is not a university SSO adapter. */
@WebFilter("/*")
public final class LocalLoginFilter implements Filter {
    private static final String AUTH = "lab.auth", CSRF = "lab.loginCsrf";
    private static final String ATTEMPTS = "lab.loginAttempts", NOTICE = "lab.loginNotice";
    private static final int ITERATIONS = 600_000, MAX_ATTEMPTS = 5;
    private static final long WINDOW_SECONDS = 900, RECENT_SECONDS = 300;
    private static final SecureRandom RANDOM = new SecureRandom();
    private final byte[] dummySalt = new byte[16];
    private byte[] dummyHash;
    private DataSource dataSource;

    private record Auth(String subject, Instant at) implements Serializable { }
    private record Attempts(Instant startedAt, int count) implements Serializable { }
    private record Credentials(String subject, byte[] salt, byte[] hash, int iterations) { }

    @Override public void init(FilterConfig config) throws ServletException {
        try {
            InitialContext context = new InitialContext();
            try {
                dataSource = (DataSource) context.lookup("java:comp/env/jdbc/UniversityDB");
            } finally { context.close(); }
            if (dataSource == null) throw new ServletException("UniversityDB data source is missing.");
            RANDOM.nextBytes(dummySalt);
            char[] randomPassword = Validation.randomToken().toCharArray();
            try { dummyHash = derive(randomPassword, dummySalt, ITERATIONS); }
            finally { Arrays.fill(randomPassword, '\0'); }
        } catch (NamingException | GeneralSecurityException | ClassCastException e) {
            throw new ServletException("Local lab login requires jdbc/UniversityDB and PBKDF2WithHmacSHA256.", e);
        }
    }

    @Override public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest req = (HttpServletRequest) request;
        HttpServletResponse res = (HttpServletResponse) response;
        req.setCharacterEncoding("UTF-8");
        res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("Pragma", "no-cache");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Security-Policy", "default-src 'none'; form-action 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'");
        if (!req.isSecure()) { res.sendError(403, "HTTPS is required."); return; }

        String path = req.getServletPath();
        if ("/login".equals(path)) { login(req, res); return; }
        if ("/logout".equals(path)) { logout(req, res); return; }
        HttpSession session = req.getSession(false);
        Auth auth = auth(session);
        if (auth == null) { redirect(req, res, "/login"); return; }
        if (path.isEmpty() || "/".equals(path)) { redirect(req, res, "/profile"); return; }

        String action = "POST".equals(req.getMethod()) ? req.getParameter("action") : null;
        if ("/profile".equals(path) && ("save".equals(action) || "verifyEmail".equals(action))
                && auth.at().isBefore(Instant.now().minusSeconds(RECENT_SECONDS))) {
            session.setAttribute(NOTICE, "Sign in again to renew the five-minute password confirmation, then retry.");
            redirect(req, res, "/login"); return;
        }
        // This timestamp comes exclusively from successful password verification in this filter.
        // It proves recent LOCAL PASSWORD authentication; it makes no MFA or university SSO claim.
        req.setAttribute("lab.passwordReauthenticatedAt", auth.at());
        Principal principal = () -> auth.subject();
        HttpServletRequest authenticated = new HttpServletRequestWrapper(req) {
            @Override public Principal getUserPrincipal() { return principal; }
            @Override public String getRemoteUser() { return auth.subject(); }
            @Override public String getAuthType() { return "LOCAL_LAB_PASSWORD"; }
            @Override public boolean isUserInRole(String role) { return "STUDENT".equals(role); }
        };
        chain.doFilter(authenticated, res);
    }

    private void login(HttpServletRequest req, HttpServletResponse res) throws IOException, ServletException {
        HttpSession session = req.getSession(true);
        if ("GET".equals(req.getMethod())) {
            String message;
            synchronized (session) {
                Object notice = session.getAttribute(NOTICE);
                message = notice instanceof String s ? s : "Enter a local lab username and password.";
                session.removeAttribute(NOTICE);
            }
            renderLogin(req, res, session, message); return;
        }
        if (!"POST".equals(req.getMethod())) { methodNotAllowed(res); return; }

        // Serialize attempts within this session, including the password calculation, to bound concurrency.
        synchronized (session) {
            if (!Validation.sameToken((String) session.getAttribute(CSRF), req.getParameter("csrf"))) {
                res.sendError(403, "Invalid form token; reload the sign-in page."); return;
            }
            clearAuthentication(session);
            Instant now = Instant.now();
            Object previous = session.getAttribute(ATTEMPTS);
            Attempts attempts = previous instanceof Attempts a ? a : new Attempts(now, 0);
            if (attempts.startedAt().plusSeconds(WINDOW_SECONDS).isBefore(now)) attempts = new Attempts(now, 0);
            if (attempts.count() >= MAX_ATTEMPTS) {
                res.setStatus(429);
                long remaining = Math.max(1, attempts.startedAt().plusSeconds(WINDOW_SECONDS).getEpochSecond() - now.getEpochSecond());
                res.setHeader("Retry-After", Long.toString(remaining));
                renderLogin(req, res, session, "Too many sign-in attempts. Wait up to 15 minutes before retrying.");
                return;
            }
            session.setAttribute(ATTEMPTS, new Attempts(attempts.startedAt(), attempts.count() + 1));
            String username = req.getParameter("username"), rawPassword = req.getParameter("password");
            boolean validInput = username != null && username.matches("[A-Za-z0-9._-]{1,64}")
                && rawPassword != null && !rawPassword.isEmpty() && rawPassword.length() <= 128;
            char[] password = validInput ? rawPassword.toCharArray() : new char[0];
            byte[] actual = null;
            try {
                Credentials credentials = validInput ? credentials(username) : null;
                byte[] salt = credentials == null ? dummySalt : credentials.salt();
                byte[] expected = credentials == null ? dummyHash : credentials.hash();
                int iterations = credentials == null ? ITERATIONS : credentials.iterations();
                actual = derive(password, salt, iterations);
                boolean matches = MessageDigest.isEqual(expected, actual);
                if (credentials == null || !validInput || !matches) {
                    res.setStatus(401);
                    renderLogin(req, res, session, "Username or password is incorrect."); return;
                }
                req.changeSessionId();
                session.setAttribute(AUTH, new Auth(credentials.subject(), Instant.now()));
                session.removeAttribute(ATTEMPTS);
                session.setAttribute(CSRF, Validation.randomToken());
                redirect(req, res, "/profile");
            } catch (SQLException | GeneralSecurityException e) {
                req.getServletContext().log("Local lab authentication unavailable; exception type=" + e.getClass().getSimpleName());
                res.sendError(503, "Sign-in is temporarily unavailable. Check the local database configuration.");
            } finally {
                Arrays.fill(password, '\0');
                if (actual != null) Arrays.fill(actual, (byte) 0);
            }
        }
    }

    private Credentials credentials(String username) throws SQLException {
        String sql = "SELECT sso_subject,password_salt,password_hash,iterations FROM lab_credentials WHERE username=?";
        try (Connection connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet rows = statement.executeQuery()) {
                if (!rows.next()) return null;
                String subject = rows.getString(1);
                byte[] salt = rows.getBytes(2), hash = rows.getBytes(3);
                int iterations = rows.getInt(4);
                if (subject == null || subject.isBlank() || subject.length() > 128
                        || salt == null || salt.length != 16 || hash == null || hash.length != 32
                        || iterations != ITERATIONS)
                    throw new SQLException("Invalid local credential record.");
                return new Credentials(subject, salt, hash, iterations);
            }
        }
    }

    private void logout(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        if (auth(session) == null) { redirect(req, res, "/login"); return; }
        if ("GET".equals(req.getMethod())) {
            res.setContentType("text/html;charset=UTF-8");
            PrintWriter out = res.getWriter();
            pageStart(out, "Sign out");
            out.println("<h1>Sign out</h1><form method=\"post\" action=\"" + html(req.getContextPath() + "/logout") + "\">");
            out.println("<input type=\"hidden\" name=\"csrf\" value=\"" + html(csrf(session)) + "\">");
            out.println("<button type=\"submit\">Confirm sign out</button></form></main></body></html>");
            return;
        }
        if (!"POST".equals(req.getMethod())) { methodNotAllowed(res); return; }
        synchronized (session) {
            if (!Validation.sameToken((String) session.getAttribute(CSRF), req.getParameter("csrf"))) {
                res.sendError(403, "Invalid form token; reload the sign-out page."); return;
            }
            session.invalidate();
        }
        redirect(req, res, "/login");
    }

    private static Auth auth(HttpSession session) {
        if (session == null) return null;
        Object value = session.getAttribute(AUTH);
        if (!(value instanceof Auth a) || a.at().isAfter(Instant.now())) return null;
        return a;
    }

    private static void clearAuthentication(HttpSession session) {
        session.removeAttribute(AUTH);
        session.removeAttribute("profile.lookupGrant");
        session.removeAttribute("profile.csrf");
        session.removeAttribute("profile.flash");
    }

    private static String csrf(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(CSRF);
            if (token == null) { token = Validation.randomToken(); session.setAttribute(CSRF, token); }
            return token;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) throws GeneralSecurityException {
        PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, 256);
        try { return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded(); }
        finally { spec.clearPassword(); }
    }

    private static void renderLogin(HttpServletRequest req, HttpServletResponse res, HttpSession session, String message)
            throws IOException {
        res.setContentType("text/html;charset=UTF-8");
        PrintWriter out = res.getWriter();
        pageStart(out, "Local lab sign-in");
        out.println("<h1>Student Profile — Local Lab</h1><p>" + html(message) + "</p>");
        out.println("<form method=\"post\" action=\"" + html(req.getContextPath() + "/login") + "\">");
        out.println("<input type=\"hidden\" name=\"csrf\" value=\"" + html(csrf(session)) + "\">");
        out.println("<p><label>Username <input name=\"username\" maxlength=\"64\" autocomplete=\"username\" required></label></p>");
        out.println("<p><label>Password <input type=\"password\" name=\"password\" maxlength=\"128\" autocomplete=\"current-password\" required></label></p>");
        out.println("<button type=\"submit\">Sign in</button></form>");
        out.println("<p>Use the local demo credentials supplied in the README. This lab uses password authentication.</p>");
        out.println("</main></body></html>");
    }

    private static void pageStart(PrintWriter out, String title) {
        out.println("<!doctype html><html lang=\"en\"><head><meta charset=\"UTF-8\"><meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>"
            + html(title) + "</title></head><body><main>");
    }

    private static String html(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
            .replace("\"", "&quot;").replace("'", "&#39;");
    }

    private static void redirect(HttpServletRequest req, HttpServletResponse res, String path) {
        res.setStatus(HttpServletResponse.SC_SEE_OTHER);
        res.setHeader("Location", req.getContextPath() + path);
    }

    private static void methodNotAllowed(HttpServletResponse res) throws IOException {
        res.setHeader("Allow", "GET, POST");
        res.sendError(405);
    }
}
