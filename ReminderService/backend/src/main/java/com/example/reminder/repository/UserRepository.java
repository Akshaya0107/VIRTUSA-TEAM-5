package com.example.reminder.repository;

import com.example.reminder.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    @org.springframework.data.jpa.repository.Modifying(clearAutomatically = true, flushAutomatically = true)
    @org.springframework.data.jpa.repository.Query("update User u set u.tokenVersion = u.tokenVersion + 1, u.updatedAt = :now where u.id = :id")
    int revokeTokens(@org.springframework.data.repository.query.Param("id") Long id,
            @org.springframework.data.repository.query.Param("now") java.time.Instant now);
}
