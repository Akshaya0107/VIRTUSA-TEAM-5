-- Initial schema, version 1. Run once as an administrator after database creation.
-- All DATETIME values represent UTC; the application supplies audit timestamps.
-- This script never drops or replaces existing tables.
USE smartreminder;
SET SESSION time_zone = '+00:00';

CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    email VARCHAR(254) COLLATE utf8mb4_0900_bin NOT NULL,
    password_hash VARCHAR(255) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    time_zone VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    token_version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_users_email (email),
    CONSTRAINT ck_users_token_version CHECK (token_version >= 0),
    CONSTRAINT ck_users_display_name CHECK (CHAR_LENGTH(TRIM(display_name)) > 0)
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE reminders (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    recipient_email VARCHAR(254) COLLATE utf8mb4_0900_bin NOT NULL,
    recipient_name VARCHAR(100) NULL,
    message TEXT NOT NULL,
    scheduled_at DATETIME(6) NOT NULL,
    time_zone VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'PENDING',
    attempt_count INT NOT NULL DEFAULT 0,
    retry_count INT GENERATED ALWAYS AS (GREATEST(attempt_count - 1, 0)) STORED,
    max_retries INT NOT NULL DEFAULT 3,
    next_attempt_at DATETIME(6) NULL,
    claim_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NULL,
    lease_until DATETIME(6) NULL,
    version BIGINT NOT NULL DEFAULT 0,
    deleted_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_reminders_claim_token (claim_token),
    KEY idx_reminders_due (status, next_attempt_at, id),
    KEY idx_reminders_owner_history (user_id, created_at, id),
    KEY idx_reminders_expired_claim (status, lease_until),
    CONSTRAINT fk_reminders_user FOREIGN KEY (user_id)
        REFERENCES users (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_reminders_status CHECK (
        status IN ('PENDING', 'PROCESSING', 'SENT', 'RETRY', 'FAILED', 'CANCELLED')
    ),
    CONSTRAINT ck_reminders_attempt_count CHECK (attempt_count >= 0),
    CONSTRAINT ck_reminders_max_retries CHECK (max_retries >= 0),
    CONSTRAINT ck_reminders_attempt_budget CHECK (attempt_count <= max_retries + 1),
    CONSTRAINT ck_reminders_version CHECK (version >= 0),
    CONSTRAINT ck_reminders_title CHECK (CHAR_LENGTH(TRIM(title)) > 0),
    CONSTRAINT ck_reminders_message CHECK (CHAR_LENGTH(TRIM(message)) > 0),
    CONSTRAINT ck_reminders_next_attempt CHECK (
        (status IN ('PENDING', 'RETRY') AND next_attempt_at IS NOT NULL)
        OR (status NOT IN ('PENDING', 'RETRY') AND next_attempt_at IS NULL)
    ),
    CONSTRAINT ck_reminders_claim CHECK (
        (status = 'PROCESSING' AND claim_token IS NOT NULL AND lease_until IS NOT NULL)
        OR (status <> 'PROCESSING' AND claim_token IS NULL AND lease_until IS NULL)
    ),
    CONSTRAINT ck_reminders_deleted CHECK (
        deleted_at IS NULL OR status IN ('SENT', 'FAILED', 'CANCELLED')
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE email_logs (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reminder_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    claim_token VARCHAR(36) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    reminder_title VARCHAR(200) NOT NULL,
    recipient_email VARCHAR(254) COLLATE utf8mb4_0900_bin NOT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    status VARCHAR(20) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT 'STARTED',
    failure_reason VARCHAR(2000) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_email_logs_attempt (reminder_id, attempt_number),
    UNIQUE KEY uk_email_logs_claim_token (claim_token),
    KEY idx_email_logs_history (reminder_id, started_at, id),
    CONSTRAINT fk_email_logs_reminder FOREIGN KEY (reminder_id)
        REFERENCES reminders (id) ON DELETE RESTRICT ON UPDATE RESTRICT,
    CONSTRAINT ck_email_logs_attempt_number CHECK (attempt_number >= 1),
    CONSTRAINT ck_email_logs_status CHECK (
        status IN ('STARTED', 'SMTP_ACCEPTED', 'FAILED', 'UNKNOWN')
    ),
    CONSTRAINT ck_email_logs_completion CHECK (
        (status = 'STARTED' AND completed_at IS NULL)
        OR (status <> 'STARTED' AND completed_at IS NOT NULL)
    ),
    CONSTRAINT ck_email_logs_time_order CHECK (
        completed_at IS NULL OR completed_at >= started_at
    )
) ENGINE = InnoDB DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
