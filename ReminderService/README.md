# Smart Reminder and Email Notification Service

Vue 3, Spring Boot 3.5 / Java 17, MySQL, JWT authentication, and scheduled SMTP notifications. The complete local Docker deployment has been built and tested on Windows.

## Run on Windows

Start Docker Desktop with its Linux engine. Open PowerShell:

```powershell
Set-Location 'C:\Users\avina\OneDrive\Documents\ChatGPT\study\ReminderService'
if (-not (Test-Path -LiteralPath '.env')) {
    powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\tools\init-env.ps1
}
docker compose up --build -d
docker compose ps
```

Open [the application](http://localhost:8088) and register your own account. Open [Mailpit](http://localhost:8025) to inspect captured test messages. Default SMTP captures mail locally; it does not send to external inboxes. Generated credentials live in the ignored `.env` file. This separate MySQL container does not require your Windows MySQL root password.

## Features

- Registration, BCrypt passwords, short-lived JWTs, protected routes, and server-side logout revocation.
- Owner-scoped reminder CRUD, cancellation, optimistic version checks, terminal soft deletion, and retained history.
- UTC persistence, IANA time zones, and explicit handling of ambiguous daylight-saving times.
- Database scheduler independent of the browser, atomic claims, claim fencing, and transactional attempt logs.
- Plain-text and escaped Thymeleaf HTML email, bounded exponential retries, and conservative handling of uncertain SMTP outcomes.
- Dashboard, paginated history, and eight responsive Vue pages with loading and error states.
- Docker Compose, persistent MySQL storage, capture SMTP, optional Caddy HTTPS deployment, Postman collection, and GitHub Actions configuration.

`SENT` means SMTP submission was accepted, not proven inbox delivery. Expired claims and ambiguous SMTP outcomes are recorded as `UNKNOWN` and require review rather than automatic resending. Exactly-once delivery is not promised.

## Executed verification

| Check | Result |
| --- | --- |
| Maven verify: JUnit 5, Mockito, persistence, HTTP and concurrency tests | 27 passed |
| Vue time-zone tests | 3 passed |
| Vue production build and npm audit | Passed; 0 reported vulnerabilities |
| Postman collection through Newman | 15 requests, 16 assertions, 0 failures |
| Live Docker / MySQL workflow | 37 HTTP checks; scheduled SMTP acceptance recorded |
| Live backend restart | Reminder and JWT persisted |
| Live SMTP failure / retry limit | 2 failed attempts, final FAILED; configuration restored |
| Browser | Registration, login, reminder forms, settings, dashboard, history, responsive navigation |

See [verification details](documentation/VERIFICATION.md) and sanitized evidence under `documentation/verification`. The running local installation was subsequently configured for Gmail: reminder 9 was accepted by Gmail SMTP on its first attempt. Recipient inbox arrival has not been independently confirmed. Fresh checkouts still default to Mailpit. Public DNS/TLS deployment and GitHub Actions execution have not been tested.

## Learn, test, and deploy

Read the [complete beginner guide](documentation/COMPLETE_GUIDE.md) for architecture, file responsibilities, annotations, API examples, Postman steps, SMTP setup, transaction boundaries, outbox tradeoffs, Docker commands, troubleshooting, and public deployment instructions.

Import [the Postman collection](documentation/postman/SmartReminder.postman_collection.json) and run it against a local test instance. The collection creates test data. Backend and frontend checks:

```powershell
Set-Location .\backend
.\mvnw.cmd -B verify
Set-Location ..\frontend
npm.cmd ci
npm.cmd test
npm.cmd run build
npm.cmd audit
```

## Project layout

```text
ReminderService/
  backend/           Java API, security, entities, workers, templates, tests
  frontend/          Vue pages, components, router, Axios, Pinia
  database/          MySQL schema, constraints, earlier setup exercises
  docker/            nginx, least-privilege database grants, Caddy HTTPS
  tools/             Secret initialization and local verification utilities
  documentation/     Beginner lessons, complete guide, Postman, evidence
  .github/workflows/ Continuous integration definition
  docker-compose.yml
  .env.example       Configuration names only; no real secrets
```

Earlier numbered lessons preserve the learning sequence and historical checkpoints. This README and the complete guide describe the finished implementation.
