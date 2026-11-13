# JPA lesson 3: email attempt history

## Scope and evidence

The assistant added EmailAttemptStatus, EmailLog, EmailLogRepository, and six repository integration tests. Maven executed the full suite successfully: 15 tests, 0 failures, 0 errors, 0 skipped. All new persistence tests use embedded H2, not MySQL or SMTP. No email was sent. The MySQL password issue and live ORM validation remain deferred at the learner's request.

All three domain tables now have JPA entity mappings: users, reminders, and email_logs. This completes the initial mapping stage, not the complete authentication, reminder API, scheduling, or email modules.

## Concepts before code

A reminder has an overall status, while each sending attempt has its own outcome. For example, a reminder waiting in RETRY may already have one FAILED email log. Reminder and attempt statuses are separate enums because they describe different objects.

One EmailLog belongs to one Reminder through email_logs.reminder_id. Many EmailLog rows can reference that reminder. A log stores an attempt number, claim token, snapshots of title and recipient, start/completion times, and outcome. Later editing or soft-deleting the reminder does not rewrite or remove existing audit records.

SMTP_ACCEPTED means a positive SMTP submission result, not confirmation of inbox placement or reading. SMTP can accept responsibility and subsequently encounter delivery problems. UNKNOWN records uncertainty, such as losing the connection before the client learns whether the submission was accepted. The future sending/retry service must classify actual outcomes; changing a test entity's status is not a real SMTP test.

## Files and annotations

The entity and repository directories already exist. The following four files have been created and saved. Open their exact paths in VS Code to inspect the full code. Ctrl+S saves edits. Do not paste Java into PowerShell.

- backend/src/main/java/com/example/reminder/entity/EmailAttemptStatus.java
- backend/src/main/java/com/example/reminder/entity/EmailLog.java
- backend/src/main/java/com/example/reminder/repository/EmailLogRepository.java
- backend/src/test/java/com/example/reminder/repository/EmailLogRepositoryTest.java

@Entity and @Table map the class and describe its two unique keys. @ManyToOne(fetch=LAZY, optional=false) connects each attempt to its reminder. @JoinColumn maps reminder_id and names the foreign key. There is no delete cascade. @Id and IDENTITY use database-generated IDs. @Enumerated(STRING) plus JDBC VARCHAR maps outcome names to the existing VARCHAR status column. UTC Instants use the same TIMESTAMP mapping as the other entities.

updatable=false marks the reminder reference, attempt number, token, snapshots, and started_at as immutable through normal ORM updates. Only completion/outcome fields are updated. This is a code-level mapping rule, not a database permission revocation against direct SQL.

## Constructor and lifecycle methods

The protected empty constructor is for Hibernate. The public constructor requires a reminder, positive attempt number, UUID claim token, and explicit startedAt Instant. It copies the current title and recipient into snapshot columns and truncates start time to microseconds. The future worker service will supply its Clock-derived attempt time. Creating this object does not claim the reminder, increase its attempt counter, or send anything.

The initial status is STARTED, with completed_at and failure_reason null. markSmtpAccepted records an acceptance result and clears the failure reason. markFailed records a definite failure. markUnknown records an uncertain result. The private complete method rejects completion before the start and rejects changing an already recorded terminal outcome in this entity instance.

These in-memory guards do not prevent two workers from concurrently updating the same audit row. The scheduler module must finalize outcomes with an atomic claim-token/status check and suitable transaction boundaries. There is no EmailLog @Version column in the existing schema. The current lesson does not claim concurrent finalization or exactly-once sending.

## Failure reasons

markFailed and markUnknown require a safe, nonblank failure reason. The helper normalizes line breaks/tabs and truncates to at most 2,000 Unicode code points without splitting a surrogate pair. This is formatting and length control, not secret redaction. The future SMTP integration must supply classified/sanitized messages, not raw credentials, transcripts, or unreviewed exception strings.

## Repository queries and history retention

findAllByReminder_IdAndReminder_User_Id checks both the reminder and its owner. findAllByReminder_User_Id supports paginated recent activity across one owner's reminders. User identity must ultimately come from the authenticated principal in the service layer; repository filtering is not a substitute for authentication.

History queries intentionally retain records for soft-deleted reminders. The normal reminder list filters deleted_at, while audit history still shows what happened. Physical deletion is already restricted by the MySQL foreign keys. Passing Pageable bounds result size and specifies stable ordering, such as startedAt descending then id descending.

## What the tests verify

