# Create the first three application tables

Status: the learner executed the initial schema on MySQL and supplied metadata matching 25 constraints, 11 indexes, and the generated retry_count column. Constraint behavior tests are prepared in lesson 07 and remain pending. The assistant did not execute the SQL against MySQL. Application root credentials are not available to the assistant.

## What is being created

users stores application identities. reminders references users.id through user_id. email_logs references reminders.id through reminder_id. All tables use InnoDB, which supports transactions and foreign keys. Tables are created in parent-before-child order.

Both scripts have already been saved in the database folder. Open their exact paths in VS Code to inspect them; Ctrl+S saves edits. Do not paste the entire SQL file into PowerShell. Use SOURCE at the mysql> prompt so line breaks and comments are preserved.

## Important SQL constructs

- USE smartreminder selects the existing application database; no database or account is created here.
- SET SESSION time_zone = '+00:00' configures UTC for this MySQL client session. It does not change other connections or permanently set the server's time zone.
- CREATE TABLE declares columns, constraints, and indexes.
- BIGINT matches Java Long; AUTO_INCREMENT lets MySQL generate row identifiers.
- NOT NULL requires a value. Nullable columns represent optional or not-yet-known values.
- DEFAULT provides an initial value when an INSERT omits that column.
- DATETIME(6) stores microsecond precision without a zone. Java supplies UTC audit timestamps explicitly; there are no automatic local-time audit defaults.
- VARCHAR limits character count; TEXT stores longer messages. Application validation will impose an explicit message limit later.
- utf8mb4 supports full Unicode. The normal table collation is case/accent insensitive. Email columns override that with utf8mb4_0900_bin so application-normalized email identities are compared precisely without accent folding. Normalization will happen before registration and login queries. ASCII binary comparison is used for statuses, BCrypt hashes, zone IDs, and claim tokens.
- PRIMARY KEY uniquely identifies a row. UNIQUE KEY prevents repeated email identities, claim tokens, or attempt numbers within a reminder. Nullable reminder claim tokens permit multiple unclaimed reminders because MySQL unique indexes allow multiple NULL values.
- FOREIGN KEY prevents an orphan reference. ON DELETE RESTRICT preserves history by rejecting a physical parent deletion while children exist.
- CHECK limits values and combinations. MySQL 8.0.45 enforces these constraints.
- KEY creates an index to support a query pattern. Due work uses status plus next_attempt_at; user history starts with user_id; stale processing work uses status plus lease_until.
- retry_count is a STORED generated column: GREATEST(attempt_count - 1, 0). The database calculates and stores it, and application writes must omit it. A future JPA field must be read-only with insertable=false and updatable=false.

## State invariants

PENDING and RETRY require next_attempt_at. PROCESSING requires claim_token and lease_until, and no next_attempt_at. Other states have no claim, lease, or next attempt time. These columns must be changed in one atomic UPDATE when transitioning states. Initial creation sets next_attempt_at equal to scheduled_at. Terminal reminders may be soft-deleted using deleted_at. PROCESSING reminders cannot be soft-deleted or cancelled by merely changing a status.

attempt_count starts at zero. The first sending attempt changes it to one, with retry_count still zero. With max_retries = 3 there are at most four automatically scheduled attempts for definite failures. max_retries is a policy snapshot, set explicitly from configuration when a reminder is created.

An email attempt begins with STARTED and no completed_at. SMTP_ACCEPTED, FAILED, and UNKNOWN require completed_at. UNKNOWN represents an uncertain outcome, not proof that the message was rejected. Reminder SENT means SMTP acceptance, not recipient inbox delivery.

These checks enforce row consistency, not an entire delivery algorithm. Worker claiming, transaction boundaries, owner authorization, retry classification, and uncertain-outcome handling still need implementation and testing. Unique attempt records alone do not prevent duplicate SMTP effects.

## Complete SQL files

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\database\02-create-tables.sql

```sql
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

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\database\03-verify-schema.sql

```sql
-- Read-only schema inspection. Run after 02-create-tables.sql completes.
USE smartreminder;

