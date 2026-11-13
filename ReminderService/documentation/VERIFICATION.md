# Executed verification — 13 November 2026

These results come from commands and browser actions actually executed in this workspace. Test-only accounts and local capture SMTP were used; no external recipient delivery is claimed.

| Layer | Executed check | Result |
| --- | --- | --- |
| Backend | Maven Wrapper verify, Java 17 / Spring Boot 3.5.16 | 27 tests passed; executable JAR built |
| Frontend | npm test, build, audit | 3 tests passed; production bundle built; 0 reported vulnerabilities |
| Containers | Compose build and startup | MySQL, backend, frontend, Mailpit running; database and API healthy |
| REST contract | Newman with supplied Postman collection | 15 requests, 16 assertions, 0 failures |
| Live MySQL | Auth/profile, ownership, CRUD, version conflicts, dashboard/history, logout | 37 HTTP checks passed |
| Scheduler | Future reminder, no frontend involvement | SENT, one attempt, SMTP_ACCEPTED log |
| Retry limit | Real SMTP connection refusal, maxRetries=1 | Two failed logs, retryCount=1, final FAILED |
| Restart | Backend container restarted with existing reminder/JWT | Both persisted; fixture cleaned up |
| Concurrency | Four simultaneous H2 transaction workers | One claim winner; replayed finalization ignored |
| Uncertain outcomes | H2 expired lease and stale worker | UNKNOWN log, final FAILED, stale result fenced |
| Template | Mockito sender and real Spring Thymeleaf engine | Plain/HTML content, escaped text, error classifications tested |
| Time zones | Temporal tests | UTC conversion and DST gap/fold rejection passed |
| Browser | Local demo and deployed Docker application | Registration/login, reminders, settings, dashboard/history, desktop/mobile navigation checked |

## Evidence

- [Live MySQL API checks](verification/live-compose.json)
- [Local demo API checks](verification/live-demo.json)
- [Sanitized Postman results](verification/postman-summary.json)
- [Restart persistence](verification/restart.json)
- [SMTP failure and retry limit](verification/smtp-failure.json)
- Screenshots in `documentation/screenshots/`.

The retry experiment temporarily recreated the backend with a closed SMTP port and short retry intervals. The script restored normal Compose settings and waited for health afterwards. The deployed application uses Mailpit capture SMTP.

Raw Postman reports containing test tokens were sanitized and removed. Generated secrets, local database files, captured messages, dependency folders, build outputs, and raw private reports are ignored by Git.

## Environment-specific work

- Gmail was subsequently configured privately by the user through tools/configure-gmail.ps1. The running backend was verified to use smtp.gmail.com:587 with authentication and STARTTLS. Real test reminder 9 completed as SENT on attempt 1, with an SMTP_ACCEPTED log and no failure reason. Inbox placement has not been independently confirmed. Fresh installations still default to local Mailpit capture.
- Public hosting requires a server, domain/DNS, firewall and HTTPS. Compose/Caddy instructions are supplied; no public deployment was performed.
- GitHub Actions is supplied but has not run on GitHub. No remote repository was created or pushed.
- Existing Windows MySQL account recovery remains deferred. Independently provisioned Docker MySQL was used for live persistence checks.
- Automated transaction/concurrency tests use H2; live workflow, retry and restart checks also used MySQL. This is not a distributed load test or an exactly-once SMTP guarantee.
- Backup commands are documented; a restore drill has not been executed.

See [the complete guide](COMPLETE_GUIDE.md) for verification commands and troubleshooting.
