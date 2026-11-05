# Database Design

## Migration history

| Version | Purpose |
| --- | --- |
| V1 | Baseline and `app_metadata`. |
| V2 | Initial domain tables and foreign keys. |
| V3 | Task 2.2b corrections: SRN moved to profiles, team-size range, preference constraints, candidate/finalized team split, formation-job seed/version, source/provenance columns. |
| V4 | Task 2.4: make `candidate_teams.formation_job_id` NOT NULL with `ON DELETE CASCADE`, add `email_verification_tokens` (hash-only, expires/used), update `app_metadata.schema_version` to 4.0.0. |
| V5 | Task 2.6: add `project_constraint_versions` for constraint version actors/timestamps and update `app_metadata.schema_version` to 5.0.0. |

V1–V4 are immutable. All schema corrections live in a later versioned migration. Task 2.5 Part A and Engine 1 use the existing tables as designed. V5 adds only the constraint-version companion table; it does not alter or rewrite existing domain rows.

## Entity-relationship diagram

```mermaid
erDiagram
    USERS ||--o| STUDENT_PROFILES : "student has one profile"
    USERS ||--o{ PROJECTS : "mentor owns"
    USERS ||--o{ PROJECT_ROSTER : "eligible student"
    USERS ||--o{ TEAMMATE_PREFERENCES : "submits"
    USERS ||--o{ TEAMMATE_PREFERENCES : "is preferred"
    USERS ||--o{ FORMATION_JOBS : "triggers"
    USERS ||--o{ CANDIDATE_TEAM_MEMBERS : "is member"
    USERS ||--o{ FINALIZED_TEAMS : "finalizes"
    USERS ||--o{ FINALIZED_TEAM_MEMBERSHIPS : "is member"
    USERS ||--o{ EMAIL_VERIFICATION_TOKENS : "requests verification"
    USERS ||--o{ PROJECT_CONSTRAINT_VERSIONS : "changes"

    STUDENT_PROFILES ||--o{ STUDENT_SKILLS : "claims"
    SKILLS ||--o{ STUDENT_SKILLS : "is claimed"

    PROJECTS ||--o{ PROJECT_ROSTER : "has roster"
    PROJECTS ||--o{ TEAMMATE_PREFERENCES : "receives"
    PROJECTS ||--o{ PROJECT_CONSTRAINTS : "configures"
    PROJECTS ||--o{ PROJECT_CONSTRAINT_VERSIONS : "versions"
    PROJECTS ||--o{ FORMATION_JOBS : "runs"
    PROJECTS ||--o{ CANDIDATE_TEAMS : "produces"
    PROJECTS ||--o{ FINALIZED_TEAMS : "finalizes"
    PROJECTS ||--o{ TEAM_VALIDATION_REPORTS : "evaluates"
    PROJECTS ||--o{ MENTOR_APPROVALS : "approves"

    FORMATION_JOBS ||--o{ CANDIDATE_TEAMS : "generates"
    FORMATION_JOBS ||--o{ TEAM_VALIDATION_REPORTS : "evaluates"

    CANDIDATE_TEAMS ||--o{ CANDIDATE_TEAM_MEMBERS : "contains"
    FINALIZED_TEAMS ||--o{ FINALIZED_TEAM_MEMBERSHIPS : "contains"

    TEAM_VALIDATION_REPORTS ||--o{ VALIDATION_FINDINGS : "explains"
    TEAM_VALIDATION_REPORTS ||--o{ MENTOR_APPROVALS : "supports"
    CANDIDATE_TEAMS ||--o{ VALIDATION_FINDINGS : "may be referenced"

    USERS {
        bigint id PK
        varchar email UK
        varchar full_name
        varchar password_hash
        varchar role
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    STUDENT_PROFILES {
        bigint id PK
        bigint user_id FK_UK "unique, references users"
        varchar srn UK "NOT NULL, case-sensitive CHECK"
        varchar department
        int semester
        varchar interests
        timestamp created_at
        timestamp updated_at
    }

    SKILLS {
        bigint id PK
        varchar name UK "case-insensitive under utf8mb4_unicode_ci"
        varchar category
        varchar description
    }

    STUDENT_SKILLS {
        bigint id PK
        bigint profile_id FK
        bigint skill_id FK
        int proficiency_level "CHECK 1..5"
        timestamp created_at
    }

    PROJECTS {
        bigint id PK
        bigint mentor_id FK
        varchar title
        varchar problem_statement
        varchar course_code
        int min_team_size "NOT NULL default 2, CHECK >= 2"
        int max_team_size "NOT NULL default 4"
        int team_size_slack "NOT NULL default 1, CHECK >= 0"
        timestamp preference_deadline
        timestamp preferences_locked_at
        varchar status
        timestamp created_at
        timestamp updated_at
    }

    PROJECT_ROSTER {
        bigint id PK
        bigint project_id FK
        bigint student_id FK
        varchar eligibility_status
        timestamp enrolled_at
    }

    TEAMMATE_PREFERENCES {
        bigint id PK
        bigint project_id FK
        bigint student_id FK
        bigint preferred_student_id FK
        int priority_rank "CHECK 1..2"
        timestamp submitted_at
    }

    PROJECT_CONSTRAINTS {
        bigint id PK
        bigint project_id FK
        varchar constraint_type
        varchar rule_type
        varchar rule_configuration
        double weight
        int version
        boolean is_active
        timestamp created_at
    }

    PROJECT_CONSTRAINT_VERSIONS {
        bigint id PK
        bigint project_id FK
        int version
        bigint changed_by FK
        timestamp changed_at
    }

    FORMATION_JOBS {
        bigint id PK
        bigint project_id FK
        varchar job_status
        int constraint_version
        bigint triggered_by_id FK
        bigint seed
        varchar algorithm_version
        timestamp started_at
        timestamp completed_at
        varchar error_message
        timestamp created_at
    }

    CANDIDATE_TEAMS {
        bigint id PK
        bigint project_id FK
        bigint formation_job_id FK_UK "NOT NULL, unique with id"
        varchar team_name
        varchar status
        double compatibility_score
        timestamp created_at
        timestamp updated_at
    }

    CANDIDATE_TEAM_MEMBERS {
        bigint id PK
        bigint candidate_team_id FK
        bigint formation_job_id FK "denormalised run id"
        bigint student_id FK
        varchar source "ALGORITHM or MANUAL_OVERRIDE"
        bigint changed_by FK "nullable"
        timestamp changed_at
        timestamp assigned_at
    }

    FINALIZED_TEAMS {
        bigint id PK
        bigint project_id FK_UK "unique with id"
        varchar team_name
        bigint finalized_by FK
        timestamp finalized_at
    }

    FINALIZED_TEAM_MEMBERSHIPS {
        bigint id PK
        bigint finalized_team_id FK
        bigint project_id FK
        bigint student_id FK
        timestamp assigned_at
        varchar source "ALGORITHM or MANUAL_OVERRIDE"
    }

    TEAM_VALIDATION_REPORTS {
        bigint id PK
        bigint project_id FK
        bigint formation_job_id FK
        varchar validation_status
        varchar summary
        timestamp evaluated_at
    }

    VALIDATION_FINDINGS {
        bigint id PK
        bigint report_id FK
        bigint candidate_team_id FK "nullable"
        varchar severity
        varchar rule_code
        varchar message
        timestamp created_at
    }

    MENTOR_APPROVALS {
        bigint id PK
        bigint project_id FK
        bigint mentor_id FK
        bigint validation_report_id FK
        varchar approval_status
        timestamp decision_timestamp
        varchar remarks
    }

    EMAIL_VERIFICATION_TOKENS {
        bigint id PK
        bigint user_id FK
        varchar token_hash UK "SHA-256 hex, raw token never stored"
        timestamp expires_at
        timestamp used_at "nullable, set when consumed"
        timestamp created_at
    }
```