SHOW TABLES;

SELECT TABLE_NAME, ENGINE
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME;

SELECT TABLE_NAME, COLUMN_NAME, REFERENCED_TABLE_NAME, REFERENCED_COLUMN_NAME
FROM information_schema.KEY_COLUMN_USAGE
WHERE TABLE_SCHEMA = DATABASE() AND REFERENCED_TABLE_NAME IS NOT NULL
ORDER BY TABLE_NAME, COLUMN_NAME;

SELECT TABLE_NAME, CONSTRAINT_NAME, CONSTRAINT_TYPE
FROM information_schema.TABLE_CONSTRAINTS
WHERE TABLE_SCHEMA = DATABASE()
ORDER BY TABLE_NAME, CONSTRAINT_TYPE, CONSTRAINT_NAME;

SELECT TABLE_NAME, INDEX_NAME,
       GROUP_CONCAT(COLUMN_NAME ORDER BY SEQ_IN_INDEX SEPARATOR ', ') AS indexed_columns
FROM information_schema.STATISTICS
WHERE TABLE_SCHEMA = DATABASE()
GROUP BY TABLE_NAME, INDEX_NAME
ORDER BY TABLE_NAME, INDEX_NAME;

SELECT TABLE_NAME, COLUMN_NAME, EXTRA, GENERATION_EXPRESSION
FROM information_schema.COLUMNS
WHERE TABLE_SCHEMA = DATABASE() AND COLUMN_NAME = 'retry_count';

```

## Execute and verify

Keep the current Spring Boot window running if desired. Open a separate ordinary PowerShell window and log in as root:

```powershell
& 'C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe' -h 127.0.0.1 -P 3306 -u root -p smartreminder
```

Enter the root password privately. At mysql>, first run:

```sql
SHOW TABLES;
```

The last learner check reported no tables. If tables now exist, stop and share their names before applying this initial script.

If empty, run at mysql>:

```sql
SOURCE C:/Users/avina/OneDrive/Documents/ChatGPT/study/ReminderService/database/02-create-tables.sql;
```

Expected: database-selection and session-setting messages followed by a Query OK for each of the three CREATE TABLE statements. Any ERROR means the step has not passed. MySQL DDL commits each table creation separately; the whole file is not one rollbackable transaction. The mysql client may continue to subsequent statements after an error. Share every error and SHOW TABLES output; do not blindly rerun, drop tables, or disable foreign-key checks. No IF NOT EXISTS is used because a preexisting incompatible table must not be silently accepted.

Then run:

```sql
SOURCE C:/Users/avina/OneDrive/Documents/ChatGPT/study/ReminderService/database/03-verify-schema.sql;
```

This read-only script shows the tables, engines, foreign keys, constraints, indexes, and generated retry_count definition. Expected tables: email_logs, reminders, users. Expected foreign-key pairs: email_logs.reminder_id -> reminders.id; reminders.user_id -> users.id. All engines should be InnoDB. Expected check counts: users 2, reminders 10, email_logs 4. These are schema expectations, not claimed database results.

Share the creation errors if any, table list, and foreign-key result. No statements in these scripts contain passwords. You do not need to share login commands or authentication prompts.

## What is not yet tested

Successful creation and metadata inspection do not prove that invalid inserts are rejected, a multi-row workflow rolls back correctly, or scheduler concurrency works. After table creation is verified we will exercise constraints in rollbackable test transactions, then map the schema to JPA. Existing H2 health tests do not execute this MySQL-specific SQL.

This is the initial manually applied schema version. Future schema changes should be separate versioned files; we will choose and configure the automated migration workflow before deployment rather than treating this filename as an already-installed migration tool.

## References

- https://dev.mysql.com/doc/refman/8.0/en/create-table-foreign-keys.html
- https://dev.mysql.com/doc/refman/8.0/en/create-table-check-constraints.html
- https://dev.mysql.com/doc/refman/8.0/en/charset-binary-collations.html

