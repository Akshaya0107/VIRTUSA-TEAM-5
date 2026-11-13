# Connect Spring Boot to MySQL

## Scope and evidence

The learner has verified a fresh reminder_app login to smartreminder. No password is stored in this lesson. The backend now has datasource configuration and a built-in database health endpoint. The assistant executed the automated HTTP integration test with H2: 1 test, 0 failures, 0 errors. H2 is a test-only in-memory database. This does not verify MySQL behavior or prove that the application's MySQL credentials work. The learner subsequently verified the live Java-to-MySQL health check in a browser: /actuator/health/database returned {"status":"UP"}. Postman verification remains pending.

## Concepts before code

JDBC is Java's standard database connection interface. MySQL Connector/J implements it for MySQL. Spring Boot configures a DataSource, backed by a HikariCP pool that reuses connections. Actuator provides health reporting; our database health group contains only its db indicator. It attempts a connection and validates the database before returning a status.

Request flow:

HTTP client -> Actuator database health group -> DataSource/HikariCP -> MySQL Connector/J -> MySQL -> health JSON.

GET /api/health remains the simple controller exercise. GET /actuator/health/database checks the configured database. Its default successful response is HTTP 200 with {"status":"UP"}; a detected database failure returns HTTP 503 with {"status":"DOWN"}. It does not prove that application tables exist or CRUD permissions are correct.

Only the health management endpoint is exposed over HTTP. Health details and component names are hidden. We will revisit authorization when Spring Security is added.

## Dependencies

- spring-boot-starter-jdbc: Spring JDBC and HikariCP connection pooling.
- mysql-connector-j, runtime scope: MySQL driver needed when running the application.
- spring-boot-starter-actuator: built-in monitoring and health support.
- h2, test scope: isolated database available only to tests.

Spring Data JPA will be introduced with the entities after schema creation. JDBC is also the foundation used by JPA underneath.

## Configuration explanation

- DB_URL optionally overrides the full JDBC URL; its default points to local smartreminder.
- connectionTimeZone=UTC and forceConnectionTimeZoneToSession=true establish UTC connection/session handling. DATETIME values still rely on the application's UTC convention.
- DB_USERNAME defaults to reminder_app.
- DB_PASSWORD has no hardcoded value or fallback; normal startup requires it.
- Hikari connection-timeout limits the pool acquisition wait to 5 seconds. It is not a complete bound on every network operation.
- validation-timeout limits connection validation to 2 seconds.
- spring.sql.init.mode=never prevents automatic execution of SQL initialization scripts.
- group.database.include=db creates /actuator/health/database and includes only the database indicator.

## How the password prompt works

start-local.ps1 prompts using Read-Host -AsSecureString. NetworkCredential converts it to the plain string required by the process environment; this is not encryption of environment variables. The password is held in memory and inherited by Maven and Java, not saved into a source file or passed as a command-line argument. The script restores the previous process environment value when it exits normally through its finally block. The newly launched PowerShell process also ends after the script exits.

Push-Location enters the script's backend folder; Pop-Location restores the previous folder. $PSScriptRoot identifies the script's own directory. try/finally ensures cleanup runs during normal failure and shutdown paths. A nonzero Maven exit code produces an error instead of implying a successful launch.

## Full current file contents

All files below have already been saved. Open the exact path in VS Code to inspect them. Use Ctrl+S to save any edits. src/test/resources was created for test-only configuration.

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
			<artifactId>spring-boot-starter-jdbc</artifactId>
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

management.endpoints.web.exposure.include=health
management.endpoint.health.show-details=never
management.endpoint.health.show-components=never
management.endpoint.health.group.database.include=db

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\start-local.ps1

```powershell
$ErrorActionPreference = 'Stop'

$previousDbPassword = [Environment]::GetEnvironmentVariable('DB_PASSWORD', 'Process')
$privatePassword = Read-Host 'MySQL password for reminder_app (input hidden)' -AsSecureString

try {
    if ($privatePassword.Length -eq 0) {
        throw 'Enter the nonempty application account password.'
    }

    $env:DB_PASSWORD = [System.Net.NetworkCredential]::new('', $privatePassword).Password

    Push-Location -LiteralPath $PSScriptRoot
    try {
        & .\mvnw.cmd spring-boot:run
        if ($LASTEXITCODE -ne 0) {
            throw 'The backend stopped with an error. Review the startup messages above.'
        }
    }
    finally {
        Pop-Location
    }
}
finally {
    [Environment]::SetEnvironmentVariable('DB_PASSWORD', $previousDbPassword, 'Process')
    $privatePassword.Dispose()
}

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\test\resources\application-test.properties

```properties
spring.datasource.url=jdbc:h2:mem:reminder_test;DB_CLOSE_ON_EXIT=FALSE
spring.datasource.username=sa
spring.datasource.password=
spring.datasource.driver-class-name=org.h2.Driver