## Engine 1 usage notes

Engine 1 reuses the V2/V3 tables without schema changes:

* `projects.status` moves through `DRAFT` → `PUBLISHED` → `PREFERENCES_LOCKED`. Later lifecycle values are owned by future engines and are retained when a mentor locks preferences manually.
* `projects.preferences_locked_at` records a manual lock. `projects.preference_deadline` is the automatic lock trigger; a project is locked when either is set/reached.
* `project_roster` is still the only eligibility source for preferences, candidate-team members and finalized memberships. Roster removal is blocked by those references, so an application-level 409 is returned before MySQL rejects the delete.
* `teammate_preferences` keeps `UNIQUE(project_id, student_id, priority_rank)` and one-to-two ranks. Engine 1 writes rank 1 and 2 from the request order.
* `student_profiles` and `student_skills` are updated only through student-owned endpoints. SRN and email are not editable.
* `skills.name` retains its case-insensitive unique collation; the service also maps a concurrent duplicate insert to 409.

`preference_deadline` and `preferences_locked_at` are second-precision `TIMESTAMP` columns. MySQL rounds sub-second values on write, so the service compares deadline values with a one-second tolerance when deciding whether a request actually changed a locked field.

## Engine 2 usage notes

Engine 2 reuses `project_constraints` and adds one companion table in V5:

