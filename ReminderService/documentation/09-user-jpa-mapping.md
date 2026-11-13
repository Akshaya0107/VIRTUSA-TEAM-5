# JPA lesson 1: map the users table

## Current scope

The learner explicitly chose to defer the MySQL account-password problem and continue development. This lesson does not attempt to authenticate to MySQL, reset credentials, or modify the existing MySQL schema. Actual MySQL ORM validation and persistence remain pending.

The assistant added the User entity, UserRepository, and repository integration tests, and ran the complete Maven test suite. Verified result: 4 tests, 0 failures, 0 errors, 0 skipped; BUILD SUCCESS. Three repository tests use an embedded H2 database. The existing HTTP health integration test also uses H2. These are real persistence/HTTP tests, not a claim of MySQL compatibility verification.

## Concepts before code

JPA is a specification for object-relational mapping. Hibernate implements JPA. Spring Data JPA generates repository implementations that use Hibernate. JDBC remains underneath this stack; replacing the JDBC starter with spring-boot-starter-data-jpa does not remove database connectivity or connection pooling.

An entity is a Java class mapped to a database table. One persisted User object corresponds to one users row. A repository gives Java code persistence operations without manually writing an INSERT or SELECT for each basic operation.

Current flow:

Test or future service -> UserRepository -> JPA/Hibernate -> JDBC connection -> database.

There is no user controller or registration API in this lesson. Entity objects will not be exposed as API responses. Future API DTOs will define which fields clients receive.

## Files and exact locations

All files below are already saved. Open the linked source or its exact path in VS Code to inspect it; press Ctrl+S to save edits. Do not enter Java into PowerShell. No manual copying is needed.

- backend/src/main/java/com/example/reminder/entity/User.java
- backend/src/main/java/com/example/reminder/repository/UserRepository.java
- backend/src/test/java/com/example/reminder/repository/UserRepositoryTest.java
- Updated backend/pom.xml, backend/src/main/resources/application.properties, and backend/src/test/resources/application-test.properties.

The repository test folder was created under the existing test package. To create it manually from backend, the equivalent command is:

```powershell
New-Item -ItemType Directory -Path '.\src\test\java\com\example\reminder\repository' -Force
```

## Entity annotations and fields

| Construct | Meaning |
| --- | --- |
| @Entity | Register the class as a JPA entity |
| @Table(name="users") | Map it to the existing users table |
| @UniqueConstraint | Describe the unique email key, also used when generating the test schema |
| @Id | Identify the primary-key field |
| @GeneratedValue(IDENTITY) | Obtain the generated ID from MySQL AUTO_INCREMENT or the test database's identity mechanism |
| @Column | Match a Java field to a column and describe its length/nullability |
| @JdbcTypeCode(SqlTypes.TIMESTAMP) | Map an Instant through a JDBC timestamp instead of a timezone-bearing SQL column; paired with UTC JDBC configuration for the existing DATETIME design |
| @PrePersist | Run the timestamp callback before an INSERT |
| @PreUpdate | Run the update timestamp callback before an UPDATE |

Long id starts as null before insertion. tokenVersion starts at zero; its logout/invalidation behavior will be implemented in the security module. Instant represents an absolute moment. Timestamp callbacks truncate to microseconds to match DATETIME(6). createdAt is updatable=false so normal ORM UPDATE statements do not change it.

The protected no-argument constructor is for Hibernate loading rows. The public constructor accepts an already-encoded password hash, normalizes login email with Locale.ROOT, and validates basic text requirements. It does not encode passwords. A future authentication service must BCrypt-encode a password before calling it; test fixture strings are not real login hashes. DTO validation and API error handling are still future work.

updateProfile validates both proposed fields before assigning either, trims the display name, and validates the time-zone string using Java ZoneId. No public setter allows clients to assign an ID or audit timestamps. Getter methods expose values to Java services and tests. There is no generated toString that prints the hash.

Only User is mapped in this lesson. The one-to-many reminder relationship will be added when Reminder is introduced; no placeholder class is needed now.

## Repository explanation

JpaRepository<User, Long> specifies the entity type and ID type. It supplies save, saveAndFlush, findById, findAll, flush, and other persistence methods. Spring generates the implementation during startup, so we do not write a UserRepositoryImpl.

findByEmail is a derived query: Spring interprets its method name to query the User.email property. Optional<User> represents either one result or no result. Callers must use the same normalized email policy when looking up an account. The database unique constraint remains essential because concurrent registrations could both pass an earlier existence check.

## Configuration explanation

Normal application.properties uses spring.jpa.hibernate.ddl-auto=validate. Hibernate checks mapped tables and columns against the schema. It does not create, replace, or alter MySQL tables. Validation is not a complete check of every index, collation, or handwritten CHECK constraint.

spring.jpa.open-in-view=false keeps persistence work within explicit application/service transactions rather than extending a persistence context through HTTP response rendering. hibernate.jdbc.time_zone=UTC supplies UTC for JDBC timestamp conversion. This does not change existing stored data.

