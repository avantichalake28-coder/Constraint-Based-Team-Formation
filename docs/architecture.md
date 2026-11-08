# Architecture

## Overview

Student Team Formation is a Spring Boot 4.1.2 application running on Java 21 with a MySQL 8.0 database. The backend is organised around the four engines described in the project material:

| Engine | Responsibility | Package |
| --- | --- | --- |
| E1 | Identity, profiles, skills, projects and rosters | `com.teamformation.backend.entity` (persistence only) |
| E2 | Constraints and feasibility rules | `com.teamformation.backend.engine.constraint` |
| E3 | Team formation runs and candidate teams | `com.teamformation.backend.engine.formation` |
| E4 | Validation findings, reports and mentor approval | `com.teamformation.backend.engine.validation` |

Task 2.2b changes persistence and tests only. Task 2.4 adds authentication, authorisation and account administration. Task 2.5 Part A hardens verification mail and startup/health behaviour, and Engine 1 adds student profiles/skills, mentor-owned projects, rosters, ranked preferences and preference locking. Task 2.6 Engine 2 adds versioned project constraints, a pure team-size planner, a read-only feasibility report and the student classmates projection. Constraint scoring, team formation, validation and finalization remain unimplemented.

## Persistence layout

* `src/main/resources/db/migration/V1__init.sql` creates the Flyway baseline and `app_metadata`.
* `src/main/resources/db/migration/V2__domain_foundation.sql` creates the original domain tables and JPA mappings.
* `src/main/resources/db/migration/V3__schema_corrections.sql` contains every Task 2.2b schema correction.
* `src/main/resources/db/migration/V4__auth_and_formation_job_integrity.sql` contains the Task 2.4 changes: `candidate_teams.formation_job_id` becomes `NOT NULL`, its foreign key moves from `ON DELETE SET NULL` to `ON DELETE CASCADE`, the hash-only `email_verification_tokens` table is added, and `app_metadata.schema_version` is advanced to `4.0.0`.
* `src/main/resources/db/migration/V5__constraint_version_audit.sql` adds `project_constraint_versions`, a companion audit table for Task 2.6 constraint versions, and advances `app_metadata.schema_version` to `5.0.0`. It only creates a new table and updates that metadata row; no existing domain row is changed or deleted.
* V1–V4 are immutable. All schema corrections live in later versioned migrations. Task 2.5 and Engine 1 added no migration; Task 2.6 adds only V5.
* Hibernate runs with `ddl-auto: validate`; Flyway owns all DDL.
* MySQL credentials are read from `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER` and `DB_PASSWORD`. No credentials are hardcoded in source or test configuration.

The V3 migration was applied only after checking that every table it changes was empty. In the development database only V1 was applied, so all V2 tables were absent and therefore empty. The empty-seed test database is created by Testcontainers for every verification run. Before V4, `candidate_teams` contained zero rows and `email_verification_tokens` did not exist; no existing data was deleted or modified. V5 only creates `project_constraint_versions` and updates the `schema_version` metadata value, so it also deletes or rewrites no domain data.

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
| `POST /api/v1/auth/register`, `/login`, `/verify-email`, `/resend-verification` | Public |
| `GET /api/v1/auth/verify-email?token=...` | Public |
| `GET /actuator/health` | Public |
| `GET /api/v1/auth/me` | Any authenticated user |
| `GET /api/v1/metadata` | Any authenticated user |
| `POST /api/v1/admin/mentors` | Authenticated plus `@PreAuthorize("hasRole('ADMIN')")` |
| `GET /api/v1/students/me`, `/students/me/skills` | `STUDENT` only |
| `PUT /api/v1/students/me`, `/students/me/skills` | `STUDENT` only |
| `GET /api/v1/skills` | Any authenticated user |
| `POST /api/v1/skills` | `MENTOR` or `ADMIN` |
| `POST/PUT /api/v1/projects`, `POST .../publish`, `.../lock-preferences` | Owning `MENTOR` only |
| `GET /api/v1/projects`, `/projects/{id}` | Role-dispatched; see Engine 1 below |
| `POST/GET/DELETE /api/v1/projects/{id}/roster` | Owning `MENTOR` only |
| `PUT /api/v1/projects/{id}/preferences`, `GET .../preferences/me` | Rostered `STUDENT` only |
| `GET /api/v1/projects/{id}/preferences` | Owning `MENTOR` only |
| `GET /api/v1/projects/{id}/classmates` | Rostered `STUDENT` only |
| `GET/PUT /api/v1/projects/{id}/constraints` | Owning `MENTOR` only |
| `POST /api/v1/projects/{id}/feasibility` | Owning `MENTOR` only |
| Everything else | Authenticated |

