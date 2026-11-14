package edu.university.profile;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;

public final class Validation {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Validation() { }

    public static String required(String raw, int max) {
        if (raw == null) throw new IllegalArgumentException("A required field is missing.");
        String s = raw.strip();
        if (s.isEmpty() || s.length() > max)
            throw new IllegalArgumentException("A field is empty or too long.");
        return s;
    }
    public static String lookupToken(String raw) {
        String s = required(raw, 20);
        if (!s.matches("(?:[0-9]{8,20}|[+]91[6-9][0-9]{9})"))
            throw new IllegalArgumentException("Enter an enrollment number or Indian mobile number.");
        return s;
    }
    public static String mobile(String raw) {
        String s = required(raw, 13);
        if (s.matches("[6-9][0-9]{9}")) return "+91" + s;
        if (s.matches("[+]91[6-9][0-9]{9}")) return s;
        throw new IllegalArgumentException("Mobile must be 10 Indian digits or +91 followed by those digits.");
    }
    public static String mobileCandidate(String token) {
        try { return mobile(token); } catch (IllegalArgumentException e) { return null; }
    }
    public static String address(String raw) {
        String s = required(raw, 500);
        if (s.length() < 5 || s.chars().anyMatch(c -> Character.isISOControl(c)
                && c != '\n' && c != '\r' && c != '\t'))
            throw new IllegalArgumentException("Enter a valid communication address (5–500 characters).");
        return s;
    }
    public static String email(String raw) {
        // Deliberately supports a common ASCII subset, not every RFC mailbox syntax.
        String s = required(raw, 254);
        if (!s.matches("[A-Za-z0-9._%+-]+@[A-Za-z0-9-]+(?:[.][A-Za-z0-9-]+)+"))
            throw new IllegalArgumentException("Enter a valid email address.");
        int at = s.indexOf('@');
        String local = s.substring(0, at), domain = s.substring(at + 1);
        if (local.length() > 64 || local.startsWith(".") || local.endsWith(".") || local.contains(".."))
            throw new IllegalArgumentException("Enter a valid email address.");
        for (String label : domain.split("[.]")) {
            if (label.length() > 63 || label.startsWith("-") || label.endsWith("-"))
                throw new IllegalArgumentException("Enter a valid email address.");
        }
        return local + "@" + domain.toLowerCase(Locale.ROOT);
    }
    public static long version(String raw) {
        try {
            if (raw == null || !raw.matches("[0-9]{1,19}")) throw new NumberFormatException();
            long n = Long.parseLong(raw);
            if (n < 0) throw new NumberFormatException();
            return n;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid profile version; reload the form.");
        }
    }
    public static String randomToken() {
        byte[] bytes = new byte[32]; RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
    public static String verificationToken(String raw) {
        if (raw == null || !raw.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("Invalid or expired verification link.");
        return raw;
    }
    public static byte[] hash(String token) {
        try { return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.US_ASCII)); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public static boolean sameToken(String expected, String supplied) {
        return expected != null && supplied != null && supplied.length() == 43
            && MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                                    supplied.getBytes(StandardCharsets.US_ASCII));
    }
}
