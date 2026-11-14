-- Reference schema for the lab; not an official IGNOU database design.
CREATE DATABASE IF NOT EXISTS university CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE university;

CREATE TABLE students (
    student_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    sso_subject VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    enrollment_no VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    full_name VARCHAR(100) NOT NULL,
    programme ENUM('BCA', 'MCA') NOT NULL,
    communication_address VARCHAR(500) NOT NULL,
    mobile_e164 VARCHAR(13) CHARACTER SET ascii COLLATE ascii_bin NOT NULL UNIQUE,
    mobile_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email VARCHAR(254) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    pending_email VARCHAR(254),
    email_token_hash BINARY(32),
    email_token_expires TIMESTAMP(6) NULL DEFAULT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    updated_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6)
        ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT enrollment_format CHECK (enrollment_no REGEXP '^[0-9]{8,20}$'),
    CONSTRAINT mobile_format CHECK (mobile_e164 REGEXP '^[+]91[6-9][0-9]{9}$'),
    CONSTRAINT nonnegative_version CHECK (row_version >= 0),
    CONSTRAINT pending_email_consistent CHECK (
        (pending_email IS NULL AND email_token_hash IS NULL AND email_token_expires IS NULL)
        OR (pending_email IS NOT NULL AND email_token_hash IS NOT NULL
            AND email_token_expires IS NOT NULL))
) ENGINE=InnoDB;

CREATE TABLE profile_audit (
    audit_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    actor_subject VARCHAR(128) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    event_type VARCHAR(32) NOT NULL,
    resulting_version BIGINT NOT NULL,
    occurred_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    FOREIGN KEY (student_id) REFERENCES students(student_id)
) ENGINE=InnoDB;

CREATE TABLE mail_outbox (
    mail_id BIGINT PRIMARY KEY AUTO_INCREMENT,
    student_id BIGINT NOT NULL,
    recipient VARCHAR(254) NOT NULL,
    subject VARCHAR(180) NOT NULL,
    body TEXT NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    sent_at TIMESTAMP(6) NULL DEFAULT NULL,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX dispatch_queue (sent_at, next_attempt_at),
    FOREIGN KEY (student_id) REFERENCES students(student_id)
) ENGINE=InnoDB;
