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
