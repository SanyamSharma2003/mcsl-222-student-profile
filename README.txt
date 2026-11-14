MCSL-222 — STUDENT PROFILE MANAGEMENT USING JSP AND JDBC
Web Technologies Lab, Question 1(i)

ASSIGNMENT PROJECT
https://github.com/SanyamSharma2003/mcsl-222-student-profile

The public repository contains the complete Java/JSP source, schema, sample data,
configuration, compiled WAR, and live test results. Viewing/downloading source does
not require the author's Codespace to run. Each evaluator can execute their own
copy; Codespaces execution requires a GitHub account and available usage quota.
The full reviewer guide and clickable source links are in README.md.

APPLICATION AND CONNECTION ASSUMPTIONS
Java 17; Tomcat 10.1/Jakarta Servlet 6; JSP/JSTL; MySQL 8.4; JDBC pooled DataSource.
Database: university. Tables: students, lab_credentials, profile_audit, mail_outbox.
Enrollment is a string; Indian mobile numbers use +91 format. The database image
runs database/*.sql on a fresh volume to create the schema and fictional fixtures.
JNDI resources: jdbc/UniversityDB, mail/UniversityMail, profilePublicUrl.
Database host db:3306 and SMTP host mailpit:1025 use the private Compose network.
config/student-profile.xml.template defines lab connection settings. The cloud
startup script generates the current Codespace's HTTPS email-link URL before build.
The demo uses local passwords and synthetic credentials, not university SSO/MFA.
Mailpit captures messages without sending real email. These isolated lab settings
are not a production configuration.

RUN IN YOUR OWN CODESPACE
1. Open https://codespaces.new/SanyamSharma2003/mcsl-222-student-profile
   Or use Code -> Codespaces -> Create codespace on main in the repository.
2. Wait for devcontainer setup and the first image build. Startup is automatic.
   If necessary, run in that Codespace's terminal:
     bash codespaces/start.sh
3. Open PORTS. Keep ports 8443 and 8025 Private. Set 8443 to HTTPS and 8025 to HTTP.
4. Open 8443 in a separate browser tab and append /student-profile/login, or use
   the application URL printed by the script. Do not use an embedded preview.
5. Sign in and look up your own sample record:
   demo1 / LabDemo!2026 — enrollment 6000000001, mobile 9000000001
   demo2 / LabDemo!2026 — enrollment 240000000002, mobile 6000000001
   The overlapping token 6000000001 tests account ownership isolation.
6. Edit contact details and save. To verify email, use a fictional new address,
   open the test inbox on port 8025, follow its link, and confirm the change.
   Sign in again if the five-minute recent-password requirement has expired.
7. Stop YOUR OWN Codespace from https://github.com/codespaces when finished.
   Closing the browser or running docker compose down alone does not stop cloud
   compute usage. Save needed work before deleting the environment.

OPTIONAL LOCAL RUN
On a permitted machine with Docker engine and Docker Compose already running,
download/extract the source, open the project directory, and run:
  cp config/student-profile.xml.template config/student-profile.xml
  docker compose up --build -d
Application: https://localhost:8443/student-profile/login
Test inbox: http://localhost:8025
The localhost demo uses a self-signed TLS certificate; any exception is only for
that local demo address. Stop while retaining the database: docker compose down
Local execution was not live-tested on the managed Mac.

FILES AND VALIDATION
Source: app/src/main/java/edu/university/profile/ and app/src/main/webapp/WEB-INF/
Schema/fixtures/grants: database/01-schema.sql, 02-students.sql, 03-local-login.sql
Included compiled WAR: dist/student-profile.war
The WAR needs the supplied Tomcat libraries, JNDI, MySQL and SMTP configuration.
Live results: checks/LIVE-RESULTS.txt. Coverage and limits: CHECKS.txt.
Submission text: docs/SUBMISSION.txt.
Live-tested in GitHub Codespaces on 14 November 2026: Docker/Maven build,
Tomcat/JSP startup, MySQL/JDBC, browser login and all 10 smoke checks passed.
Repeat in the running environment: python3 checks/cloud_smoke_test.py
The test restores original address and verified email. Production readiness,
university SSO/MFA and real email delivery are not certified by these lab tests.
