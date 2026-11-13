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

    public void softDelete(Instant now) {
        if (status != ReminderStatus.CANCELLED && status != ReminderStatus.SENT && status != ReminderStatus.FAILED) {
            throw new IllegalStateException("Cancel a pending reminder before deleting it");
        }
        deletedAt = Objects.requireNonNull(now).truncatedTo(ChronoUnit.MICROS);
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
