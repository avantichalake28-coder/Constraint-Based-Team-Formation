# Architecture

## Overview

Student Team Formation is a Spring Boot 4.1.2 application running on Java 21 with a MySQL 8.0 database. The backend is organised around the four engines described in the project material:

| Engine | Responsibility | Package |
| --- | --- | --- |
| E1 | Identity, profiles, skills, projects and rosters | `com.teamformation.backend.entity` (persistence only) |
| E2 | Constraints and feasibility rules | `com.teamformation.backend.engine.constraint` |
| E3 | Team formation runs and candidate teams | `com.teamformation.backend.engine.formation` |
| E4 | Validation findings, reports and mentor approval | `com.teamformation.backend.engine.validation` |

Task 2.2b changes persistence and tests only. Task 2.4 adds authentication, authorisation and the account-administration endpoints described below. It deliberately does not add project, roster, preference, formation, validation or finalization services/controllers; those engines remain unimplemented.

## Persistence layout

* `src/main/resources/db/migration/V1__init.sql` creates the Flyway baseline and `app_metadata`.
* `src/main/resources/db/migration/V2__domain_foundation.sql` creates the original domain tables and JPA mappings.
* `src/main/resources/db/migration/V3__schema_corrections.sql` contains every Task 2.2b schema correction.
* `src/main/resources/db/migration/V4__auth_and_formation_job_integrity.sql` contains the Task 2.4 changes: `candidate_teams.formation_job_id` becomes `NOT NULL`, its foreign key moves from `ON DELETE SET NULL` to `ON DELETE CASCADE`, the hash-only `email_verification_tokens` table is added, and `app_metadata.schema_version` is advanced to `4.0.0`.
* V1–V3 are immutable. All schema corrections live in later versioned migrations.
* Hibernate runs with `ddl-auto: validate`; Flyway owns all DDL.
* MySQL credentials are read from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`. No credentials are hardcoded in source or test configuration.

The V3 migration was applied only after checking that every table it changes was empty. In the development database only V1 was applied, so all V2 tables were absent and therefore empty. The empty-seed test database is created by Testcontainers for every verification run. Before V4, `candidate_teams` contained zero rows and `email_verification_tokens` did not exist; no existing data was deleted or modified.

## Domain model

The persistence model is deliberately split between candidate and finalized teams:

* **Candidate team** (`candidate_teams`, `candidate_team_members`) is the output of a formation run. A student may appear in at most one candidate team per formation job.
* **Finalized team** (`finalized_teams`, `finalized_team_memberships`) is the mentor-approved result. A student may appear in only one finalized team per project.

Both membership tables keep the source (`ALGORITHM` or `MANUAL_OVERRIDE`) so later engine work can distinguish generated and overridden assignments.

`project_roster` is the single source of project eligibility. Teammate preferences and finalized memberships use composite foreign keys back to `project_roster(project_id, student_id)`, so a student who is not on the roster cannot be preferred or finalized.

## Authentication and authorisation

Task 2.4 adds a stateless security layer under `com.teamformation.backend.security`:

| Package | Contents |
| --- | --- |
| `security.config` | `SecurityConfig` (filter chain, public routes, OAuth2 resource server, method security, BCrypt) and `JwtConfig` (HMAC-SHA256 encoder/decoder built from `JWT_SECRET`). |
| `security.authentication` | `JwtService` (token issue), `DatabaseJwtAuthenticationConverter` (per-request user lookup), `CurrentUserService`, and the JSON `RestAuthenticationEntryPoint` / `RestAccessDeniedHandler`. |
| `security.service` | Registration, email verification, login, failed-login throttling, mentor creation, admin seeding, email-domain policy, token generation/hashing and mail delivery. |
| `security.dto` | Request/response records for registration, verification, login, `/me` and mentor creation. |

The chain is stateless, disables CSRF (no cookie-based sessions are used), keeps the existing permissive CORS policy, and delegates 401/403 responses to the same `ErrorResponse` envelope used by `GlobalExceptionHandler`.

| Route | Access |
| --- | --- |
| `POST /api/v1/auth/register` | Public |
| `POST /api/v1/auth/login` | Public |
| `GET /api/v1/auth/verify-email?token=...` | Public |
| `GET /actuator/health` | Public |
| `GET /api/v1/auth/me` | Any authenticated user |
| `POST /api/v1/admin/mentors` | Authenticated plus `@PreAuthorize("hasRole('ADMIN')")` |
| Everything else | Authenticated |

JWTs are HMAC-SHA256, signed with a `JWT_SECRET` of at least 32 bytes (startup fails otherwise). They last 60 minutes, carry `sub` = user id and a `role` claim, and there is no refresh token or revocation list. On every request `DatabaseJwtAuthenticationConverter` reloads the user by id and rejects missing or inactive accounts, so deactivation and role changes take effect immediately even while an older token is still within its lifetime. Authentication therefore never trusts the role claim alone.

Registration creates an inactive `STUDENT` user and its `StudentProfile` in one transaction. The 256-bit verification token is emailed as a link; only its SHA-256 hash is stored in `email_verification_tokens` with a 24-hour expiry. Verification activates the account and marks the token used. Login returns the same generic 401 for an unknown email or a wrong password; the explicit "verify your email" message appears only after the password was verified. Five failed attempts for one email within fifteen minutes return 429; the counter is held in memory per instance.

`AdminSeedRunner` creates the first `ADMIN` on startup only when no admin exists and both `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set. Otherwise it logs a warning and the application still starts. Mentor accounts are created by an admin and are active immediately without an SRN.

