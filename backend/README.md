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
| `V5__constraint_version_audit.sql` | Task 2.6 Engine 2 companion table for constraint version actors/timestamps. |

V1–V4 are immutable. Add new changes only in a new versioned migration. V5 adds only `project_constraint_versions`; existing tables and rows are not altered. It also advances `app_metadata.schema_version` to `5.0.0`.

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
| `POST` | `/api/v1/auth/verify-email` | Public |
| `POST` | `/api/v1/auth/resend-verification` | Public |
| `POST` | `/api/v1/auth/login` | Public |
| `GET` | `/api/v1/auth/me` | Any authenticated user |
| `POST` | `/api/v1/admin/mentors` | `ADMIN` only |
| `GET` | `/api/v1/students/me` | `STUDENT` only |
| `PUT` | `/api/v1/students/me` | `STUDENT` only |
| `GET` | `/api/v1/students/me/skills` | `STUDENT` only |
| `PUT` | `/api/v1/students/me/skills` | `STUDENT` only |
| `GET` | `/api/v1/skills` | Any authenticated user |
| `POST` | `/api/v1/skills` | `MENTOR` or `ADMIN` |
| `POST` | `/api/v1/projects` | `MENTOR` only |
| `GET` | `/api/v1/projects?status=&page=&size=` | `MENTOR` own projects; `STUDENT` visible rostered projects |
| `GET` | `/api/v1/projects/{id}` | Owning `MENTOR` or rostered `STUDENT` |
| `PUT` | `/api/v1/projects/{id}` | Owning `MENTOR` only |
| `POST` | `/api/v1/projects/{id}/publish` | Owning `MENTOR` only |
| `POST` | `/api/v1/projects/{id}/lock-preferences` | Owning `MENTOR` only |
| `POST` | `/api/v1/projects/{id}/roster` | Owning `MENTOR` only, before lock |
| `GET` | `/api/v1/projects/{id}/roster?page=&size=` | Owning `MENTOR` only |
| `DELETE` | `/api/v1/projects/{id}/roster/{studentId}` | Owning `MENTOR` only, before lock |
| `PUT` | `/api/v1/projects/{id}/preferences` | Rostered `STUDENT` only, published and unlocked |
| `GET` | `/api/v1/projects/{id}/preferences/me` | Rostered `STUDENT` only |
| `GET` | `/api/v1/projects/{id}/preferences?page=&size=` | Owning `MENTOR` only |
| `GET` | `/api/v1/projects/{id}/classmates?page=&size=` | Rostered `STUDENT` only, non-draft project |
| `GET` | `/api/v1/projects/{id}/constraints` | Owning `MENTOR` only |
| `PUT` | `/api/v1/projects/{id}/constraints` | Owning `MENTOR` only, before `FORMED` and while no formation job is active |
| `POST` | `/api/v1/projects/{id}/feasibility` | Owning `MENTOR` only |
| `GET` | `/actuator/health` | Public |

Authenticated requests use `Authorization: Bearer <token>`. Access tokens last 60 minutes and there is no refresh or revocation endpoint; the user and role are reloaded from the database on every request.

### Project status and lock rules

* A new project starts as `DRAFT`. Publishing maps `DRAFT` to `PUBLISHED`; publishing an already published project is idempotent, and any later status returns 409.
* Locking sets `preferencesLockedAt`. It maps `DRAFT` or `PUBLISHED` to `PREFERENCES_LOCKED`; a project already in a later state (`FORMED`, etc.) keeps that state and only gains the lock timestamp. Repeated lock requests are idempotent, including concurrent requests serialised with a pessimistic database lock.
* `ProjectService.arePreferencesLocked(project)` is the single lock check used by roster, preference and project-update operations. A project is locked when the timestamp is set or the deadline is at or before the current time.
* After a lock, team-size fields and the deadline cannot change. Title, problem statement and course code remain editable. A deadline that is already in the past is accepted only when it is unchanged; a newly supplied or changed deadline must be in the future.
* `GET /api/v1/projects` is role-dispatched: mentors see their own projects (optional `status` filter), students see non-draft projects they are rostered for, and admins receive 403. Draft or non-rostered single projects return 404 to students.
* Roster changes are rejected with 409 after lock. Removal is also rejected with 409 when the student has preferences, candidate-team membership or finalized-team membership in the project.
* Roster additions are all-or-nothing: unknown, inactive or non-student SRNs return 400 with `invalidSrns` and no row is written. Duplicate SRNs in one request are collapsed, and already-rostered students are reported in `alreadyOnRoster`.
* Preference updates replace the full ranked list. At most two preferences are allowed, both must be rostered, self-preference and duplicates are rejected, and a non-rostered caller or preferred student returns 400. Student preferences are confidential; only the owning mentor can list all preferences.
* Constraint saves are versioned snapshots. `PUT /constraints` replaces the whole typed configuration (`prohibitedPairs` and `mandatorySkills` only) and stores the next version without changing older `project_constraints` rows. `GET /constraints` before the first save returns version `0` with empty lists and no actor.
* Constraints can still change while the project is `DRAFT`, `PUBLISHED` or `PREFERENCES_LOCKED`. A save is rejected with 409 once the project is `FORMED`, `APPROVED` or `COMPLETED`, or while a formation job is `READY` or `RUNNING`.
* `POST /feasibility` is read-only. It plans team sizes greedily, checks that enough eligible students meet each mandatory skill and reports prohibited-pair configuration anomalies. Only a team-size failure or a mandatory-skill shortage makes the report `INFEASIBLE`; prohibited-pair warnings are advisory, and a `FEASIBLE` report explicitly does not guarantee that a valid team arrangement exists.
* A rostered student can page through the other students on a non-draft project through `/classmates`. The projection deliberately exposes only student id, full name and SRN.

