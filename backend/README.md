# Student Team Formation — Backend

Spring Boot 4.1.2 application on Java 21 with MySQL 8.0, Flyway and Testcontainers.

## Prerequisites

* Java 21
* Docker (required for integration tests; a missing daemon fails the build)
* MySQL 8.0 for local runs
* Mailpit for local verification email (see below)

## Configuration

All deployment-specific values are supplied through environment variables. Copy `.env.example` and inject the values into your shell or IDE run configuration. The application does not read `.env` automatically.

| Variable | Required | Default | Purpose |
| --- | --- | --- | --- |
| `DB_HOST` | no | `localhost` | MySQL host. |
| `DB_PORT` | no | `3306` | MySQL port. |
| `DB_NAME` | no | `student_team_formation` | Database name. |
| `DB_USER` | yes | — | MySQL user. |
| `DB_PASSWORD` | yes | — | MySQL password. |
| `ALLOWED_EMAIL_DOMAIN` | yes | — | The only domain accepted for student registration, e.g. `college.edu`. Startup fails if blank. |
| `JWT_SECRET` | yes | — | HMAC-SHA256 signing secret, at least 32 bytes. Startup fails if missing or too short. |
| `MAIL_HOST` | no | `localhost` | SMTP host. |
| `MAIL_PORT` | no | `1025` | SMTP port. |
| `MAIL_FROM` | yes | — | Sender address used in verification mail. |
| `MAIL_USERNAME` / `MAIL_PASSWORD` | no | empty | SMTP credentials when the server requires authentication. |
| `MAIL_SMTP_AUTH` | no | `false` | Enable SMTP authentication. |
| `MAIL_SMTP_STARTTLS` | no | `false` | Enable STARTTLS. |
| `APP_BASE_URL` | yes | — | Public base URL used to build verification links, e.g. `http://localhost:8081`. |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | no | empty | Bootstrap admin. Created only when no ADMIN exists and both are set. |

Never commit real secrets. Passwords, password hashes and raw verification tokens are never logged or returned in API responses.

## Mailpit for development

Verification emails are sent over SMTP. Pin the current Mailpit release and expose both the SMTP port and the web inbox:

```bash
docker run --name mailpit -d \
  -p 1025:1025 \
  -p 8025:8025 \
  axllent/mailpit:v1.33.0
```

* SMTP: `localhost:1025`
* Web inbox: <http://localhost:8025>

Set `MAIL_HOST=localhost`, `MAIL_PORT=1025` and `MAIL_FROM=no-reply@college.edu` (or your chosen sender). Verification links use `APP_BASE_URL`; open the link from the Mailpit inbox to activate the account.

## Database

Flyway owns the schema; Hibernate runs with `ddl-auto: validate`.

| Migration | Purpose |
| --- | --- |
| `V1__init.sql` | Baseline and `app_metadata`. |
| `V2__domain_foundation.sql` | Original domain tables. |
| `V3__schema_corrections.sql` | Task 2.2b corrections. |
| `V4__auth_and_formation_job_integrity.sql` | Task 2.4 auth table and `candidate_teams.formation_job_id NOT NULL`. |

V1–V3 are immutable. Add new changes only in a new versioned migration.

## Run

```bash
./mvnw spring-boot:run
```

Windows:

```powershell
.\mvnw.cmd spring-boot:run
```

The API listens on `http://localhost:8081`.

## Test

```bash
./mvnw clean verify
```

Windows:

```powershell
.\mvnw.cmd clean verify
```

Integration tests start a real `mysql:8.0` container through Testcontainers. There are no skipped tests and no H2 fallback. The suite mocks `JavaMailSender`, so no SMTP server is needed for the build.

## API summary

| Method | Path | Access |
| --- | --- | --- |
| `POST` | `/api/v1/auth/register` | Public |
| `GET` | `/api/v1/auth/verify-email?token=...` | Public |
| `POST` | `/api/v1/auth/login` | Public |
| `GET` | `/api/v1/auth/me` | Any authenticated user |
| `POST` | `/api/v1/admin/mentors` | `ADMIN` only |
| `GET` | `/actuator/health` | Public |

Authenticated requests use `Authorization: Bearer <token>`. Access tokens last 60 minutes and there is no refresh or revocation endpoint; the user and role are reloaded from the database on every request.

## Documentation

* [`../docs/architecture.md`](../docs/architecture.md)
* [`../docs/database-design.md`](../docs/database-design.md)
* [`../docs/decision-log.md`](../docs/decision-log.md)
