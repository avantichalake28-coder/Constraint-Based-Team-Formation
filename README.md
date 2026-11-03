# Skill Match — Student Team Formation

Backend implementation of the Student Team Formation system: Spring Boot 4.1.2, Java 21, MySQL 8.0, Flyway and Testcontainers.

## Quick start

* Backend setup, environment variables, Mailpit and run/test commands: [`backend/README.md`](backend/README.md)
* Architecture: [`docs/architecture.md`](docs/architecture.md)
* Database design: [`docs/database-design.md`](docs/database-design.md)
* Decisions and assumptions: [`docs/decision-log.md`](docs/decision-log.md)

## Current scope

Task 2.4 provides student registration, email verification, login, HMAC-SHA256 JWT authentication, role-based authorisation, admin-created mentor accounts and first-admin seeding. Project, roster, preference, formation, validation and finalization features are not implemented yet.

## Verify

From `backend/`:

```powershell
.\mvnw.cmd clean verify
```

The suite requires Docker and runs against a real MySQL 8.0 container. It contains no skipped tests.
