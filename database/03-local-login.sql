USE university;
CREATE TABLE lab_credentials (username VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin PRIMARY KEY, sso_subject VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE, password_salt BINARY(16) NOT NULL, password_hash BINARY(32) NOT NULL, iterations INT NOT NULL, FOREIGN KEY (sso_subject) REFERENCES students(sso_subject)) ENGINE=InnoDB;
INSERT INTO lab_credentials VALUES ('demo1','student-demo-1',UNHEX('8e9aa1797c8a4aa71df1b979d2b40401'),UNHEX('73a83fc78c13ec1f58f650d876818d61ce2bb93e0a05cd360749263854895012'),600000);
INSERT INTO lab_credentials VALUES ('demo2','student-demo-2',UNHEX('5932996e351e1e5f858105f1339d262b'),UNHEX('1a5a6773a0462b5444a946fb497580bb22bcecc427f9287edea3a0a5cfcad205'),600000);
REVOKE ALL PRIVILEGES ON university.* FROM 'profile_app'@'%';
GRANT SELECT, UPDATE ON university.students TO 'profile_app'@'%';
GRANT INSERT ON university.profile_audit TO 'profile_app'@'%';
GRANT SELECT, INSERT, UPDATE ON university.mail_outbox TO 'profile_app'@'%';
GRANT SELECT ON university.lab_credentials TO 'profile_app'@'%';