JWTs are HMAC-SHA256, signed with a `JWT_SECRET` of at least 32 bytes (startup fails otherwise). They last 60 minutes, carry `sub` = user id and a `role` claim, and there is no refresh token or revocation list. On every request `DatabaseJwtAuthenticationConverter` reloads the user by id and rejects missing or inactive accounts, so deactivation and role changes take effect immediately even while an older token is still within its lifetime. Authentication therefore never trusts the role claim alone.

Registration creates an inactive `STUDENT` user and its `StudentProfile` in one transaction. The 256-bit verification token is emailed as a link; only its SHA-256 hash is stored in `email_verification_tokens` with a 24-hour expiry. Verification activates the account and marks the token used. Login returns the same generic 401 for an unknown email or a wrong password; the explicit "verify your email" message appears only after the password was verified. Five failed attempts for one email within fifteen minutes return 429; the counter is held in memory per instance.

`AdminSeedRunner` creates the first `ADMIN` on startup only when no admin exists and both `ADMIN_EMAIL` and `ADMIN_PASSWORD` are set. Otherwise it logs a warning and the application still starts. Mentor accounts are created by an admin and are active immediately without an SRN.

## Engine 1: profiles, projects, roster and preferences

Engine 1 follows the same controller → service → repository layering. Controllers only translate HTTP to service calls; authorisation is checked in services from the database-loaded authenticated user; write services are `@Transactional`. Responses are DTOs wrapped in `ApiResponse`, and failures flow through `GlobalExceptionHandler`.

| Service | Responsibility |
| --- | --- |
| `StudentProfileService`, `StudentSkillService` | A student reads/updates only their own profile and replaces their whole skill set atomically. SRN and email are immutable; a request containing either is rejected as an unknown field. |
| `SkillService` | Reads the shared catalogue for any authenticated user. Mentors and admins may add skills; names are trimmed and unique ignoring case. |
| `ProjectService` | Mentor-owned project create/update/publish/lock, role-dispatched listing and single-project visibility, and the shared `arePreferencesLocked` check. |
| `RosterService` | Mentor-owned all-or-nothing roster additions by SRN, paged roster listing and guarded removal. |
| `PreferenceService` | Student-owned replacement of ranked preferences, own-preference read and mentor review of all preferences. |

Project lifecycle and lock rules:

* A new project is `DRAFT`. Publishing is owner-only, idempotent for an already `PUBLISHED` project and rejected with 409 for any later status.
* Locking is owner-only and idempotent. `ProjectService.lockPreferences` loads the project with a pessimistic write lock, so concurrent requests observe one timestamp. `DRAFT`/`PUBLISHED` become `PREFERENCES_LOCKED`; a later status such as `FORMED` is retained.
* `arePreferencesLocked` is the only lock check used by project update, roster and preference operations. A project is locked when `preferencesLockedAt` is set or the deadline is not after the current time.
* After locking, team-size fields and the deadline are immutable. Title, problem statement and course code remain editable. An unchanged past deadline is accepted when only non-locked fields change; a new or changed deadline must be in the future.
* The schema stores timestamps at second precision, so deadline comparison tolerates a one-second difference caused by MySQL rounding of sub-second request values.

Visibility:

* A mentor sees and reads only their own projects.
* A student sees non-draft projects they are rostered for. A draft or non-rostered project returns 404 for both listing and single reads. Rostered students can still read a project after it is locked.
* `GET /api/v1/projects` accepts `status` only for mentors; students ignore it, and admins receive 403.

Roster and preferences:

* Roster additions normalise SRNs, reject the whole request with 400 and an `invalidSrns` list when any SRN is unknown, inactive or not a student, collapse duplicate SRNs, and report already-rostered students in `alreadyOnRoster`.
* Roster changes are rejected with 409 after lock. Removal is also rejected with 409 when the student has preferences, candidate-team membership or finalized-team membership in the project.
* Preference updates require a published, unlocked project and a rostered caller. The ordered list replaces the prior list, allows at most two entries, rejects nulls/duplicates/self-preference and requires every preferred student to be rostered. Violations return 400; a lock returns 409.
* A student can read only their own preferences and only for a non-draft project they are on the roster for. The owning mentor can page through every preference in their project. No other role can read preference data.