## Mail and external configuration

Verification mail uses Spring's `JavaMailSender` with `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` and `APP_BASE_URL`. The development default is Mailpit on `localhost:1025` (SMTP) with the web inbox on port 8025. Test runs replace `JavaMailSender` with a mock and read the link from the captured message; no SMTP server is required for `clean verify`.

Secrets and deployment-specific values are read only from the environment: `DB_*`, `ALLOWED_EMAIL_DOMAIN`, `JWT_SECRET`, `MAIL_*`, `APP_BASE_URL` and optionally `ADMIN_EMAIL` / `ADMIN_PASSWORD`. Passwords, password hashes and raw verification tokens are never logged or returned by a controller.

## Validation strategy

* Schema constraints are enforced by MySQL, not only by Hibernate or Bean Validation.
* Bean Validation mirrors the SRN rule on `StudentProfile` and normalises the value to uppercase before persistence.
* Integration tests use Testcontainers with the pinned image `mysql:8.0`; H2 is not used anywhere.
* A missing Docker daemon is a build failure. There is no `@EnabledIf`, assumption or skip path around the database tests.
* Constraint tests insert invalid rows and assert the database rejects them, including CHECK, UNIQUE and foreign-key violations.
* A live-schema test queries `information_schema.columns` to ensure no phone, mobile, contact or MSISDN column exists.
* `AuthIntegrationTest` exercises registration, verification, login, JWT validation, role checks, admin mentor creation, throttling and secret hygiene through MockMvc against a real MySQL 8.0 container.
* Boot 4's Jackson 3 mapper is configured with `spring.jackson.deserialization.fail-on-unknown-properties: true`, and a test proves that `phone`/`role` fields on registration are rejected and persist nothing.
* The SQL migration sequence and Hibernate validation are checked by `BackendApplicationTests`; the suite asserts that four migrations (V1–V4) applied successfully.

## Configuration notes

`application.yml` no longer declares an explicit Hibernate dialect; Hibernate detects the MySQL dialect from the JDBC connection. Java 21 is configured in the Maven POM and is the version used by the build.

The test profile supplies fixed, non-production auth values and disables the mail health indicator, because the mocked `JavaMailSender` cannot satisfy Boot's mail health contributor. The mail indicator remains enabled in normal runtime.