1. Persist STARTED, reload it, edit the reminder, record a synthetic SMTP_ACCEPTED outcome, and verify the old title/recipient snapshots remain.
2. Persist a definite failure with a normalized, bounded single-line reason.
3. Reject a completion before start, persist UNKNOWN, and reject rewriting that terminal outcome in the same entity instance.
4. Reject a second log with the same reminder/attempt-number pair using the database unique constraint.
5. Reject a claim token reused across different reminders using the database unique constraint.
6. Verify owner-filtered, paginated history excludes another user's attempts and retains the owner's history after soft deletion.

@DataJpaTest and @ActiveProfiles("test") use H2 and transaction rollback. Test fixtures create logs independently of the future worker workflow, so fixture reminder counters/attempts are not a simulation of actual scheduler processing. Duplicate-key tests deliberately produce logged H2 errors that are caught by assertThrows. H2-generated tables do not duplicate every handwritten MySQL constraint, index, or collation.

## Complete current files

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\entity\EmailAttemptStatus.java

```java
package com.example.reminder.entity;

public enum EmailAttemptStatus {
    STARTED,
    SMTP_ACCEPTED,
    FAILED,
    UNKNOWN
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\entity\EmailLog.java

```java
package com.example.reminder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "email_logs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_email_logs_attempt", columnNames = {"reminder_id", "attempt_number"}),
        @UniqueConstraint(name = "uk_email_logs_claim_token", columnNames = "claim_token")
})
public class EmailLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reminder_id", nullable = false, updatable = false,
            foreignKey = @ForeignKey(name = "fk_email_logs_reminder"))
    private Reminder reminder;

    @Column(name = "attempt_number", nullable = false, updatable = false)
    private int attemptNumber;

    @Column(name = "claim_token", nullable = false, updatable = false, length = 36)
    private String claimToken;

    @Column(name = "reminder_title", nullable = false, updatable = false, length = 200)
    private String reminderTitle;

    @Column(name = "recipient_email", nullable = false, updatable = false, length = 254)
    private String recipientEmail;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "completed_at")
    private Instant completedAt;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private EmailAttemptStatus status = EmailAttemptStatus.STARTED;

    @Column(name = "failure_reason", length = 2000)
    private String failureReason;

    protected EmailLog() {
        // Required by JPA when loading rows.
    }

    public EmailLog(Reminder reminder, int attemptNumber, String claimToken, Instant startedAt) {
        this.reminder = Objects.requireNonNull(reminder, "reminder is required");
        if (attemptNumber < 1) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        this.attemptNumber = attemptNumber;
        this.claimToken = UUID.fromString(Objects.requireNonNull(claimToken, "claimToken is required"))
                .toString();
        this.reminderTitle = reminder.getTitle();
        this.recipientEmail = reminder.getRecipientEmail();
        this.startedAt = Objects.requireNonNull(startedAt, "startedAt is required")
                .truncatedTo(ChronoUnit.MICROS);
    }

    public void markSmtpAccepted(Instant completedAt) {
        complete(EmailAttemptStatus.SMTP_ACCEPTED, completedAt, null);
    }

    public void markFailed(Instant completedAt, String safeFailureReason) {
        complete(EmailAttemptStatus.FAILED, completedAt, normalizeReason(safeFailureReason));
    }

    public void markUnknown(Instant completedAt, String safeFailureReason) {
        complete(EmailAttemptStatus.UNKNOWN, completedAt, normalizeReason(safeFailureReason));
    }

    private void complete(EmailAttemptStatus outcome, Instant completedAt, String safeReason) {
        if (status != EmailAttemptStatus.STARTED) {
            throw new IllegalStateException("This attempt already has a recorded outcome");
        }
        Instant completed = Objects.requireNonNull(completedAt, "completedAt is required")
                .truncatedTo(ChronoUnit.MICROS);
        if (completed.isBefore(startedAt)) {
            throw new IllegalArgumentException("completedAt cannot be before startedAt");
        }
        this.status = outcome;
        this.completedAt = completed;
        this.failureReason = safeReason;
    }

    private static String normalizeReason(String safeReason) {
        Objects.requireNonNull(safeReason, "safeFailureReason is required");
        String normalized = safeReason.replaceAll("[\\r\\n\\t]+", " ").strip();
        if (normalized.isBlank()) {
            throw new IllegalArgumentException("safeFailureReason cannot be blank");
        }
        int codePoints = normalized.codePointCount(0, normalized.length());
        return normalized.substring(0, normalized.offsetByCodePoints(0, Math.min(codePoints, 2000)));
    }

    public Long getId() { return id; }
    public Reminder getReminder() { return reminder; }
    public int getAttemptNumber() { return attemptNumber; }
    public String getClaimToken() { return claimToken; }
    public String getReminderTitle() { return reminderTitle; }
    public String getRecipientEmail() { return recipientEmail; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getCompletedAt() { return completedAt; }
    public EmailAttemptStatus getStatus() { return status; }
    public String getFailureReason() { return failureReason; }
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\repository\EmailLogRepository.java

```java
package com.example.reminder.repository;

import com.example.reminder.entity.EmailLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailLogRepository extends JpaRepository<EmailLog, Long> {

    Page<EmailLog> findAllByReminder_IdAndReminder_User_Id(
            Long reminderId, Long userId, Pageable pageable);

    Page<EmailLog> findAllByReminder_User_Id(Long userId, Pageable pageable);
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\test\java\com\example\reminder\repository\EmailLogRepositoryTest.java

```java
package com.example.reminder.repository;

import com.example.reminder.entity.EmailAttemptStatus;
import com.example.reminder.entity.EmailLog;
import com.example.reminder.entity.Reminder;
import com.example.reminder.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class EmailLogRepositoryTest {

    private static final Instant START = Instant.parse("2099-01-01T03:30:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private EmailLogRepository emailLogRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsStartAndAcceptanceWhileKeepingOriginalSnapshots() {
        Reminder reminder = saveReminder(saveUser("snapshot@example.test"), "Original title");
        EmailLog log = emailLogRepository.saveAndFlush(newLog(reminder, 1));
        Long id = log.getId();
        Long reminderId = reminder.getId();
        entityManager.clear();

        EmailLog started = emailLogRepository.findById(id).orElseThrow();
        assertNotNull(started.getId());
        assertEquals(EmailAttemptStatus.STARTED, started.getStatus());
        assertEquals(START, started.getStartedAt());
        assertNull(started.getCompletedAt());
        assertNull(started.getFailureReason());

        Reminder edited = reminderRepository.findById(reminderId).orElseThrow();
        edited.updateDetails("Changed title", "new-recipient@example.test", null,
                "Changed content", START.plusSeconds(60), "Asia/Kolkata");
        started.markSmtpAccepted(START.plusSeconds(2));
        emailLogRepository.flush();
        entityManager.clear();

        EmailLog accepted = emailLogRepository.findById(id).orElseThrow();
        assertEquals(EmailAttemptStatus.SMTP_ACCEPTED, accepted.getStatus());
        assertEquals(START.plusSeconds(2), accepted.getCompletedAt());
        assertEquals("Original title", accepted.getReminderTitle());
        assertEquals("recipient@example.test", accepted.getRecipientEmail());
        assertEquals("Changed title", accepted.getReminder().getTitle());
        assertNull(accepted.getFailureReason());
    }

    @Test
    void storesDefiniteFailureWithBoundedSingleLineReason() {
        EmailLog log = emailLogRepository.saveAndFlush(newLog(
                saveReminder(saveUser("failure@example.test"), "Failure fixture"), 1));
        log.markFailed(START.plusSeconds(1), "Temporary\r\nSMTP failure " + "x".repeat(2100));
        emailLogRepository.flush();
        entityManager.clear();

        EmailLog loaded = emailLogRepository.findById(log.getId()).orElseThrow();
        assertEquals(EmailAttemptStatus.FAILED, loaded.getStatus());
        assertEquals(START.plusSeconds(1), loaded.getCompletedAt());
        assertEquals(2000, loaded.getFailureReason().length());
        assertTrue(loaded.getFailureReason().startsWith("Temporary SMTP failure "));
        assertFalse(loaded.getFailureReason().contains("\n"));
        assertFalse(loaded.getFailureReason().contains("\r"));
    }

    @Test
    void storesUnknownOutcomeAndRejectsInvalidTimeAndTerminalRewrite() {
        EmailLog log = emailLogRepository.saveAndFlush(newLog(
                saveReminder(saveUser("unknown@example.test"), "Unknown fixture"), 1));
        assertThrows(IllegalArgumentException.class,
                () -> log.markSmtpAccepted(START.minusSeconds(1)));
        assertEquals(EmailAttemptStatus.STARTED, log.getStatus());
        assertNull(log.getCompletedAt());

        log.markUnknown(START.plusSeconds(3), "Outcome uncertain after connection loss");
        emailLogRepository.flush();
        entityManager.clear();

        EmailLog loaded = emailLogRepository.findById(log.getId()).orElseThrow();
        assertEquals(EmailAttemptStatus.UNKNOWN, loaded.getStatus());
        assertEquals("Outcome uncertain after connection loss", loaded.getFailureReason());
        assertThrows(IllegalStateException.class,
                () -> loaded.markSmtpAccepted(START.plusSeconds(4)));
    }

    @Test
    void rejectsDuplicateAttemptNumberForTheSameReminder() {
        Reminder reminder = saveReminder(saveUser("attempt@example.test"), "Attempt fixture");
        emailLogRepository.saveAndFlush(newLog(reminder, 1));

        assertThrows(DataIntegrityViolationException.class,
                () -> emailLogRepository.saveAndFlush(newLog(reminder, 1)));
    }

    @Test
    void rejectsReusingAClaimTokenAcrossReminders() {
        User owner = saveUser("claim@example.test");
        Reminder first = saveReminder(owner, "First claim fixture");
        Reminder second = saveReminder(owner, "Second claim fixture");
        String token = UUID.randomUUID().toString();
        emailLogRepository.saveAndFlush(new EmailLog(first, 1, token, START));

        assertThrows(DataIntegrityViolationException.class, () ->
                emailLogRepository.saveAndFlush(new EmailLog(second, 1, token, START)));
    }

    @Test
    void historyQueriesExcludeOtherOwnersAndRetainSoftDeletedReminderHistory() {
        User owner = saveUser("history-owner@example.test");
        Reminder own = saveReminder(owner, "Own history");
        Reminder other = saveReminder(saveUser("history-other@example.test"), "Other history");
        emailLogRepository.saveAndFlush(newLog(own, 1));
        EmailLog latest = emailLogRepository.saveAndFlush(
                new EmailLog(own, 2, UUID.randomUUID().toString(), START.plusSeconds(1)));
        emailLogRepository.saveAndFlush(newLog(other, 1));

        own.cancel();
        reminderRepository.flush();
        entityManager.createNativeQuery("""
                UPDATE reminders SET deleted_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = :id
                """).setParameter("id", own.getId()).executeUpdate();
        entityManager.clear();

        PageRequest pageRequest = PageRequest.of(0, 1,
                Sort.by(Sort.Direction.DESC, "startedAt", "id"));
        Page<EmailLog> history = emailLogRepository.findAllByReminder_User_Id(owner.getId(), pageRequest);
        assertEquals(2, history.getTotalElements());
        assertEquals(1, history.getNumberOfElements());
        assertEquals(latest.getId(), history.getContent().get(0).getId());
        assertEquals(2, emailLogRepository.findAllByReminder_IdAndReminder_User_Id(
                own.getId(), owner.getId(), pageRequest).getTotalElements());
        assertEquals(0, emailLogRepository.findAllByReminder_IdAndReminder_User_Id(
                other.getId(), owner.getId(), pageRequest).getTotalElements());
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(new User(
                email, "synthetic-hash-fixture", "Learner", "Asia/Kolkata"));
    }

    private Reminder saveReminder(User owner, String title) {
        return reminderRepository.saveAndFlush(new Reminder(owner, title,
                "recipient@example.test", "Learner", "Synthetic email log fixture",
                START, "Asia/Kolkata", 3));
    }

    private EmailLog newLog(Reminder reminder, int attemptNumber) {
        return new EmailLog(reminder, attemptNumber, UUID.randomUUID().toString(), START);
    }
}

```

## Learner checkpoint

Run in ordinary PowerShell:

```powershell
Set-Location 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend'
.\mvnw.cmd test
```

No MySQL or SMTP passwords are required. Expected and assistant-verified:

```text
Tests run: 15, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Expected duplicate-key errors may appear earlier in the log; inspect the final test summary. Tests exit on their own. The assistant build log is backend/email-log-jpa-build.log, excluded by the existing *.log ignore rule. Reports are under backend/target/surefire-reports.

Share the summary or unexpected errors. Next: begin user registration with BCrypt, validated DTOs, service/controller separation, and consistent API errors. Live MySQL persistence and Postman testing remain pending before those integrations are declared complete.

## Reference

SMTP acceptance responsibilities and subsequent delivery failures: https://www.rfc-editor.org/rfc/rfc5321.html#section-6.1
