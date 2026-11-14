package edu.university.profile;

/** View model: internal token hashes and SSO subjects are never sent to the JSP. */
public final class Profile {
    private final long id, version;
    private final String enrollment, name, programme, address, mobile, email, pendingEmail;
    private final boolean emailVerified, mobileVerified;

    public Profile(long id, long version, String enrollment, String name, String programme,
                   String address, String mobile, String email, String pendingEmail,
                   boolean emailVerified, boolean mobileVerified) {
        this.id = id; this.version = version; this.enrollment = enrollment;
        this.name = name; this.programme = programme; this.address = address;
        this.mobile = mobile; this.email = email; this.pendingEmail = pendingEmail;
        this.emailVerified = emailVerified; this.mobileVerified = mobileVerified;
    }
    public long getId() { return id; }
    public long getVersion() { return version; }
    public String getEnrollment() { return enrollment; }
    public String getName() { return name; }
    public String getProgramme() { return programme; }
    public String getAddress() { return address; }
    public String getMobile() { return mobile; }
    public String getEmail() { return email; }
    public String getPendingEmail() { return pendingEmail; }
    public boolean isEmailVerified() { return emailVerified; }
    public boolean isMobileVerified() { return mobileVerified; }
    public String getRequestedEmail() { return pendingEmail == null ? email : pendingEmail; }
}
