package edu.university.profile;

import javax.sql.DataSource;
import java.net.URI;
import java.sql.*;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

/** JDBC persistence; no shared Connection, Statement, or per-request mutable state. */
public final class ProfileDao {
    private final DataSource dataSource;
    private final String publicProfileUrl;

    public static final class Conflict extends Exception {
        public Conflict(String message) { super(message); }
    }

    public ProfileDao(DataSource dataSource, String publicProfileUrl) {
        URI uri = URI.create(publicProfileUrl);
        if (!"https".equals(uri.getScheme()) || uri.getHost() == null
                || uri.getRawQuery() != null || uri.getRawFragment() != null)
            throw new IllegalArgumentException("Configure a trusted HTTPS profile URL.");
        this.dataSource = dataSource;
        this.publicProfileUrl = publicProfileUrl;
    }

    public Profile lookup(String subject, String rawToken) throws SQLException {
        String token = Validation.lookupToken(rawToken);
        String sql = "SELECT * FROM students WHERE sso_subject=? "
                   + "AND (enrollment_no=? OR mobile_e164=?)";
        try (Connection c = dataSource.getConnection(); PreparedStatement p = prepare(c, sql)) {
            p.setString(1, subject); p.setString(2, token);
            p.setString(3, Validation.mobileCandidate(token));
            try (ResultSet rs = p.executeQuery()) { return rs.next() ? view(rs) : null; }
        }
    }

    public Profile find(String subject, long id) throws SQLException {
        try (Connection c = dataSource.getConnection();
             PreparedStatement p = prepare(c, "SELECT * FROM students WHERE student_id=? AND sso_subject=?")) {
            p.setLong(1, id); p.setString(2, subject);
            try (ResultSet rs = p.executeQuery()) { return rs.next() ? view(rs) : null; }
        }
    }

