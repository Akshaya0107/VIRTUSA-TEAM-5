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
