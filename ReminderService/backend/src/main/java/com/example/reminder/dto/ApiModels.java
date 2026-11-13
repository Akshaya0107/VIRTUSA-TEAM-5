package com.example.reminder.dto;

import com.example.reminder.entity.*;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

public final class ApiModels {
    private ApiModels() {}

    public record RegisterRequest(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(min=8,max=72) String password,
            @NotBlank @Size(max=100) String displayName, @NotBlank @Size(max=64) String timeZone) {}
    public record LoginRequest(@NotBlank @Email @Size(max=254) String email,
            @NotBlank @Size(max=72) String password) {}
    public record ProfileRequest(@NotBlank @Size(max=100) String displayName,
            @NotBlank @Size(max=64) String timeZone) {}
    public record UserView(Long id, String email, String displayName, String timeZone) {
        public static UserView from(User u) { return new UserView(u.getId(),u.getEmail(),u.getDisplayName(),u.getTimeZone()); }
    }
    public record TokenView(String accessToken, String tokenType, Instant expiresAt, UserView user) {}
    public record ReminderRequest(@NotBlank @Size(max=200) String title,
            @NotBlank @Email @Size(max=254) String recipientEmail, @Size(max=100) String recipientName,
            @NotBlank @Size(max=10000) String message, @NotNull Instant scheduledAt,
            @NotBlank @Size(max=64) String timeZone, @PositiveOrZero Long version) {}
    public record ReminderView(Long id, String title, String recipientEmail, String recipientName,
            String message, Instant scheduledAt, String timeZone, ReminderStatus status,
            int attemptCount, int retryCount, int maxRetries, Instant nextAttemptAt,
            Long version, Instant createdAt) {
        public static ReminderView from(Reminder r) { return new ReminderView(r.getId(),r.getTitle(),
                r.getRecipientEmail(),r.getRecipientName(),r.getMessage(),r.getScheduledAt(),r.getTimeZone(),
                r.getStatus(),r.getAttemptCount(),r.getRetryCount(),r.getMaxRetries(),r.getNextAttemptAt(),
                r.getVersion(),r.getCreatedAt()); }
    }
    public record LogView(Long id, Long reminderId, String reminderTitle, String recipientEmail,
            int attemptNumber, Instant startedAt, Instant completedAt, EmailAttemptStatus status, String failureReason) {
        public static LogView from(EmailLog l) { return new LogView(l.getId(),l.getReminder().getId(),
                l.getReminderTitle(),l.getRecipientEmail(),l.getAttemptNumber(),l.getStartedAt(),l.getCompletedAt(),
                l.getStatus(),l.getFailureReason()); }
    }
    public record PageView<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
        public static <T> PageView<T> from(Page<T> p) { return new PageView<>(p.getContent(),p.getNumber(),p.getSize(),p.getTotalElements(),p.getTotalPages()); }
    }
    public record DashboardView(long total, long pending, long processing, long sent, long retry,
            long failed, long cancelled, List<ReminderView> upcoming, List<LogView> recentActivity) {}
}
