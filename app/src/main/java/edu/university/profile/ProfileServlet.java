package edu.university.profile;

import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.*;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;
import java.io.IOException;
import java.io.Serializable;
import java.sql.SQLException;
import java.time.Instant;
import java.util.UUID;

@WebServlet(urlPatterns="/profile", loadOnStartup=1)
public final class ProfileServlet extends HttpServlet {
    private ProfileDao dao;
    private static final String GRANT = "profile.lookupGrant", CSRF = "profile.csrf";
    private record LookupGrant(String subject, long studentId, Instant until) implements Serializable { }

    @Override public void init() throws ServletException {
        try {
            InitialContext jndi = new InitialContext();
            try {
                dao = new ProfileDao((DataSource) jndi.lookup("java:comp/env/jdbc/UniversityDB"),
                    (String) jndi.lookup("java:comp/env/profilePublicUrl"));
            } finally { jndi.close(); }
        } catch (NamingException | IllegalArgumentException e) {
            throw new ServletException("Required profile resources are not configured.", e);
        }
    }

    @Override protected void service(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        req.setCharacterEncoding("UTF-8"); res.setCharacterEncoding("UTF-8");
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("Referrer-Policy", "no-referrer");
        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("Content-Security-Policy", "default-src 'none'; form-action 'self'; "
            + "frame-ancestors 'none'; base-uri 'none'");
        if (!req.isSecure()) { res.sendError(403, "HTTPS is required."); return; }
        if (req.getUserPrincipal() == null) { res.sendError(401, "Sign in at the local lab login page."); return; }
        if (!req.isUserInRole("STUDENT")) { res.sendError(403); return; }
        super.service(req, res);
    }

    @Override protected void doGet(HttpServletRequest req, HttpServletResponse res)
            throws ServletException, IOException {
        HttpSession session = req.getSession(true);
        req.setAttribute("csrf", csrf(session));
        synchronized (session) {
            req.setAttribute("message", session.getAttribute("profile.flash"));
            session.removeAttribute("profile.flash");
        }
        try {
            if (req.getParameter("verify") != null) {
                // GET only displays confirmation. A CSRF-protected POST consumes the token.
                req.setAttribute("verificationToken", Validation.verificationToken(req.getParameter("verify")));
            } else {
                LookupGrant grant = grant(session, req.getUserPrincipal().getName());
                if (grant != null) {
                    Profile profile = dao.find(grant.subject(), grant.studentId());
                    if (profile == null) session.removeAttribute(GRANT);
                    req.setAttribute("profile", profile);
                }
            }
        } catch (IllegalArgumentException e) {
            req.setAttribute("message", e.getMessage());
        } catch (SQLException e) {
            req.setAttribute("message", databaseError(e));
        }
        req.getRequestDispatcher("/WEB-INF/profile.jsp").forward(req, res);
    }

    @Override protected void doPost(HttpServletRequest req, HttpServletResponse res)
            throws IOException {
        HttpSession session = req.getSession(false);
        if (session == null || !Validation.sameToken((String)session.getAttribute(CSRF), req.getParameter("csrf"))) {
            res.sendError(403, "Invalid form token; reload the page."); return;
        }
        String subject = req.getUserPrincipal().getName();
        String action = req.getParameter("action"), message;
        try {
            if ("lookup".equals(action)) {
                session.removeAttribute(GRANT);
                Profile found = dao.lookup(subject, req.getParameter("lookup"));
                if (found == null) message = "No matching profile is available for your signed-in account.";
                else {
                    session.setAttribute(GRANT, new LookupGrant(subject, found.getId(), Instant.now().plusSeconds(600)));
                    message = "Profile verified. You may edit the contact details.";
                }
            } else if ("save".equals(action) || "verifyEmail".equals(action)) {
                // LOCAL LAB ONLY: LocalLoginFilter binds this proof to a real password login.
                // This is recent password authentication, not university SSO or MFA.
                Object proof = req.getAttribute("lab.passwordReauthenticatedAt");
                Instant now = Instant.now();
                if (!(proof instanceof Instant at) || at.isBefore(now.minusSeconds(300)) || at.isAfter(now.plusSeconds(30))) {
                    res.sendError(403, "Sign in again at /student-profile/login, then retry."); return;
                }
                if ("save".equals(action)) {
                    LookupGrant grant = grant(session, subject);
                    if (grant == null) throw new IllegalArgumentException("Perform the profile lookup again.");
                    dao.update(subject, grant.studentId(), Validation.version(req.getParameter("version")),
                        req.getParameter("address"), req.getParameter("mobile"), req.getParameter("email"));
                    message = "Profile saved. Any new email address remains pending until confirmed. "
                        + "A changed mobile remains unverified and cannot be used for authentication.";
                } else {
                    dao.confirmEmail(subject, req.getParameter("token"));
                    message = "Email verified successfully. Look up your profile to review it.";
                }
            } else throw new IllegalArgumentException("Unknown form action.");
        } catch (IllegalArgumentException | ProfileDao.Conflict e) {
            message = e.getMessage();
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062 && "23000".equals(e.getSQLState()))
                message = "That mobile number is already registered. Use another number.";
            else message = databaseError(e);
        }
        session.setAttribute("profile.flash", message);
        res.setStatus(HttpServletResponse.SC_SEE_OTHER);
        res.setHeader("Location", req.getContextPath() + "/profile");
    }

    private static String csrf(HttpSession session) {
        synchronized (session) {
            String token = (String) session.getAttribute(CSRF);
            if (token == null) { token = Validation.randomToken(); session.setAttribute(CSRF, token); }
            return token;
        }
    }
    private static LookupGrant grant(HttpSession session, String subject) {
        Object value = session.getAttribute(GRANT);
        if (value instanceof LookupGrant g && g.subject().equals(subject) && g.until().isAfter(Instant.now())) return g;
        session.removeAttribute(GRANT); return null;
    }
    private String databaseError(SQLException e) {
        String reference = UUID.randomUUID().toString();
        // Do not log bound PII, verification tokens, connection credentials or raw SQL messages.
        getServletContext().log("Profile database error " + reference + "; SQLState=" + e.getSQLState());
        return "The operation could not be completed. Reload before retrying. Reference: " + reference;
    }
}