    public void update(String subject, long id, long expectedVersion,
                       String rawAddress, String rawMobile, String rawEmail)
            throws SQLException, Conflict {
        // Validate again at the persistence boundary; browser validation is only convenience.
        String address = Validation.address(rawAddress), mobile = Validation.mobile(rawMobile);
        String email = Validation.email(rawEmail);
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Profile old;
                byte[] hash;
                Timestamp expiry;
                try (PreparedStatement p = prepare(c,
                        "SELECT * FROM students WHERE student_id=? AND sso_subject=? FOR UPDATE")) {
                    p.setLong(1, id); p.setString(2, subject);
                    try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next()) throw new Conflict("Profile unavailable; perform the lookup again.");
                        old = view(rs); hash = rs.getBytes("email_token_hash");
                        expiry = rs.getTimestamp("email_token_expires");
                    }
                }
                if (old.getVersion() != expectedVersion)
                    throw new Conflict("The profile changed in another tab. Review the refreshed values and retry.");

                String pending = old.getPendingEmail(), token = null;
                if (email.equals(old.getEmail()) && old.isEmailVerified()) {
                    pending = null; hash = null; expiry = null;
                } else if (!email.equals(pending) || expiry == null
                        || !expiry.toInstant().isAfter(Instant.now())) {
                    pending = email; token = Validation.randomToken(); hash = Validation.hash(token);
                    expiry = Timestamp.from(Instant.now().plus(15, ChronoUnit.MINUTES));
                }
                String sql = "UPDATE students SET communication_address=?, mobile_e164=?, mobile_verified=?, "
                    + "pending_email=?, email_token_hash=?, email_token_expires=?, row_version=row_version+1 "
                    + "WHERE student_id=? AND sso_subject=? AND row_version=?";
                try (PreparedStatement p = prepare(c, sql)) {
                    p.setString(1, address); p.setString(2, mobile);
                    p.setBoolean(3, mobile.equals(old.getMobile()) && old.isMobileVerified());
                    p.setString(4, pending); p.setBytes(5, hash); p.setTimestamp(6, expiry);
                    p.setLong(7, id); p.setString(8, subject); p.setLong(9, expectedVersion);
                    if (p.executeUpdate() != 1) throw new Conflict("Profile changed; reload and retry.");
                }
                audit(c, id, subject, "PROFILE_UPDATED", expectedVersion + 1);
                if (token != null) {
                    queue(c, id, pending, "Confirm your university email address",
                        "Sign in to your university account, then confirm this change within 15 minutes:\n"
                        + publicProfileUrl + "?verify=" + token
                        + "\nIf you did not request this change, ignore this message and contact support.");
                }
                if (old.isEmailVerified() && (!mobile.equals(old.getMobile()) || token != null)) {
                    queue(c, id, old.getEmail(), "University profile change requested",
                        "A change to your profile contact details was requested. "
                        + "If this was not you, contact the university helpdesk immediately.");
                }
                c.commit();
            } catch (SQLException | Conflict | RuntimeException e) {
                rollback(c, e); throw e;
            }
        }
    }

    public void confirmEmail(String subject, String rawToken) throws SQLException, Conflict {
        byte[] tokenHash = Validation.hash(Validation.verificationToken(rawToken));
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                Profile old;
                try (PreparedStatement p = prepare(c, "SELECT * FROM students WHERE sso_subject=? "
                        + "AND email_token_hash=? AND email_token_expires>UTC_TIMESTAMP(6) FOR UPDATE")) {
                    p.setString(1, subject); p.setBytes(2, tokenHash);
                    try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next() || rs.getString("pending_email") == null)
                            throw new Conflict("Invalid, expired, or already used verification link.");
                        old = view(rs);
                    }
                }
                try (PreparedStatement p = prepare(c, "UPDATE students SET email=pending_email, "
                        + "email_verified=TRUE, pending_email=NULL, email_token_hash=NULL, "
                        + "email_token_expires=NULL, row_version=row_version+1 "
                        + "WHERE student_id=? AND sso_subject=? AND row_version=? "
                        + "AND email_token_hash=? AND email_token_expires>UTC_TIMESTAMP(6)")) {
                    p.setLong(1, old.getId()); p.setString(2, subject);
                    p.setLong(3, old.getVersion()); p.setBytes(4, tokenHash);
                    if (p.executeUpdate() != 1) throw new Conflict("Verification expired; request a new link.");
                }
                audit(c, old.getId(), subject, "EMAIL_VERIFIED", old.getVersion() + 1);
                if (old.isEmailVerified()) {
                    queue(c, old.getId(), old.getEmail(), "University email address changed",
                        "Your university contact email address was changed after confirmation. "
                        + "If this was not you, contact the university helpdesk immediately.");
                }
                c.commit();
            } catch (SQLException | Conflict | RuntimeException e) {
                rollback(c, e); throw e;
            }
        }
    }

    private static PreparedStatement prepare(Connection c, String sql) throws SQLException {
        PreparedStatement p = c.prepareStatement(sql); p.setQueryTimeout(5); return p;
    }
    private static Profile view(ResultSet rs) throws SQLException {
        return new Profile(rs.getLong("student_id"), rs.getLong("row_version"),
            rs.getString("enrollment_no"), rs.getString("full_name"), rs.getString("programme"),
            rs.getString("communication_address"), rs.getString("mobile_e164"),
            rs.getString("email"), rs.getString("pending_email"),
            rs.getBoolean("email_verified"), rs.getBoolean("mobile_verified"));
    }
    private static void audit(Connection c, long id, String subject, String event, long version)
            throws SQLException {
        try (PreparedStatement p = prepare(c, "INSERT INTO profile_audit "
                + "(student_id,actor_subject,event_type,resulting_version) VALUES (?,?,?,?)")) {
            p.setLong(1,id); p.setString(2,subject); p.setString(3,event); p.setLong(4,version);
            p.executeUpdate();
        }
    }
    private static void queue(Connection c, long id, String recipient, String subject, String body)
            throws SQLException {
        try (PreparedStatement p = prepare(c,
                "INSERT INTO mail_outbox (student_id,recipient,subject,body) VALUES (?,?,?,?)")) {
            p.setLong(1,id); p.setString(2,recipient); p.setString(3,subject); p.setString(4,body);
            p.executeUpdate();
        }
    }
    private static void rollback(Connection c, Exception original) {
        try { c.rollback(); } catch (SQLException secondary) { original.addSuppressed(secondary); }
    }
}
