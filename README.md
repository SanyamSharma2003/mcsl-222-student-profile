# MCSL-222 — Student Profile Management using JSP and JDBC

Web Technologies Lab, Question 1(i): an authenticated university learner finds their own profile using an enrollment number or registered mobile number, then updates the communication address, mobile number, and email. A changed email remains pending until its confirmation link is used.

**Assignment project:** [SanyamSharma2003/mcsl-222-student-profile](https://github.com/SanyamSharma2003/mcsl-222-student-profile). Source and downloads can be reviewed without a running Codespace. To execute it in Codespaces, each evaluator needs their own GitHub account and available Codespaces allowance or billing quota.

## Stack and database assumptions

- Java 17, Tomcat 10.1 / Jakarta Servlet 6, JSP/JSTL, MySQL 8.4, and JDBC through a pooled JNDI `DataSource`.
- Database `university` contains `students`, `lab_credentials`, `profile_audit`, and `mail_outbox`. Enrollment numbers are strings; Indian mobile numbers use `+91` format. The supplied SQL creates the schema and fictional fixtures automatically on the database volume's first startup.
- JNDI names are `jdbc/UniversityDB`, `mail/UniversityMail`, and `profilePublicUrl`. The [configuration template](config/student-profile.xml.template) uses database host `db:3306` and test SMTP host `mailpit:1025` on the private Compose network. The startup script generates the current Codespace's HTTPS email-link URL before building.
- Sample credentials and unencrypted database/test-SMTP connections are for this isolated lab only. Authentication uses local password verification; university SSO/MFA and real email delivery are outside this demo.

## Source and submission files

- [Java controller, validation, JDBC DAO, authentication and mail worker](app/src/main/java/edu/university/profile/) · [JSP form](app/src/main/webapp/WEB-INF/profile.jsp)
- [Database schema](database/01-schema.sql) · [sample learners](database/02-students.sql) · [sample login setup and database grants](database/03-local-login.sql)
- [Build configuration](app/pom.xml) · [server libraries](build/runtime-pom.xml) · [Docker deployment](compose.yaml)
- [Included compiled WAR](dist/student-profile.war) · [live test results](checks/LIVE-RESULTS.txt) · [verification scope](CHECKS.txt) · [ready-to-copy submission text](docs/SUBMISSION.txt)

The WAR needs the supplied Tomcat libraries, JNDI resources, database, and test SMTP configuration; the Compose build sets these up together.

## Run in your own Codespace

1. [Create a Codespace for this repository](https://codespaces.new/SanyamSharma2003/mcsl-222-student-profile), or select **Code → Codespaces → Create codespace on main** on the repository page. This creates your own environment.
2. Wait for setup and the first image build. The devcontainer starts the lab automatically. If needed, run this in its terminal:

   ```bash
   bash codespaces/start.sh
   ```

3. Open **Ports** and keep **8443** and **8025** **Private**. Port **8443** must use **HTTPS**; port **8025** uses **HTTP** as its backend protocol.
4. For **8443**, use **Open in Browser** and append `/student-profile/login`, or open the exact application URL printed by the startup script. Use a separate tab; embedded previews are blocked by the app's security policy.
5. Sign in and look up the matching enrollment or mobile number below. To test email, save a new fictional address such as `learner@example.invalid`, open the test inbox on **8025**, follow the message link, and click **Confirm email change**. Mailpit captures messages without sending real email.

| Username | Password | Enrollment number | Mobile number |
|---|---|---|---|
| `demo1` | `LabDemo!2026` | `6000000001` | `9000000001` |
| `demo2` | `LabDemo!2026` | `240000000002` | `6000000001` |

The overlapping token `6000000001` deliberately tests ownership: each account retrieves only its own record. Saving and confirming email require a password login within five minutes; sign in again and repeat the action if prompted.

When finished, stop **your own Codespace** from its **… → Stop codespace** menu at [GitHub Codespaces](https://github.com/codespaces). Closing its browser tab or stopping only the application containers does not stop Codespaces compute usage. Database changes persist across ordinary stops; retain needed work before deleting the environment.

## Optional local execution

On a permitted computer with a running Docker engine and Docker Compose, download/extract the source, open its project directory, and run:

```bash
cp config/student-profile.xml.template config/student-profile.xml
docker compose up --build -d
```

Open [local login](https://localhost:8443/student-profile/login) and [local test inbox](http://localhost:8025). The localhost demo uses a self-signed certificate; any certificate exception applies only to this local demo address. To stop the containers while retaining the database, run `docker compose down`.

## Verification

Live-tested in GitHub Codespaces on **14 November 2026**: Docker/Maven builds, Tomcat/JSP startup, MySQL/JDBC, browser login, and all 10 [smoke checks](checks/LIVE-RESULTS.txt) passed. They cover both lookups, cross-owner denial, CSRF rejection, address persistence, email delivery/confirmation, and restoration of original contact data. Repeat inside the running environment:

```bash
python3 checks/cloud_smoke_test.py
```

See [CHECKS.txt](CHECKS.txt) for limits. These synthetic lab checks do not certify production readiness, university SSO/MFA integration, or real email delivery. Local Docker execution is documented but was not live-tested on the managed Mac.

GitHub documents the repository launch-link format in [Creating a link to the codespace creation page](https://docs.github.com/en/codespaces/setting-up-your-project-for-codespaces/setting-up-your-repository/facilitating-quick-creation-and-resumption-of-codespaces).
