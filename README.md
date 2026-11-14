# MCSL-222 Student Profile Lab

Run this JSP/JDBC assignment in GitHub Codespaces without installing Java, MySQL, or Tomcat on your computer.

## Open and start

1. [Open your existing Codespace](https://laughing-space-pancake-pqxvg6576g7frjr6.github.dev/), named `laughing-space-pancake`.
2. Wait for its container setup to finish. If the lab is not running, enter this in its terminal:

   ```bash
   bash codespaces/start.sh
   ```

3. Open **Ports**. Keep ports **8443** and **8025** **Private**.
4. For **8443**, right-click its row and set **Change Port Protocol → HTTPS**, then **Open in Browser**.
5. Open `/student-profile/login` at that forwarded address, or use the application URL printed by the script.

Port **8025** is the test email inbox. Its backend port protocol stays **HTTP**; open it from **Ports** or use the printed inbox URL. GitHub provides HTTPS for the browser-facing forwarded address.

## Try the application

- Sign in with username **demo1** and password **LabDemo!2026**.
- Look up **6000000001** (enrollment) or **9000000001** (mobile).
- Change the communication address or mobile number, then save.
- To test email verification, enter a new address such as `learner@example.invalid`, save, and open the confirmation message in the port **8025** test inbox. No real email is sent.
- Follow the email link and confirm the change. Sign in again if the five-minute password confirmation expires.

The second account is **demo2**, with the same password. Its enrollment is **240000000002** and mobile is **6000000001**. That mobile deliberately matches demo1's enrollment: each signed-in account must retrieve only its own record.

All records and credentials are synthetic. This local lab verifies passwords; it does not implement the enterprise version's university SSO or MFA integration.

## Source, schema, and stopping

- Java source: `app/src/main/java/edu/university/profile/`
- JSP form: `app/src/main/webapp/WEB-INF/profile.jsp`
- Database definition and fixtures: `database/`
- Full schema and configuration notes: [README.txt](README.txt)

Files saved in the Codespace persist when it stops. Commit and push source changes to preserve a separate copy in the repository; deleting the Codespace can delete unpushed work and its database volume.

When finished, open [GitHub Codespaces](https://github.com/codespaces), find this Codespace, choose its **…** menu, and select **Stop codespace**. Closing the browser tab alone does not stop it.

Live deployment was verified in GitHub Codespaces on **14 November 2026**: Docker/Maven builds, Tomcat/JSP startup, MySQL/JDBC, browser login, and the full profile/email smoke test passed. Run `python3 checks/cloud_smoke_test.py` inside the running Codespace to repeat the checks; the script restores the original address and verified email. See [CHECKS.txt](CHECKS.txt) for coverage and limitations. Production university SSO/MFA and real email delivery are not certified by these lab checks.

The accompanying download will include the built WAR at `dist/student-profile.war`. This WAR requires the supplied Tomcat/JNDI/database configuration and runtime libraries.