```

### C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend\src\test\java\com\example\reminder\ReminderApplicationTests.java

```java
package com.example.reminder;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class ReminderApplicationTests {

	@Autowired
	private TestRestTemplate restTemplate;

	@Test
	void databaseHealthChecksTestDatabaseWithoutExposingDetails() {
		ResponseEntity<JsonNode> response = restTemplate.getForEntity(
				"/actuator/health/database", JsonNode.class);

		assertEquals(HttpStatus.OK, response.getStatusCode());
		JsonNode body = response.getBody();
		assertNotNull(body);
		assertEquals("UP", body.path("status").asText());
		assertFalse(body.has("components"));
		assertFalse(body.has("details"));
	}

}

```

## Automated test explanation

@SpringBootTest(webEnvironment = RANDOM_PORT) starts a real HTTP server on an available port. @ActiveProfiles("test") loads application-test.properties, overriding the MySQL URL and password with test-only H2 settings. @Autowired injects TestRestTemplate, which sends an actual request to that server. The test checks HTTP 200, UP, and the absence of details and component fields. The test therefore exercises Spring configuration, HTTP routing, and a real H2 database connection. It does not contact MySQL.

Run at PowerShell:

```powershell
Set-Location 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend'
.\mvnw.cmd test
```

Expected and assistant-verified: Tests run: 1, Failures: 0, Errors: 0, Skipped: 0; BUILD SUCCESS. Test reports are in target/surefire-reports. database-check-build.log records the local assistant run and is excluded from Git by the existing *.log rule.

## Live MySQL check: learner checkpoint

1. If currently at mysql>, use exit; to return to PowerShell.
2. Stop any previous Spring Boot instance using Ctrl+C in its log window so port 8080 is free.
3. In an ordinary PowerShell window run:

```powershell
Set-Location 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend'
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-local.ps1
```

-NoProfile avoids user startup customizations. -ExecutionPolicy Bypass applies only to the newly launched PowerShell process and lets this local script run; it does not permanently change Windows execution policy. Organizational policy can still take precedence.

4. At the hidden prompt, type the current reminder_app password, without surrounding quotes. Keep it private. Wait for Started ReminderApplication and leave that terminal open.
5. In a second PowerShell window run:

```powershell
$response = Invoke-WebRequest -Uri 'http://localhost:8080/actuator/health/database' -UseBasicParsing
$response.StatusCode
$response.Content
```

Expected: 200 and {"status":"UP"}. The learner verified the UP body against the live MySQL configuration in a browser; the browser report did not separately include the HTTP status. A successful app-start message alone does not prove a successful MySQL connection because the connection pool can initialize on demand when this health request arrives.

6. In Postman, send GET http://localhost:8080/actuator/health/database with No Auth and no body. Save it as Database Health in the Smart Reminder Service collection. Postman execution is pending.
7. Share only the HTTP status and response, or sanitized errors. Do not print environment variable values or share passwords.

## Troubleshooting

- Could not resolve placeholder DB_PASSWORD: use start-local.ps1 instead of the earlier bare spring-boot:run command, or provide DB_PASSWORD securely in the launching process.
- Port 8080 in use: stop the previous Spring Boot instance before restarting.
- HTTP 404 at the database health URL: confirm the updated application was restarted and use /actuator/health/database exactly.
- HTTP 503 / DOWN: inspect the server log for an authentication, database-name, TLS, or connectivity error. Share a sanitized error; do not disable TLS verification as a generic fix.
- Access denied: verify the current application-account password with the MySQL client; root credentials are not used by the app.
- Communications link failure / connection refused: check Get-Service MySQL80 and the configured MySQL port.
- H2 test success with MySQL failure: expected distinction; H2 does not verify MySQL configuration or SQL compatibility.

No application tables or entities are created by this step. The next phase begins only after the live connection check passes.

## References

- https://docs.spring.io/spring-boot/3.5/reference/data/sql.html
- https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html
- https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html

