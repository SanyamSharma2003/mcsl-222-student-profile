package edu.university.profile;

import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.mail.Session;
import jakarta.mail.Transport;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Small Tomcat demo worker. A production installation may run this consumer separately. */
@WebListener
public final class OutboxMailer implements ServletContextListener {
    private ScheduledExecutorService executor;
    private DataSource dataSource;
    private Session mail;
    private ServletContext context;

    @Override public void contextInitialized(ServletContextEvent event) {
        context = event.getServletContext();
        try {
            InitialContext jndi = new InitialContext();
            try {
                dataSource = (DataSource)jndi.lookup("java:comp/env/jdbc/UniversityDB");
                mail = (Session)jndi.lookup("java:comp/env/mail/UniversityMail");
            } finally { jndi.close(); }
            new InternetAddress(mail.getProperty("mail.from"), true).validate();
            executor = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "profile-mail-outbox"); t.setDaemon(true); return t;
            });
            executor.scheduleWithFixedDelay(this::tick, 5, 10, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new IllegalStateException("Required database/mail resources are not configured.", e);
        }
    }

    private void tick() {
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try {
                long id; String recipient, subject, body;
                try (PreparedStatement p = c.prepareStatement("SELECT mail_id,recipient,subject,body FROM mail_outbox "
                        + "WHERE sent_at IS NULL AND attempts<8 AND next_attempt_at<=UTC_TIMESTAMP(6) "
                        + "ORDER BY mail_id LIMIT 1 FOR UPDATE SKIP LOCKED")) {
                    p.setQueryTimeout(5);
                    try (ResultSet rs = p.executeQuery()) {
                        if (!rs.next()) { c.commit(); return; }
                        id = rs.getLong(1); recipient = rs.getString(2);
                        subject = rs.getString(3); body = rs.getString(4);
                    }
                }
                boolean sent;
                try { send(recipient, subject, body); sent = true; }
                catch (MessagingException e) {
                    sent = false;
                    context.log("Mail delivery deferred; outbox id=" + id);
                }
                String sql = sent
                    ? "UPDATE mail_outbox SET sent_at=UTC_TIMESTAMP(6), body='', attempts=attempts+1 WHERE mail_id=?"
                    : "UPDATE mail_outbox SET attempts=attempts+1, next_attempt_at=DATE_ADD(UTC_TIMESTAMP(6), INTERVAL 1 MINUTE) WHERE mail_id=?";
                try (PreparedStatement p = c.prepareStatement(sql)) {
                    p.setQueryTimeout(5); p.setLong(1,id); p.executeUpdate();
                }
                c.commit();
            } catch (Exception e) {
                try { c.rollback(); } catch (SQLException secondary) { e.addSuppressed(secondary); }
                throw e;
            }
        } catch (Exception e) {
            context.log("Mail worker will retry; error type=" + e.getClass().getSimpleName());
        }
    }

    private void send(String recipient, String subject, String body) throws MessagingException {
        MimeMessage message = new MimeMessage(mail);
        message.setFrom(new InternetAddress(mail.getProperty("mail.from"), true));
        message.setRecipient(Message.RecipientType.TO, new InternetAddress(recipient, true));
        message.setSubject(subject, "UTF-8"); message.setText(body, "UTF-8");
        Transport.send(message);
    }

    @Override public void contextDestroyed(ServletContextEvent event) {
        if (executor != null) executor.shutdownNow();
    }
}
