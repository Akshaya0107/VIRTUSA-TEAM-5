package com.example.reminder.service;

import com.example.reminder.entity.*;
import java.time.Instant;

public record EmailWork(Long reminderId, String claimToken, int attemptNumber, int maxRetries,
        String title, String recipientEmail, String recipientName, String message,
        Instant scheduledAt, String timeZone, Instant startedAt) {
    public static EmailWork from(Reminder r,Instant started) { return new EmailWork(r.getId(),r.getClaimToken(),r.getAttemptCount(),r.getMaxRetries(),r.getTitle(),r.getRecipientEmail(),r.getRecipientName(),r.getMessage(),r.getScheduledAt(),r.getTimeZone(),started); }
}
