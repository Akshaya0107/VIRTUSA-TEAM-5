-- Run in a fresh interactive mysql session as reminder_app.
-- Exactly four deliberate constraint errors are expected in this exercise.
-- No email is sent. All synthetic test rows are removed by the final ROLLBACK.
USE smartreminder;
SET SESSION time_zone = '+00:00';
SELECT CURRENT_USER() AS authenticated_account;

START TRANSACTION;
SET @test_time = UTC_TIMESTAMP(6);
SET @test_due = TIMESTAMPADD(DAY, 1, @test_time);
SET @test_email = CONCAT('schema-', REPLACE(UUID(), '-', ''), '@example.test');

INSERT INTO users (email, password_hash, display_name, time_zone, created_at, updated_at)
VALUES (@test_email, 'TEST_FIXTURE_NOT_A_LOGIN_HASH', 'Schema test',
        'Asia/Kolkata', @test_time, @test_time);
SET @test_user_id = LAST_INSERT_ID();

INSERT INTO reminders (user_id, title, recipient_email, recipient_name, message,
                       scheduled_at, time_zone, next_attempt_at, created_at, updated_at)
VALUES (@test_user_id, 'Schema test reminder', @test_email, 'Schema test',
        'Synthetic transaction fixture; do not send.', @test_due, 'Asia/Kolkata',
        @test_due, @test_time, @test_time);
SET @test_reminder_id = LAST_INSERT_ID();

-- A synthetic failure log tests storage only; no SMTP attempt occurred.
INSERT INTO email_logs (reminder_id, attempt_number, claim_token, reminder_title,
                        recipient_email, started_at, completed_at, status, failure_reason)
VALUES (@test_reminder_id, 1, UUID(), 'Schema test reminder', @test_email,
        @test_time, @test_time, 'FAILED', 'Synthetic schema-test failure');
SET @test_log_id = LAST_INSERT_ID();

-- Set fixture counters directly to verify the generated retry count.
-- Real attempts and state transitions will be implemented in Java later.
UPDATE reminders
SET status = 'RETRY', attempt_count = 2, updated_at = @test_time
WHERE id = @test_reminder_id;

SELECT attempt_count, retry_count, status
FROM reminders WHERE id = @test_reminder_id;

SELECT 'TEST 1: expect ERROR 1062 for uk_users_email' AS expected_result;
INSERT INTO users (email, password_hash, display_name, time_zone, created_at, updated_at)
VALUES (@test_email, 'TEST_FIXTURE_NOT_A_LOGIN_HASH', 'Duplicate fixture',
        'Asia/Kolkata', @test_time, @test_time);

SELECT 'TEST 2: expect ERROR 3819 for ck_reminders_status' AS expected_result;
UPDATE reminders SET status = 'INVALID', next_attempt_at = NULL
WHERE id = @test_reminder_id;

SELECT 'TEST 3: expect ERROR 1451 for fk_reminders_user' AS expected_result;
DELETE FROM users WHERE id = @test_user_id;

SELECT 'TEST 4: expect ERROR 1062 for uk_email_logs_attempt' AS expected_result;
INSERT INTO email_logs (reminder_id, attempt_number, claim_token, reminder_title,
                        recipient_email, started_at, completed_at, status, failure_reason)
VALUES (@test_reminder_id, 1, UUID(), 'Schema test reminder', @test_email,
        @test_time, @test_time, 'FAILED', 'Duplicate attempt fixture');

SELECT 'BEFORE ROLLBACK: expect 1, 1, 1' AS verification;
SELECT
    (SELECT COUNT(*) FROM users WHERE id = @test_user_id) AS test_users,
    (SELECT COUNT(*) FROM reminders WHERE id = @test_reminder_id) AS test_reminders,
    (SELECT COUNT(*) FROM email_logs WHERE reminder_id = @test_reminder_id) AS test_logs;

ROLLBACK;

SELECT 'AFTER ROLLBACK: expect 0, 0, 0' AS verification;
SELECT
    (SELECT COUNT(*) FROM users WHERE email = @test_email) AS remaining_test_users,
    (SELECT COUNT(*) FROM reminders WHERE id = @test_reminder_id) AS remaining_test_reminders,
    (SELECT COUNT(*) FROM email_logs WHERE reminder_id = @test_reminder_id) AS remaining_test_logs;
