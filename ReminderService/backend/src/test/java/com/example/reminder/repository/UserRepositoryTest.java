package com.example.reminder.repository;

import com.example.reminder.entity.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    void savesAndReloadsNormalizedUserWithGeneratedIdAndUtcAuditTimes() {
        User user = userRepository.saveAndFlush(new User(
                " Learner@Example.test ", "synthetic-hash-fixture", "Learner", "Asia/Kolkata"));
        Long id = user.getId();
        entityManager.clear();

        User loaded = userRepository.findByEmail("learner@example.test").orElseThrow();

        assertNotNull(id);
        assertTrue(id > 0);
        assertEquals(id, loaded.getId());
        assertEquals("learner@example.test", loaded.getEmail());
        assertEquals("synthetic-hash-fixture", loaded.getPasswordHash());
        assertEquals("Learner", loaded.getDisplayName());
        assertEquals("Asia/Kolkata", loaded.getTimeZone());
        assertEquals(0, loaded.getTokenVersion());
        assertNotNull(loaded.getCreatedAt());
        assertEquals(loaded.getCreatedAt(), loaded.getUpdatedAt());
        assertFalse(loaded.getCreatedAt().isAfter(Instant.now()));
    }

    @Test
    void updatesProfileWithoutChangingCreationTime() {
        User user = userRepository.saveAndFlush(new User(
                "profile@example.test", "synthetic-hash-fixture", "Before", "Asia/Kolkata"));
        Instant originalCreatedAt = user.getCreatedAt();
        Instant originalUpdatedAt = user.getUpdatedAt();

        user.updateProfile("After", "Europe/London");
        userRepository.flush();
        entityManager.clear();

        User loaded = userRepository.findById(user.getId()).orElseThrow();
        assertEquals("After", loaded.getDisplayName());
        assertEquals("Europe/London", loaded.getTimeZone());
        assertEquals(originalCreatedAt, loaded.getCreatedAt());
        assertFalse(loaded.getUpdatedAt().isBefore(originalUpdatedAt));
    }

    @Test
    void rejectsDuplicateNormalizedEmailAtDatabaseBoundary() {
        userRepository.saveAndFlush(new User(
                "unique@example.test", "synthetic-hash-fixture", "First", "Asia/Kolkata"));

        assertThrows(DataIntegrityViolationException.class, () ->
                userRepository.saveAndFlush(new User(
                        " UNIQUE@example.test ", "synthetic-hash-fixture", "Second", "Asia/Kolkata")));
    }
}