### Sample requests

```powershell
# Create a project (mentor token)
curl.exe -X POST http://localhost:8081/api/v1/projects `
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" `
  -d '{"title":"Capstone","problemStatement":"Build a classifier","courseCode":"CS301","minTeamSize":2,"maxTeamSize":4,"teamSizeSlack":1,"preferenceDeadline":"2026-12-01T10:00:00Z"}'

# Publish and lock preferences (mentor token)
curl.exe -X POST http://localhost:8081/api/v1/projects/1/publish -H "Authorization: Bearer $TOKEN"
curl.exe -X POST http://localhost:8081/api/v1/projects/1/lock-preferences -H "Authorization: Bearer $TOKEN"

# Add students to the roster by SRN (mentor token)
curl.exe -X POST http://localhost:8081/api/v1/projects/1/roster `
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" `
  -d '{"srns":["02FE24BCS001","02FE24BCS002"]}'

# Replace my ranked preferences (student token)
curl.exe -X PUT http://localhost:8081/api/v1/projects/1/preferences `
  -H "Authorization: Bearer $STUDENT_TOKEN" -H "Content-Type: application/json" `
  -d '{"preferredStudentIds":[7,9]}'

# Replace the versioned constraint configuration (owning mentor token)
curl.exe -X PUT http://localhost:8081/api/v1/projects/1/constraints `
  -H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json" `
  -d '{"prohibitedPairs":[{"studentId1":7,"studentId2":9}],"mandatorySkills":[{"skillId":1,"minimumProficiency":4}]}'

# Check feasibility without writing anything (owning mentor token)
curl.exe -X POST http://localhost:8081/api/v1/projects/1/feasibility -H "Authorization: Bearer $TOKEN"

# Page through the other rostered students (student token)
curl.exe "http://localhost:8081/api/v1/projects/1/classmates?page=0&size=20" `
  -H "Authorization: Bearer $STUDENT_TOKEN"

# Update my profile and skill set (student token)
curl.exe -X PUT http://localhost:8081/api/v1/students/me `
  -H "Authorization: Bearer $STUDENT_TOKEN" -H "Content-Type: application/json" `
  -d '{"fullName":"Student Name","department":"CSE","semester":5,"interests":"ML, distributed systems"}'
curl.exe -X PUT http://localhost:8081/api/v1/students/me/skills `
  -H "Authorization: Bearer $STUDENT_TOKEN" -H "Content-Type: application/json" `
  -d '{"skills":[{"skillId":1,"proficiency":4},{"skillId":2,"proficiency":5}]}'

# Resend a verification email (public)
curl.exe -X POST http://localhost:8081/api/v1/auth/resend-verification `
  -H "Content-Type: application/json" -d '{"email":"student@college.edu"}'
```

All success responses use `{"success":true,"message":"...","data":...}`. Paged responses include `content`, `page`, `size`, `totalElements`, `totalPages`, `first` and `last`. Validation errors use `validationErrors`; roster all-or-nothing errors also include `invalidSrns`.

## Documentation

* [`../docs/architecture.md`](../docs/architecture.md)
* [`../docs/database-design.md`](../docs/database-design.md)
* [`../docs/decision-log.md`](../docs/decision-log.md)
