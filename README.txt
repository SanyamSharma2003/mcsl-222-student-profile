MCSL-222 JSP/JDBC LAB — RUN IN YOUR BROWSER WITH GITHUB CODESPACES

Your managed Mac does not need Docker Desktop, Java, MySQL or Maven installed.
Everything below runs on GitHub's cloud machine. It does not alter your employer's
browser/device policy or require accessing a blocked software-installation site.
Use an account/service permitted for your personal education. This package contains
only fictional student records and example credentials, not organisation data.

EASIEST ROUTE: BLANK CODESPACE + UPLOAD THE ZIP

1. Download student-profile-codespaces.zip. Keep it as a ZIP; do not extract yet.

2. Open https://github.com/codespaces and sign in.
   Under Explore quick start templates, click See all.
   Find Blank and click Use this template.
   Wait for the browser-based VS Code editor to open.
   A blank template uses GitHub's default development environment, which includes
   Docker tooling in the CLOUD. Nothing is installed on your Mac.

3. In the left Explorer/file panel, upload student-profile-codespaces.zip.
   Drag the ZIP onto that file panel, or use Upload from its context menu.
   Confirm that the ZIP name appears in the file list.

4. Open Terminal -> New Terminal in the browser editor. Run:

     unzip student-profile-codespaces.zip
     cd student-profile-codespaces
     bash codespaces/start.sh

   If the browser changed the downloaded filename, use the actual uploaded ZIP name
   in the unzip command. The startup script checks the cloud container engine,
   generates the correct instance-specific HTTPS email URL, builds the Java app,
   starts MySQL and a test mailbox, then waits until the login page responds.
   The first build downloads cloud images/dependencies and may take several minutes.

5. In the bottom PORTS tab, add ports 8443 and 8025 if not already listed.
   For 8443, right-click -> Change Port Protocol -> HTTPS.
   For 8025, leave the protocol HTTP.
   Keep BOTH ports Private. Do not change them to Public.
   Use Open in Browser (the globe icon) for port 8443. Open a separate browser tab;
   the app deliberately disallows iframe/embedded preview.
   Append /student-profile/login to the forwarded URL if it opens a server root.
   You can also use the exact login URL printed by codespaces/start.sh.
   Do not type your Mac's localhost address; this server is in the cloud.

6. Sign in:
     Username: demo1
     Password: LabDemo!2026

   Lookup with either:
     Enrollment: 6000000001
     Mobile:     9000000001

   Click Find my profile. Edit address, contact mobile or requested email, then Save.

TEST EMAIL CONFIRMATION
   Change the email to a sample such as learner.new@example.com and save.
   Open port 8025 using its globe icon or the test-inbox URL printed by the script.
   Wait briefly for the background mail worker, open the verification message, and
   click its cloud HTTPS link. Keep demo1 signed in; click Confirm email change.
   The old verified email stays current until confirmation succeeds. A GET link
   alone does not mutate the profile. Tokens expire after 15 minutes and are one-use.
   Mailpit captures mail in this test inbox; it does not deliver to real recipients.

SECOND SAMPLE ACCOUNT
     Username: demo2
     Password: LabDemo!2026
     Enrollment: 240000000002
     Mobile: 6000000001
   Use Sign out before switching accounts. The overlapping token 6000000001 is
   intentional: each signed-in student can still access only their own profile.

RECENT PASSWORD CONFIRMATION
   Saving or confirming email requires password authentication within five minutes.
   If redirected to login, sign in again, then repeat the lookup/edit or reopen the
   email link. This demo uses real local password checking, not university SSO/MFA.

STOP WHEN FINISHED
   In the same project directory:
     bash codespaces/stop.sh
   Then go to https://github.com/codespaces, find this Codespace and select Stop.
   Stopping the app containers alone does not stop Codespaces compute usage.
   Usage/storage are subject to your GitHub account allowance and settings.

   To resume in the same Codespace, open its terminal, return to this project folder,
   and run bash codespaces/start.sh again. The named database volume is retained
   across ordinary container stops. A deleted/rebuilt cloud machine can lose its
   Docker volume. Download needed assignment source/results before deleting it.

OPTIONAL ROUTE: YOUR OWN PRIVATE GITHUB REPOSITORY
   Upload the CONTENTS of this project at the repository root. Include .devcontainer
   and .dockerignore (Mac Finder shows hidden files with Command+Shift+.).
   The root should contain compose.yaml, Dockerfile, app/, config/, codespaces/ and
   .devcontainer/. Do not upload just the ZIP as a repository source file.
   Select Code -> Codespaces -> Create codespace on main. The included devcontainer
   automatically configures HTTPS port 8443 and runs the startup script at startup.
   The blank-template route above avoids needing to create/publish a repository.

TROUBLESHOOTING
   No PORTS tab: open View -> Terminal, then choose PORTS beside the Terminal tab.
   502 / SSL error: set 8443's port protocol to HTTPS and check the app is running.
   Blank embedded preview: use Open in Browser / a separate tab.
   Email link points to localhost: rerun bash codespaces/start.sh to regenerate it.
   Build failed: docker compose logs --tail=100 app; inspect the startup output too.
   Engine missing: the selected template may have custom restrictions. The optional
   repository devcontainer defines the required engine feature; do not install any
   software or alter security settings on the managed Mac to compensate.
   A cloud preview URL is blocked by organisation policy: this package cannot
   override that policy. Use a permitted personal device or university lab instead.

IMPLEMENTATION AND BOUNDARIES
   Java 17 + Tomcat 10.1 + MySQL 8.4; JSP view, Servlet controller, JDBC persistence.
   Secure session cookies, CSRF, bound principal ownership, prepared SQL, version
   checks, transactional audit/outbox, hashed expiring email tokens are retained.
   The named cloud instances generate HTTPS mail links before image construction.
   Local test credentials/plain DB/test SMTP stay inside this synthetic lab stack.
   This sample is not for real learner data/public deployment. The original SSO
   reference project defines the university integration separately.

VALIDATION STATUS
   Live-tested in GitHub Codespaces on 14 November 2026: Docker/Maven build,
   Tomcat/JSP startup, MySQL/JDBC, browser login and all 10 smoke checks passed.
   Repeat inside the running Codespace: python3 checks/cloud_smoke_test.py
   The test covers owner-scoped lookups, CSRF rejection, saved address and verified
   email changes through Mailpit; it restores the original address and email.
   Authoring checks also cover Bash syntax, XML/JSON, password hashes and lookup SQL.
   Production SSO/MFA and real email delivery are not certified. See CHECKS.txt.
   The accompanying download will include the built WAR: dist/student-profile.war.

PRIMARY REFERENCES
   https://docs.github.com/en/codespaces/developing-in-a-codespace/creating-a-codespace-from-a-template
   https://docs.github.com/en/codespaces/developing-in-a-codespace/forwarding-ports-in-your-codespace
   https://github.com/github/docs/blob/main/content/codespaces/developing-in-a-codespace/default-environment-variables-for-your-codespace.md
   https://github.com/devcontainers/images/blob/main/src/universal/.devcontainer/devcontainer.json