## Engine 2: versioned constraints and feasibility

Engine 2 follows the same controller → service → repository layering. `ProjectConstraintController` delegates to `ConstraintConfigurationService` and `FeasibilityService`; both load the authenticated user from the database and grant access only to the owning mentor of the project. Controllers return DTOs wrapped in `ApiResponse`, and failures use `GlobalExceptionHandler`.

Constraint configuration:

* A `PUT` replaces the whole configuration as one new version. The request holds only `prohibitedPairs` (`studentId1`, `studentId2`) and `mandatorySkills` (`skillId`, optional `minimumProficiency` defaulting to 3).
* Rows are stored in the existing `project_constraints` table, keyed by `version`; `project_constraint_versions` records the actor, timestamp and authoritative current version. An empty configuration still creates a version row, so history and actor cannot be lost.
* `GET /constraints` before the first save reports version `0` with empty typed lists and no actor.
* Version allocation is serialised with a pessimistic lock on the project row and a locking read of the version table, so two concurrent saves receive distinct consecutive versions.
* Validation rejects duplicate prohibited pairs in either order, students outside the roster, self-pairs, duplicate mandatory skills, unknown skill ids, proficiency outside 1-5 and unknown JSON fields. A rejected request writes no version and no constraint row.
* Constraints remain mutable while the project is `DRAFT`, `PUBLISHED` or `PREFERENCES_LOCKED`. A save returns 409 once the project is `FORMED`, `APPROVED` or `COMPLETED`, or while a formation job is `READY` or `RUNNING`. Locking preferences alone is not a constraint freeze.

Feasibility:

* `TeamSizePlanner` is a pure function over population, `minTeamSize`, `maxTeamSize` and `teamSizeSlack`. It forms as many full teams of `max` as possible, then keeps a remainder team when it is at least `min`, absorbs the remainder into one full team when slack permits, or reports infeasibility with deterministic suggestions that are never applied. Inputs outside the schema ranges throw `IllegalArgumentException`.
* `FeasibilityChecker` is a pure function over the plan, the typed configuration, the eligible roster and skill names. It reports a `CRITICAL` `TEAM_SIZE_INFEASIBLE` finding when the planner fails and a `CRITICAL` `MANDATORY_SKILL_SHORTAGE` finding when fewer eligible students meet a mandatory skill than there are planned teams.
* A prohibited pair that references a non-rostered student, or that cannot be separated because fewer than two teams are possible, produces a `WARNING` finding. Warnings are advisory and do not change the overall status. `FEASIBLE` means only that these checks did not prove infeasibility; every report carries that disclaimer.
* `POST /feasibility` is read-only. It counts only roster entries whose eligibility is `ELIGIBLE`, uses the current constraint version, writes no version or constraint row, and returns the plan, mandatory-skill coverage, findings and disclaimer.

## Engine 3: formation jobs and candidate teams

Engine 3 is a synchronous formation run over the frozen Engine 1/2 inputs. It reuses the existing `projects`, `project_roster`, `teammate_preferences`, `project_constraints`, `project_constraint_versions`, `formation_jobs`, `candidate_teams` and `candidate_team_members` tables; no V6 migration is added.

API surface (all endpoints require the owning mentor and return `ApiResponse`):

* `POST /api/v1/projects/{projectId}/formation-jobs` starts a run and returns the completed job with its candidate teams.
* `GET /api/v1/projects/{projectId}/formation-jobs/{jobId}` returns one completed or failed job and its persisted candidate teams.
* `GET /api/v1/projects/{projectId}/formation-jobs?status=...&page=...&size=...` pages job history, optionally filtered by status.

Lifecycle and transactions:

* `FormationService` is the only orchestrator. It does not run inside a controller-level transaction and uses three explicit `TransactionTemplate` phases.
* **T1** locks the project row, checks ownership, project status, `ProjectService.arePreferencesLocked`, the absence of another `READY`/`RUNNING` job, and the current constraint rows, then loads the eligible roster, skills, preferences, team-size settings and the current constraint version. A failure here writes no job. A success writes a `READY` job with the seed, algorithm version and constraint version.
* **T2** locks the job row, requires `READY`, records `RUNNING`, re-checks that the current constraint version equals the job's recorded version, executes the pure engine, validates the plan, persists candidate teams and `ALGORITHM` members, flushes, and verifies the persisted shape before marking the job `COMPLETED`.
* **T3** runs in a new transaction after a T2 failure, locks the job and moves `READY`/`RUNNING` to `FAILED` with a safe reason truncated to 2000 characters. T2 candidate rows are rolled back, so a failed job has no teams or members.
* Reruns are allowed after a `COMPLETED` job. A `READY` or `RUNNING` job rejects a second run with 409. Engine 3 never changes the project status and never writes `FORMED`.

