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