The test-only application-test.properties overrides ddl-auto with create-drop. Hibernate creates tables in embedded H2 for the tests and removes them when the context closes. This override is located only in src/test/resources. It does not apply to a normal MySQL application run.

Since JPA initializes its EntityManagerFactory and validates the schema during startup, a normal application run now needs valid MySQL credentials even before health is requested. That live check is intentionally deferred per the learner's request.

## Tests and responsibilities

@DataJpaTest starts the persistence portion of Spring and an embedded database. Its tests are transactional and roll back automatically. @ActiveProfiles("test") selects the test configuration. @Autowired supplies the generated repository and EntityManager.

The tests exercise:

1. Save and reload after EntityManager.clear(), proving the result comes from the database rather than just the current in-memory entity. Verify generated ID, normalized email, stored fields, tokenVersion, and UTC Instant audit fields.
2. Update a profile, flush the UPDATE, clear the persistence context, and reload. Verify the changed fields and preserved creation time.
3. Attempt a duplicate normalized email and require DataIntegrityViolationException from the database boundary.

saveAndFlush forces pending writes to execute now. clear detaches managed entities so a later repository read must load database state. assertThrows makes the duplicate-key failure an expected test outcome. A logged SQL Error 23505 during that test is expected if the overall summary has zero failures/errors.

H2 generates its tables from the annotations. These tests do not execute the MySQL-specific migration, reproduce its binary collation exactly, validate all MySQL constraints, or test JWT/BCrypt/registration behavior.

## Full current code

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\entity\User.java

```java
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

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\java\com\example\reminder\repository\UserRepository.java

```java
package com.example.reminder.repository;

import com.example.reminder.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\test\java\com\example\reminder\repository\UserRepositoryTest.java

```java
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

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\pom.xml

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
	xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
	<modelVersion>4.0.0</modelVersion>
	<parent>
		<groupId>org.springframework.boot</groupId>
		<artifactId>spring-boot-starter-parent</artifactId>
		<version>3.5.16</version>
		<relativePath/> <!-- lookup parent from repository -->
	</parent>
	<groupId>com.example</groupId>
	<artifactId>reminder</artifactId>
	<version>0.0.1-SNAPSHOT</version>
	<name>reminder</name>
	<description>Smart Reminder and Email Notification Service</description>
	<url/>
	<licenses>
		<license/>
	</licenses>
	<developers>
		<developer/>
	</developers>
	<scm>
		<connection/>
		<developerConnection/>
		<tag/>
		<url/>
	</scm>
	<properties>
		<java.version>17</java.version>
	</properties>
	<dependencies>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-validation</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-web</artifactId>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-data-jpa</artifactId>
		</dependency>
		<dependency>
			<groupId>com.mysql</groupId>
			<artifactId>mysql-connector-j</artifactId>
			<scope>runtime</scope>
		</dependency>
		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-actuator</artifactId>
		</dependency>

		<dependency>
			<groupId>org.springframework.boot</groupId>
			<artifactId>spring-boot-starter-test</artifactId>
			<scope>test</scope>
		</dependency>
		<dependency>
			<groupId>com.h2database</groupId>
			<artifactId>h2</artifactId>
			<scope>test</scope>
		</dependency>
	</dependencies>

	<build>
		<plugins>
			<plugin>
				<groupId>org.springframework.boot</groupId>
				<artifactId>spring-boot-maven-plugin</artifactId>
			</plugin>
		</plugins>
	</build>

</project>

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\main\resources\application.properties

```properties
spring.application.name=reminder

spring.datasource.url=${DB_URL:jdbc:mysql://127.0.0.1:3306/smartreminder?connectionTimeZone=UTC&forceConnectionTimeZoneToSession=true}
spring.datasource.username=${DB_USERNAME:reminder_app}
spring.datasource.password=${DB_PASSWORD}
spring.datasource.hikari.connection-timeout=5000
spring.datasource.hikari.validation-timeout=2000
spring.sql.init.mode=never

spring.jpa.hibernate.ddl-auto=validate
spring.jpa.open-in-view=false
spring.jpa.properties.hibernate.jdbc.time_zone=UTC

management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
management.endpoint.health.show-components=never
management.endpoint.health.group.database.include=db

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\test\resources\application-test.properties

```properties
spring.datasource.url=jdbc:h2:mem:reminder_test;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=create-drop

```

## Run this checkpoint

In ordinary PowerShell, run:

```powershell
Set-Location 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend'
.\mvnw.cmd test
```

No MySQL password or running MySQL service is needed for these tests. Test classes explicitly select the test profile; the application will not switch to H2 during a normal run.

Expected and assistant-verified:

```text
Tests run: 4, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Share the final summary or unexpected error. Tests exit by themselves; there is no long-running server to keep open. The assistant's build log is backend/user-jpa-build.log and test reports are in backend/target/surefire-reports. The existing *.log ignore rule excludes this local build log from Git.

Next lesson after learner verification: map Reminder and its user ownership relationship. MySQL credentials and Postman checks stay explicitly pending until resumed.

## References

- https://docs.spring.io/spring-boot/3.5/reference/data/sql.html
- https://docs.spring.io/spring-data/jpa/reference/jpa/entity-persistence.html
