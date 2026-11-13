# JPA lesson 2: Reminder and its owner

## Scope and verified evidence

The MySQL credential issue remains explicitly deferred by the learner. No password repair, MySQL authentication, SQL migration, or live MySQL ORM validation is performed in this lesson.

The assistant created ReminderStatus, Reminder, ReminderRepository, and five repository integration tests. The complete Maven suite passed with H2: 9 tests, 0 failures, 0 errors, 0 skipped. This includes the existing three user tests and one HTTP database-health test. The new tests are persistence tests, not live reminder API or SMTP tests.

## Concepts before code

A reminder stores a User reference. Many reminders can point to the same user. In the database this is reminders.user_id -> users.id. The Reminder side owns the JPA association because it contains the foreign-key column.

A User collection is not required to represent this database relationship correctly. Current listing operations query reminders by owner ID with pagination. No cascade is configured: saving or deleting a reminder must not automatically save/delete its user. API services will obtain an existing authenticated User when creating a reminder.

Current flow:

Future service -> ReminderRepository -> Hibernate -> reminders/user_id -> database.

No reminder controller or DTO endpoint is added yet. Entity objects will remain internal to the persistence/service layers; future HTTP responses will use DTOs.

## Annotations

| Annotation | Meaning |
| --- | --- |
| @Entity / @Table | Map Reminder to reminders |
| @ManyToOne(optional=false) | Each reminder must reference one User |
| fetch=LAZY | Ask Hibernate to load owner details when accessed, rather than automatically loading all details with every reminder |
| @JoinColumn(name="user_id") | Map the association through the existing foreign-key column |
| @ForeignKey | Give generated test-schema foreign keys the known name |
| @Enumerated(STRING) | Store names such as PENDING rather than enum ordinal numbers |
| @JdbcTypeCode(VARCHAR) | Keep status compatible with the existing MySQL VARCHAR design instead of choosing a native ENUM column |
| @GeneratedColumn | Let the database calculate retry_count and let Hibernate retrieve generated values |
| insertable=false, updatable=false | Omit retry_count from application INSERT/UPDATE assignments |
| @Version | Include a version check in ORM UPDATE statements to reject stale writes |
| @JdbcTypeCode(TIMESTAMP) | Use UTC JDBC timestamp conversion for Instant values backed by the existing DATETIME design |

GeneratedColumn specifies greatest(attempt_count - 1, 0). Test-schema generation creates the expression in H2. Normal MySQL runs still use ddl-auto=validate, so Hibernate does not replace the existing STORED generated MySQL column.

## Methods and field responsibilities

The protected no-argument constructor allows Hibernate to load a row. The public constructor accepts an existing owner, content, an absolute scheduled Instant, a display zone, and a nonnegative maximum-retry policy snapshot.

A new Reminder starts PENDING, with attempt_count=0, retry_count calculated as 0, and next_attempt_at equal to scheduled_at. Claim/lease/deletion fields start null. Hibernate initializes its Long version to zero on insertion.

updateDetails is allowed only for PENDING or RETRY. It validates all proposed fields before assigning them, preserves message formatting, validates the zone through ZoneId, and truncates timestamps to microseconds. Changing its schedule updates next_attempt_at. It preserves counters and retry policy. A future API service must additionally validate email syntax and that a new schedule is in the future using an injectable Clock; those rules are not claimed here.

cancel is allowed only for PENDING or RETRY and sets CANCELLED plus next_attempt_at=null in one entity change. Completed, failed, cancelled, and in-flight reminders are not editable through these methods.

beforeInsert sets UTC creation/update timestamps; beforeUpdate refreshes the modification time. Getters allow services to read state. There are no arbitrary setters for the owner, status, attempt counters, claim tokens, lease, IDs, or version. Worker claim/retry/outcome operations will be introduced with their atomic transaction design later, not simulated by public setters now.

The message uses JDBC LONGVARCHAR with a TEXT definition and a 10,000-Java-character application limit. Optional blank recipient names become null. Recipient email is trimmed but its case is preserved; login-email normalization remains a separate User policy.

## Repository methods

- findAllByUser_IdAndDeletedAtIsNull: returns a Page of reminders for one owner and excludes soft-deleted rows. User_Id traverses the User association to its id. Pageable supplies bounded page size and ordering.
- findByIdAndUser_IdAndDeletedAtIsNull: requires both reminder ID and owner ID, excluding soft-deleted records. An absent or differently owned record produces Optional.empty().

