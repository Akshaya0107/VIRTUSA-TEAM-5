# Backend starter: first learning checkpoint

## What exists

The backend was generated from Spring Initializr, then configured for Spring Boot 3.5.16 to meet this project's Java 17 and JUnit 5 requirements. Maven Central publication and a clean startup test were verified. Maven Wrapper uses Maven 3.10.0. This project is still a starter: no reminder APIs, authentication, database connection, or email processing exists yet.

Project root: C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService

## Dependency purposes

- spring-boot-starter-web: Spring MVC REST API support, JSON conversion, and embedded Tomcat.
- spring-boot-starter-validation: Jakarta Bean Validation, for required fields, email addresses, and other input constraints when DTOs are added.
- spring-boot-starter-test: Spring test utilities, JUnit 5, Mockito, and assertion libraries. Test scope excludes these from the application runtime.
- spring-boot-maven-plugin: runs the application and creates an executable JAR when packaging.
- spring-boot-starter-parent: supplies consistent dependency and plugin versions.

Maven downloads transitive dependencies: libraries needed by the dependencies we explicitly select. We let the Spring Boot parent manage compatible library versions.

## Folder roles

All Java package folders below are inside backend\src\main\java\com\example\reminder. They are currently empty except for ReminderApplication.java. Empty folders are visible locally but will not be preserved by Git until files are added.

| Folder | Future responsibility |
| --- | --- |
| controller | Receive HTTP requests and return DTO responses |
| service | Apply business rules and coordinate operations |
| repository | Read and write database records |
| entity | Map Java objects to database tables |
| dto | Define validated request and response data |
| config | Configure application infrastructure |
| security | Authenticate users and enforce access rules |
| scheduler | Find and process due reminders without the browser |
| exception | Convert application errors into consistent API responses |

backend\src\main\resources\templates is reserved for Thymeleaf email templates.
backend\src\test\java contains test code.
backend\target contains generated compiler output and test reports; do not edit it.

To create these package folders manually from the project root:

```powershell
$packageRoot = '.\backend\src\main\java\com\example\reminder'
'controller','service','repository','entity','dto','config','security','scheduler','exception' | ForEach-Object {
    New-Item -ItemType Directory -Path (Join-Path $packageRoot $_) -Force
}
New-Item -ItemType Directory -Path '.\backend\src\main\resources\templates' -Force
```

The pipe passes each name to ForEach-Object. $_ is the current name. Join-Path safely joins folder names. -Force permits an existing directory to be reused.

## Read the starter

The following sections contain the complete current content of the core configuration, application, and test files. These files are already saved. To edit one, open its exact path in VS Code, make the change, and press Ctrl+S. Do not paste Java or XML into PowerShell.

### backend\pom.xml

modelVersion selects the Maven POM format. groupId identifies the project family; artifactId names the build artifact. The project version 0.0.1-SNAPSHOT marks our own work in development, independently of the stable Spring Boot parent version. java.version targets Java 17. dependencies declares libraries, and build/plugins configures Maven tasks. Empty metadata elements are generated placeholders.

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
			<artifactId>spring-boot-starter-test</artifactId>
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

### backend\src\main\java\com\example\reminder\ReminderApplication.java

package matches the Java folder hierarchy. import makes Spring classes available. @SpringBootApplication combines configuration, automatic configuration, and component scanning. Scanning begins in com.example.reminder and its subpackages. main is the Java entry point. SpringApplication.run creates and starts the Spring application; normal application startup also starts the embedded server.

```java
package com.example.reminder;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class ReminderApplication {

	public static void main(String[] args) {
		SpringApplication.run(ReminderApplication.class, args);
	}

}

```

### backend\src\main\resources\application.properties

spring.application.name gives the application its logical name. This file contains no database, JWT, or SMTP secrets. Later we will reference environment variables for sensitive configuration.

```properties
spring.application.name=reminder

```

### backend\src\test\java\com\example\reminder\ReminderApplicationTests.java

@SpringBootTest loads the Spring application context. @Test marks a JUnit test method. contextLoads has an empty body because failure to initialize Spring itself makes this test fail. It does not test an HTTP endpoint, database integration, or business logic. The default test configuration does not open a listening HTTP port.

```java
package com.example.reminder;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class ReminderApplicationTests {

	@Test
	void contextLoads() {
	}

}

```

### backend\.mvn\wrapper\maven-wrapper.properties

The wrapper scripts read this file to locate and download a pinned Maven distribution. mvnw.cmd is the Windows entry point; mvnw is for Unix-like shells. Keep the wrapper scripts generated by Initializr. A global mvn command is unnecessary.

```properties
wrapperVersion=3.3.4
distributionType=only-script
distributionUrl=https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip

```

## Verify in PowerShell

Run from an ordinary, non-administrator PowerShell window:

```powershell
Set-Location 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService\backend'
.\mvnw.cmd --version
.\mvnw.cmd test
```

The .\ prefix runs the script in the current directory. --version checks Maven and the Java runtime selected by the wrapper. test downloads dependencies as needed, compiles the source and test classes, and executes tests. The first execution needs network access and may take longer.

Actually executed by the assistant: .\mvnw.cmd --version and .\mvnw.cmd -B clean test. The -B flag selects batch output; clean removes only Maven's generated target build directory before rebuilding.

Verified result: Maven 3.10.0 with Java 17.0.16; Spring Boot 3.5.16; tests run 1, failures 0, errors 0, skipped 0; BUILD SUCCESS.

The local build log is backend\initial-build.log, excluded from Git by *.log. Test summaries are in backend\target\surefire-reports.

## Common problems

- mvnw.cmd not found: use Set-Location to enter backend and include the .\ prefix.
- JAVA_HOME errors: inspect $env:JAVA_HOME and java -version; JAVA_HOME must name a JDK directory, not its bin folder.
- Download failures: share the error; check network/proxy settings without disabling certificate validation.
- Unknown parent version: verify the exact version in pom.xml and Maven Central.
- A 404 response from the application root is expected until a controller defines that route. No API has been implemented or HTTP-tested yet.

## References

- Spring Initializr: https://start.spring.io/
- Java 17 compatibility: https://docs.spring.io/spring-boot/3.5/system-requirements.html
- Testing support: https://docs.spring.io/spring-boot/3.5/reference/testing/index.html

Next checkpoint: the learner runs the wrapper and startup test, shares the result, and resolves any errors before the first HTTP endpoint lesson.
