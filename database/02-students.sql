USE university;
-- Fictional fixtures. Replace subjects with actual trusted test-SSO subjects.
-- example.invalid cannot receive mail; use controlled test inboxes for email tests.
INSERT INTO students
    (sso_subject, enrollment_no, full_name, programme,
     communication_address, mobile_e164, mobile_verified, email, email_verified)
VALUES
    ('student-demo-1', '6000000001', 'Demo Student One', 'MCA',
     'Demo address, New Delhi, 110001', '+919000000001', TRUE,
     'student-one@example.invalid', TRUE),
    ('student-demo-2', '240000000002', 'Demo Student Two', 'BCA',
     'Demo address, Jaipur, 302001', '+916000000001', TRUE,
     'student-two@example.invalid', TRUE);

-- The token 6000000001 deliberately matches student 1's enrollment and
-- student 2's mobile. Principal-scoped lookup must return only the caller's row.
