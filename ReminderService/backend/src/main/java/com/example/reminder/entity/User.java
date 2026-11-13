package com.example.reminder.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;

@Entity
@Table(name = "users", uniqueConstraints = {
        @UniqueConstraint(name = "uk_users_email", columnNames = "email")
})
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "email", nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;

    @Column(name = "time_zone", nullable = false, length = 64)
    private String timeZone;

    @Column(name = "token_version", nullable = false)
    private long tokenVersion;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.TIMESTAMP)
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected User() {
        // Required by JPA when loading database rows.
    }

    public User(String email, String passwordHash, String displayName, String timeZone) {
        String normalizedEmail = requiredText(email, "email", 254)
                .strip().toLowerCase(Locale.ROOT);
        this.email = requiredText(normalizedEmail, "email", 254);
        this.passwordHash = requiredText(passwordHash, "passwordHash", 255);
        updateProfile(displayName, timeZone);
    }

    public void updateProfile(String displayName, String timeZone) {
        String normalizedName = requiredText(displayName, "displayName", 100).strip();
        String normalizedZone = ZoneId.of(requiredText(timeZone, "timeZone", 64).strip()).getId();
        this.displayName = normalizedName;
        this.timeZone = normalizedZone;
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

    public Long getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getTimeZone() {
        return timeZone;
    }

    public long getTokenVersion() {
        return tokenVersion;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