Supported configuration:

* Only `HARD` rules of type `PROHIBITED_PAIR` and `MANDATORY_SKILL` are executable. The loader inspects the current version's rows directly (rather than relying on the Engine 2 loader, which intentionally ignores unknown rule types) and rejects any soft row or unsupported rule type with 409 before a job is created.
* Mandatory skills are enforced per team at the configured minimum proficiency. The project status must be `PUBLISHED` or `PREFERENCES_LOCKED`, and preferences must effectively be locked.

Pure formation engine:

* `FormationInput` contains plain Java values only: eligible students, skill maps, directed preferences, prohibited pairs, mandatory skills and the Engine 2 `TeamSizePlan`. It has no JPA, repository, HTTP or transaction dependency.
* `FormationEngine` is deterministic. Students are ordered by seed-derived rank then id, teams are filled greedily, empty-team symmetry is skipped, mandatory-skill coverage is pruned, and bounded backtracking repairs failures. A bounded best-improvement pairwise-swap local search then runs over the accepted plan.
* The node budget is one placement attempt after capacity and symmetry filtering. `formation.search.max-nodes` defaults to 250000 and `formation.search.max-improvement-passes` defaults to 100. Both are validated at startup and a non-positive value fails the application.
* The objective is lexicographic: maximise total preference score, minimise students with preferences but none satisfied, then minimise the seed-derived plan tie-break. A student without preferences is excluded from the unsatisfied count. Rank 1 is 1.0 and rank 2 is 0.5; preferences are directed, mutual pairs count twice, and no normalisation or `ProjectConstraint.weight` is used.
* `FormationPlanValidator` runs after the engine and before persistence. It rejects an invalid seed/algorithm version, wrong team count or sizes, missing/duplicate/ineligible students, mismatched scores, non-canonical team ordering or names, prohibited pairs in one team, missing mandatory-skill coverage, incorrect metrics or a mismatched tie-break.
* Team names are `Team 01`, `Team 02`, ... with a zero-padded width of `max(2, digits(teamCount))`. Teams are ordered by descending planned size and then by the smallest member student id, which makes names unique within a job.
* The engine's result is a bounded heuristic and is never presented as the global optimum. If the node budget is exhausted before a valid plan is found, the run fails without persisting a partial plan.

Frozen-reference limitation:

* A job records the constraint version, seed and algorithm version but not roster, preference, team-size or skill snapshots. Re-running a job uses the inputs as they exist at the new run. Historical results remain readable because candidate teams and members are persisted, but an old job cannot be replayed byte-for-byte if those inputs changed. This is a deliberate V2.9 scope limit; snapshot tables and replay are deferred.

## Mail and external configuration

Verification mail uses Spring's `JavaMailSender` with `MAIL_HOST`, `MAIL_PORT`, `MAIL_FROM` and `APP_BASE_URL`. The development default is Mailpit on `localhost:1025` (SMTP) with the web inbox on port 8025. Test runs replace `JavaMailSender` with a mock where mail content matters; no SMTP server is required for `clean verify`.

Registration and resend register an after-commit callback, so mail is sent only when the surrounding transaction commits. A rolled-back registration writes no user, profile, token or message. An SMTP failure after a successful commit is logged without the raw token and does not roll back the account; the student can recover through `POST /api/v1/auth/resend-verification`. Resend returns the same generic message for unknown, verified and unverified addresses, and is limited to three requests per email per hour in memory. Resend marks previous unused tokens used and issues one new 24-hour token.

The mail health indicator is disabled in all profiles because mail is not required for the API to serve requests: an unreachable SMTP server must not make `/actuator/health` report the application as down. The database health component remains enabled. This is a deliberate availability choice; mail failures still appear in application logs, and resend is the recovery path.