These helpers support ownership checks; they are not authentication by themselves. Future API services must obtain the owner ID from the authenticated principal, never trust a client-provided owner ID, and use these scoped queries.

## Optimistic locking example

Two readers load version 0. The first commits an update, making the version 1. When the second tries to save its stale version 0, Hibernate detects the mismatch and rejects the write. The test uses detached/current entity instances to reproduce that conflict. This does not implement multi-worker reminder claiming or exactly-once email sending. Future native/bulk worker updates must explicitly participate in version/claim handling because bulk SQL bypasses normal entity callbacks.

## Tests

1. Persist and reload a reminder, its owner, content, initial state, schedule, version, and generated counter.
2. Change attempt_count with a SQL fixture, clear the persistence context, and verify the database-generated retry_count becomes 1 for 2 attempts. This fixture is not an implemented scheduler workflow.
3. Cancel, flush, reload, and verify CANCELLED, no next attempt, incremented version, and edit rejection.
4. Create two owners plus a soft-deleted fixture. Verify paginated owner queries and single-record queries exclude the other owner and hidden row. Soft deletion uses a native SQL fixture after cancellation; a deletion service is not yet implemented.
5. Reproduce a stale entity write and require ObjectOptimisticLockingFailureException.

@DataJpaTest runs these inside rollbackable transactions on embedded H2. The generated H2 schema includes the entity mappings but is not a complete copy of the hand-written MySQL constraints/indexes/collations. Actual MySQL mapping validation remains pending.

## Complete current files

These files are already saved. Open the exact path in VS Code to inspect them; Ctrl+S saves edits. The entity and repository folders already existed. New files were added inside them, and the repository test folder was created in lesson 09. No manual file creation or source copying is needed.

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\entity\ReminderStatus.java

```java
package com.example.reminder.entity;

public enum ReminderStatus {
    PENDING,
    PROCESSING,
    SENT,
    RETRY,
    FAILED,
    CANCELLED
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\entity\Reminder.java

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.hibernate.annotations.GeneratedColumn;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

@Entity
@Table(name = "reminders")
public class Reminder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_reminders_user"))
    private User user;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "recipient_email", nullable = false, length = 254)
    private String recipientEmail;

    @Column(name = "recipient_name", length = 100)
    private String recipientName;

    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "message", nullable = false, columnDefinition = "text")
    private String message;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "scheduled_at", nullable = false)
    private Instant scheduledAt;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "status", nullable = false, length = 20)
    private ReminderStatus status = ReminderStatus.PENDING;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @GeneratedColumn("greatest(attempt_count - 1, 0)")
    @Column(name = "retry_count", insertable = false, updatable = false)
    private int retryCount;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "claim_token", length = 36)
    private String claimToken;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "deleted_at")
    private Instant deletedAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected Reminder() {
        // Required by JPA when loading rows.
    }

    public Reminder(User user, String title, String recipientEmail, String recipientName,
                    String message, Instant scheduledAt, String timeZone, int maxRetries) {
        this.user = Objects.requireNonNull(user, "user is required");
        if (maxRetries < 0) {
            throw new IllegalArgumentException("maxRetries must be nonnegative");
        }
        this.maxRetries = maxRetries;
        applyDetails(title, recipientEmail, recipientName, message, scheduledAt, timeZone);
    }

    public void updateDetails(String title, String recipientEmail, String recipientName,
                              String message, Instant scheduledAt, String timeZone) {
        requireEditable();
        applyDetails(title, recipientEmail, recipientName, message, scheduledAt, timeZone);
    }

    public void cancel() {
        requireEditable();
        status = ReminderStatus.CANCELLED;
        nextAttemptAt = null;
    }

    private void requireEditable() {
        if (status != ReminderStatus.PENDING && status != ReminderStatus.RETRY) {
            throw new IllegalStateException("Only pending or retry reminders can be changed");
        }
    }

    private void applyDetails(String title, String recipientEmail, String recipientName,
                              String message, Instant scheduledAt, String timeZone) {
        String newTitle = requiredText(title, "title", 200).strip();
        String newEmail = requiredText(recipientEmail, "recipientEmail", 254).strip();
        String newName = recipientName == null || recipientName.isBlank()
                ? null : requiredText(recipientName, "recipientName", 100).strip();
        String newMessage = requiredText(message, "message", 10_000);
        Instant newScheduledAt = Objects.requireNonNull(scheduledAt, "scheduledAt is required")
                .truncatedTo(ChronoUnit.MICROS);
        String newZone = ZoneId.of(requiredText(timeZone, "timeZone", 64).strip()).getId();

        this.title = newTitle;
        this.recipientEmail = newEmail;
        this.recipientName = newName;
        this.message = newMessage;
        this.scheduledAt = newScheduledAt;
        this.timeZone = newZone;
        this.nextAttemptAt = newScheduledAt;
    }

    @PrePersist
    private void beforeInsert() {
        Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    private void beforeUpdate() {
        updatedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
    }

    private static String requiredText(String value, String field, int maxLength) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain 1 to " + maxLength + " characters");
        }
        return value;
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getTitle() { return title; }
    public String getRecipientEmail() { return recipientEmail; }
    public String getRecipientName() { return recipientName; }
    public String getMessage() { return message; }
    public Instant getScheduledAt() { return scheduledAt; }
    public String getTimeZone() { return timeZone; }
    public ReminderStatus getStatus() { return status; }
    public int getAttemptCount() { return attemptCount; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public Instant getNextAttemptAt() { return nextAttemptAt; }
    public String getClaimToken() { return claimToken; }
    public Instant getLeaseUntil() { return leaseUntil; }
    public Long getVersion() { return version; }
    public Instant getDeletedAt() { return deletedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\repository\ReminderRepository.java

```java
package com.example.reminder.repository;