* `project_constraints.version` is the configuration version. A save inserts a new set of rows with the next version; older rows are never updated or deleted, so a formation job can keep referencing the version it started with.
* `constraint_type` is `HARD` for both Task 2.6 rule types, prohibited pairs and mandatory skills.
* `rule_type` is one of `PROHIBITED_PAIR` (`rule_configuration` = `studentId1,studentId2`) or `MANDATORY_SKILL` (`skillId:minimumProficiency`). Older rule types remain in the enum but are ignored by the Task 2.6 reader.
* `project_constraint_versions` has one row per saved version and is the authoritative source for the current version. It is separate because an empty configuration has no `project_constraints` rows, so the actor and timestamp cannot live only on those rows.
* The current version is the highest `version` for the project. A project with no version row reports configuration version `0` with empty lists and no actor.
* Both foreign keys are deliberate: `project_id` cascades when a project is deleted, and `changed_by` uses `ON DELETE RESTRICT` so an audit row cannot lose its actor. `UNIQUE(project_id, version)` prevents duplicate versions.
* The feasibility report reads `project_roster` entries with eligibility `ELIGIBLE`, student skills and the current configuration. It writes nothing.

## Key constraints added or confirmed by V5

| Area | Constraint |
| --- | --- |
| Constraint versions | `UNIQUE(project_id, version)`; `project_id` references `projects(id) ON DELETE CASCADE`; `changed_by` references `users(id) ON DELETE RESTRICT`; `changed_by` and `changed_at` are `NOT NULL`. |
| App metadata | `app_metadata.schema_version` is advanced to `5.0.0` with an idempotent upsert; no other metadata row is deleted. |
| Data safety | V5 creates a new table only. No existing table is dropped, altered or rewritten, and no row is deleted or modified apart from the schema-version metadata value. |

## Key constraints added or confirmed by V4

| Area | Constraint |
| --- | --- |
| Candidate teams | `candidate_teams.formation_job_id` is `NOT NULL`. Because the V2 `ON DELETE SET NULL` rule is invalid for a NOT NULL column, the foreign key to `formation_jobs` is recreated with `ON DELETE CASCADE`. |
| Email verification | `email_verification_tokens.user_id` references `users.id ON DELETE CASCADE`; `token_hash` is `NOT NULL UNIQUE` (64-character SHA-256 hex); `expires_at` is `NOT NULL`; `used_at` is nullable. |
| App metadata | `app_metadata.schema_version` is advanced to `4.0.0` with an idempotent upsert; no other metadata row is deleted. |

## Key constraints added or confirmed by V3

| Area | Constraint |
| --- | --- |
| Student profile | `student_profiles.user_id` is `NOT NULL UNIQUE` with a foreign key to `users.id`. |
| SRN | `student_profiles.srn` is `NOT NULL UNIQUE`; `CHECK (REGEXP_LIKE(srn, '^[0-9]{2}FE[0-9]{2}[A-Z]{3}[0-9]{3}$', 'c'))`. The `'c'` match type makes the CHECK case-sensitive. |
| Team size | `CHECK (min_team_size >= 2 AND min_team_size <= max_team_size AND team_size_slack >= 0)`. |
| Roster | `UNIQUE(project_id, student_id)` on `project_roster`; this key is the target of the preference and finalized-membership composite foreign keys. |
| Preferences | `CHECK (priority_rank IN (1, 2))`, `UNIQUE(project_id, student_id, priority_rank)`, `UNIQUE(project_id, student_id, preferred_student_id)`, `CHECK (student_id <> preferred_student_id)`, and composite foreign keys to `project_roster`. |
| Skills | `UNIQUE(student_skills.profile_id, student_skills.skill_id)`, proficiency `CHECK (1..5)`, and case-insensitive unique skill names via the `utf8mb4_unicode_ci` collation. |
| Candidate teams | `UNIQUE(candidate_team_id, student_id)` and `UNIQUE(formation_job_id, student_id)`; `(candidate_team_id, formation_job_id)` is a composite foreign key to `candidate_teams(id, formation_job_id)`. |
| Finalized teams | `UNIQUE(project_id, student_id)` and `UNIQUE(finalized_team_id, student_id)`; `(finalized_team_id, project_id)` references `finalized_teams(id, project_id)`; `(project_id, student_id)` references `project_roster(project_id, student_id)`. |
| Provenance | `source` is restricted to `ALGORITHM` and `MANUAL_OVERRIDE` on both membership tables. |

## Notes

* `project_roster` is the post-V3 name; V2 originally created `project_rosters`.
* `candidate_team_members.formation_job_id` is `NOT NULL` so the one-team-per-student-per-run unique key is effective.
* Until V4, `candidate_teams.formation_job_id` was nullable because the legacy V2 column was nullable. V4 makes it `NOT NULL`; every candidate team now has an owning formation run.
* `email_verification_tokens` stores only a SHA-256 hash of the 256-bit random token. The raw token exists only in the verification email and is never written to the database or logs.
* `priority_rank` is the real V2 column name and is retained; constraints use that name.
* `project_constraint_versions` is the only V5 table. It stores audit data and the current version number; the actual rule rows remain in `project_constraints` keyed by the same `version`.
* No phone, mobile, contact or MSISDN column exists in the live schema. A test checks `information_schema.columns` for such names.