Secrets and deployment-specific values are read only from the environment: `DB_*`, `ALLOWED_EMAIL_DOMAIN`, `JWT_SECRET`, `MAIL_*`, `APP_BASE_URL` and optionally `ADMIN_EMAIL` / `ADMIN_PASSWORD`. Passwords, password hashes and raw verification tokens are never logged or returned by a controller.

## Validation strategy

* Schema constraints are enforced by MySQL, not only by Hibernate or Bean Validation.
* Bean Validation mirrors the SRN rule on `StudentProfile` and normalises the value to uppercase before persistence.
* Integration tests use Testcontainers with the pinned image `mysql:8.0`; H2 is not used anywhere.
* A missing Docker daemon is a build failure. There is no `@EnabledIf`, assumption or skip path around the database tests.
* Constraint tests insert invalid rows and assert the database rejects them, including CHECK, UNIQUE and foreign-key violations.
* A live-schema test queries `information_schema.columns` to ensure no phone, mobile, contact or MSISDN column exists.
* `AuthIntegrationTest` exercises registration, verification, login, JWT validation, role checks, admin mentor creation, throttling and secret hygiene through MockMvc against a real MySQL 8.0 container.
* `PartAFixesIntegrationTest` covers resend token replacement, generic responses, hourly throttling, the POST verification form, after-commit mail failure semantics and formation-job/project delete cascades.
* `RegistrationRollbackIntegrationTest` runs registration inside a rolled-back transaction and proves no user, profile, token or mail is persisted.
* `Engine1ProfileSkillIntegrationTest`, `Engine1ProjectIntegrationTest`, `Engine1RosterIntegrationTest` and `Engine1PreferenceIntegrationTest` cover ownership, role rejection, validation, status transitions, visibility, locking (including concurrency), all-or-nothing rosters and preference confidentiality.
* `Engine2ConstraintIntegrationTest` and `Engine2FeasibilityIntegrationTest` cover version 0, full/empty/historical versions, validation with no writes, ownership and role rejection, `FORMED`/later and active-job freezes, concurrent version allocation, read-only feasibility, roster eligibility, below-minimum and slack-zero plans, mandatory-skill shortage, off-roster pairs and team-size infeasibility.
* Unit tests cover `CompatibilityScorer` (directed weights, mutual pairs, unsatisfied and no-preference metrics), `DeterministicTieBreaker`, `FormationEngine` (success, prohibited pairs, mandatory skills, search limits, determinism), `FormationPlanValidator`, `TeamNameGenerator` and `FormationSearchProperties` (defaults and startup rejection).
* `Engine3FormationIntegrationTest` covers successful persistence, `ALGORITHM` source, team naming, seed/version recording, project-status preservation, rerun after completion, pre-job failures with no job, unsupported soft/rule-type rejection, mandatory-skill and prohibited-pair enforcement, API role/ownership security, retrieval/history paging and eligible-only rosters.
* `Engine3FormationConcurrencyIntegrationTest` holds the real engine inside T2 and proves a second run receives 409 while the first job is `RUNNING`.
* `Engine3FormationFailureIntegrationTest` injects a persistence failure after one candidate team is written and proves T2 rolls back, T3 records one `FAILED` job with a safe reason, and no candidate teams or members remain.
* `ClassmatesIntegrationTest` covers self-exclusion, paging, draft/non-rostered visibility, visibility after a preference lock and role/authentication rejection.
* `TestDatabaseCleanerTest` proves the integration cleaner refuses non-Testcontainers URLs, including the real `student_team_formation` database; `TestDatabaseCleaner` requires a `jdbc:mysql://` localhost URL, a non-3306 random port and database name `test`.
* `StartupConfigurationTest`, `AdminSeedIntegrationTest` and `HealthIntegrationTest` cover fail-fast configuration, first-admin seeding and health with unreachable SMTP.
* Boot 4's Jackson 3 mapper is configured with `spring.jackson.deserialization.fail-on-unknown-properties: true`, and tests prove that `phone`/`role` fields are rejected and persist nothing.
* The SQL migration sequence and Hibernate validation are checked by `BackendApplicationTests`; the suite asserts that five migrations (V1–V5) applied successfully. V5 is the only Task 2.6 migration.

## Configuration notes

`application.yml` no longer declares an explicit Hibernate dialect; Hibernate detects the MySQL dialect from the JDBC connection. Java 21 is configured in the Maven POM and is the version used by the build.

The test profile supplies fixed, non-production auth values. The mail health indicator is disabled globally, not only in tests, because the application can serve requests while SMTP is temporarily unavailable.