import com.example.reminder.entity.Reminder;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReminderRepository extends JpaRepository<Reminder, Long> {

    Page<Reminder> findAllByUser_IdAndDeletedAtIsNull(Long userId, Pageable pageable);

    Optional<Reminder> findByIdAndUser_IdAndDeletedAtIsNull(Long id, Long userId);
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\test\java\com\example\reminder\repository\ReminderRepositoryTest.java

```java
package com.example.reminder.repository;

import com.example.reminder.entity.Reminder;
import com.example.reminder.entity.ReminderStatus;
import com.example.reminder.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class ReminderRepositoryTest {

    private static final Instant DUE = Instant.parse("2099-01-01T03:30:00Z");

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ReminderRepository reminderRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void persistsOwnerAndInitialReminderState() {
        User owner = saveUser("owner@example.test");
        Reminder reminder = reminderRepository.saveAndFlush(newReminder(owner, "Doctor visit"));
        Long id = reminder.getId();
        entityManager.clear();

        Reminder loaded = reminderRepository
                .findByIdAndUser_IdAndDeletedAtIsNull(id, owner.getId()).orElseThrow();
        assertEquals(owner.getId(), loaded.getUser().getId());
        assertEquals("owner@example.test", loaded.getUser().getEmail());
        assertEquals("Doctor visit", loaded.getTitle());
        assertEquals("recipient@example.test", loaded.getRecipientEmail());
        assertEquals("Learner", loaded.getRecipientName());
        assertEquals("Bring your documents", loaded.getMessage());
        assertEquals("Asia/Kolkata", loaded.getTimeZone());
        assertEquals(DUE, loaded.getScheduledAt());
        assertEquals(DUE, loaded.getNextAttemptAt());
        assertEquals(ReminderStatus.PENDING, loaded.getStatus());
        assertEquals(0, loaded.getAttemptCount());
        assertEquals(0, loaded.getRetryCount());
        assertEquals(3, loaded.getMaxRetries());
        assertEquals(0L, loaded.getVersion());
        assertNull(loaded.getClaimToken());
        assertNull(loaded.getLeaseUntil());
        assertNull(loaded.getDeletedAt());
        assertNotNull(loaded.getCreatedAt());
    }

    @Test
    void databaseGeneratesRetryCountFromAttemptCount() {
        Reminder reminder = reminderRepository.saveAndFlush(
                newReminder(saveUser("retry@example.test"), "Retry fixture"));

        // Fixture only: scheduler state transitions are not implemented yet.
        entityManager.createNativeQuery("""
                UPDATE reminders SET attempt_count = 2, status = 'RETRY', version = version + 1
                WHERE id = :id
                """).setParameter("id", reminder.getId()).executeUpdate();
        entityManager.clear();

        Reminder loaded = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(2, loaded.getAttemptCount());
        assertEquals(1, loaded.getRetryCount());
        assertEquals(ReminderStatus.RETRY, loaded.getStatus());
    }

    @Test
    void cancellationRemovesNextAttemptAndPreventsFurtherEdits() {
        Reminder reminder = reminderRepository.saveAndFlush(
                newReminder(saveUser("cancel@example.test"), "Cancel fixture"));
        reminder.cancel();
        reminderRepository.flush();
        entityManager.clear();

        Reminder loaded = reminderRepository.findById(reminder.getId()).orElseThrow();
        assertEquals(ReminderStatus.CANCELLED, loaded.getStatus());
        assertNull(loaded.getNextAttemptAt());
        assertEquals(1L, loaded.getVersion());
        assertThrows(IllegalStateException.class, () -> loaded.updateDetails(
                "Changed", "recipient@example.test", null, "Changed", DUE, "Asia/Kolkata"));
    }

    @Test
    void ownerQueriesExcludeOtherOwnersAndSoftDeletedReminders() {
        User owner = saveUser("list-owner@example.test");
        User otherOwner = saveUser("other-owner@example.test");
        Reminder visible = reminderRepository.saveAndFlush(newReminder(owner, "Visible"));
        Reminder other = reminderRepository.saveAndFlush(newReminder(otherOwner, "Other"));
        Reminder hidden = reminderRepository.saveAndFlush(newReminder(owner, "Hidden"));
        hidden.cancel();
        reminderRepository.flush();
        entityManager.createNativeQuery("""
                UPDATE reminders SET deleted_at = CURRENT_TIMESTAMP, version = version + 1
                WHERE id = :id
                """).setParameter("id", hidden.getId()).executeUpdate();
        entityManager.clear();

        Page<Reminder> page = reminderRepository.findAllByUser_IdAndDeletedAtIsNull(
                owner.getId(), PageRequest.of(0, 10, Sort.by("scheduledAt", "id")));
        assertEquals(1, page.getTotalElements());
        assertEquals(visible.getId(), page.getContent().get(0).getId());
        assertTrue(reminderRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                other.getId(), owner.getId()).isEmpty());
        assertTrue(reminderRepository.findByIdAndUser_IdAndDeletedAtIsNull(
                hidden.getId(), owner.getId()).isEmpty());
    }

    @Test
    void rejectsAStaleUpdateAfterAnotherWriterChangesTheReminder() {
        Reminder stale = reminderRepository.saveAndFlush(
                newReminder(saveUser("version@example.test"), "Version fixture"));
        entityManager.detach(stale);

        Reminder current = reminderRepository.findById(stale.getId()).orElseThrow();
        current.cancel();
        reminderRepository.flush();
        assertEquals(1L, current.getVersion());

        stale.cancel();
        assertThrows(ObjectOptimisticLockingFailureException.class,
                () -> reminderRepository.saveAndFlush(stale));
    }

    private User saveUser(String email) {
        return userRepository.saveAndFlush(new User(
                email, "synthetic-hash-fixture", "Learner", "Asia/Kolkata"));
    }

    private Reminder newReminder(User owner, String title) {
        return new Reminder(owner, title, "recipient@example.test", "Learner",
                "Bring your documents", DUE, "Asia/Kolkata", 3);
    }
}

```

## Learner checkpoint

Run in ordinary PowerShell:

```powershell
Set-Location 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend'
.\mvnw.cmd test
```

No MySQL password is needed. Expected and assistant-verified:

```text
Tests run: 9, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

The duplicate user-email test may log an expected H2 23505 unique-key error; the passing summary is what matters. The test runner exits by itself. H2 create/drop statements in the test log concern the isolated in-memory database, not your MySQL tables.

The assistant's local log is backend/reminder-jpa-build.log, excluded from Git by the existing *.log rule. Reports are in backend/target/surefire-reports.

Share the summary or unexpected errors before the next lesson, EmailLog mapping. Runtime MySQL and Postman checks remain pending.

## References

- https://docs.hibernate.org/orm/6.6/javadocs/org/hibernate/annotations/GeneratedColumn.html
- https://docs.spring.io/spring-boot/3.5/reference/data/sql.html
