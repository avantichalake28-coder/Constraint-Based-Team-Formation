-- =============================================================================
-- V6__engine3_hardening.sql — V2.11 Engine 3 Reliability & Consistency
-- =============================================================================
-- V1–V5 are immutable. This migration is additive and nullable/backfilled where
-- historical rows already exist. It never drops a column, deletes a row, or
-- fabricates provenance for legacy data.
--
-- 1. projects.version — optimistic lock guard for project lifecycle writes.
-- 2. formation_jobs — structured failure code/phase, completion-time metrics,
--    duration and indexes for stale-job recovery.
-- 3. formation_input_snapshots and child tables — authoritative T1 input copy
--    used by T2/Engine 4; canonical SHA-256 input hash.
-- 4. finalized_teams / finalized_team_memberships — nullable provenance links
--    to the formation job, candidate team and candidate membership.
-- 5. Schema version becomes 6.0.0.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Projects — optimistic version
-- -----------------------------------------------------------------------------
ALTER TABLE projects
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0 AFTER status;

-- -----------------------------------------------------------------------------
-- 2. Formation jobs — structured failure, metrics and recovery support
-- -----------------------------------------------------------------------------
ALTER TABLE formation_jobs
    ADD COLUMN failure_code VARCHAR(50) NULL AFTER error_message,
    ADD COLUMN failure_phase VARCHAR(30) NULL AFTER failure_code,
    ADD COLUMN total_preference_score DOUBLE NULL AFTER failure_phase,
    ADD COLUMN unsatisfied_preference_students INT NULL AFTER total_preference_score,
    ADD COLUMN students_without_preferences INT NULL AFTER unsatisfied_preference_students,
    ADD COLUMN duration_millis BIGINT NULL AFTER students_without_preferences;

ALTER TABLE formation_jobs
    ADD CONSTRAINT chk_formation_jobs_failure_code
        CHECK (failure_code IS NULL OR failure_code IN (
            'PROJECT_NOT_READY',
            'ACTIVE_JOB_EXISTS',
            'INFEASIBLE_CONFIGURATION',
            'UNSUPPORTED_CONSTRAINT',
            'CONSTRAINT_VERSION_CHANGED',
            'SNAPSHOT_INTEGRITY',
            'SEARCH_LIMIT_REACHED',
            'EXECUTION_BUDGET_EXCEEDED',
            'NO_VALID_PLAN',
            'PLAN_VALIDATION_FAILED',
            'PERSISTENCE_VERIFICATION_FAILED',
            'THROTTLED',
            'INTERNAL_ERROR',
            'STALE_JOB'
        )),
    ADD CONSTRAINT chk_formation_jobs_failure_phase
        CHECK (failure_phase IS NULL OR failure_phase IN (
            'PRE_JOB',
            'INPUT_SNAPSHOT',
            'EXECUTION',
            'PERSISTENCE',
            'VERIFICATION',
            'RECOVERY',
            'INTERNAL'
        )),
    ADD CONSTRAINT chk_formation_jobs_metrics
        CHECK (
            (unsatisfied_preference_students IS NULL OR unsatisfied_preference_students >= 0)
            AND (students_without_preferences IS NULL OR students_without_preferences >= 0)
            AND (duration_millis IS NULL OR duration_millis >= 0)
        );

-- One job-id is unique inside its project; used by the finalized-team FK so a
-- provenance row cannot claim a job from a different project.
ALTER TABLE formation_jobs
    ADD CONSTRAINT uk_formation_jobs_id_project UNIQUE (id, project_id);

ALTER TABLE formation_jobs
    ADD INDEX idx_formation_jobs_status_created (job_status, created_at),
    ADD INDEX idx_formation_jobs_status_started (job_status, started_at);

-- -----------------------------------------------------------------------------
-- 3. Formation input snapshots
-- -----------------------------------------------------------------------------
-- One immutable snapshot per formation job. The snapshot is written in T1 and
-- is the only input source for T2. Legacy jobs have no snapshot and are never
-- resumed; they can still be read, reaped and audited.
CREATE TABLE formation_input_snapshots (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    formation_job_id   BIGINT      NOT NULL,
    project_id         BIGINT      NOT NULL,
    snapshot_version   INT         NOT NULL,
    input_hash         CHAR(64)    NOT NULL,
    min_team_size      INT         NOT NULL,
    max_team_size      INT         NOT NULL,
    team_size_slack    INT         NOT NULL,
    constraint_version INT         NOT NULL,
    seed               BIGINT      NOT NULL,
    algorithm_version  VARCHAR(50) NOT NULL,
    created_at         TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_snapshot_job
        FOREIGN KEY (formation_job_id) REFERENCES formation_jobs (id) ON DELETE CASCADE,
    CONSTRAINT fk_snapshot_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT uk_snapshot_job UNIQUE (formation_job_id),
    CONSTRAINT chk_snapshot_version CHECK (snapshot_version = 1),
    CONSTRAINT chk_snapshot_team_size
        CHECK (min_team_size >= 2 AND min_team_size <= max_team_size AND team_size_slack >= 0),
    INDEX idx_snapshot_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE formation_input_snapshot_students (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT NOT NULL,
    student_id BIGINT NOT NULL,
    CONSTRAINT fk_snapshot_student_header
        FOREIGN KEY (snapshot_id) REFERENCES formation_input_snapshots (id) ON DELETE CASCADE,
    CONSTRAINT uk_snapshot_student UNIQUE (snapshot_id, student_id),
    INDEX idx_snapshot_student_id (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE formation_input_snapshot_skills (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id     BIGINT NOT NULL,
    student_id      BIGINT NOT NULL,
    skill_id        BIGINT NOT NULL,
    proficiency     INT    NOT NULL,
    CONSTRAINT fk_snapshot_skill_header
        FOREIGN KEY (snapshot_id) REFERENCES formation_input_snapshots (id) ON DELETE CASCADE,
    CONSTRAINT fk_snapshot_skill_skill
        FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uk_snapshot_skill UNIQUE (snapshot_id, student_id, skill_id),
    CONSTRAINT chk_snapshot_skill_proficiency CHECK (proficiency BETWEEN 1 AND 5),
    INDEX idx_snapshot_skill_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE formation_input_snapshot_preferences (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id          BIGINT NOT NULL,
    student_id           BIGINT NOT NULL,
    preferred_student_id BIGINT NOT NULL,
    preference_rank      INT    NOT NULL,
    CONSTRAINT fk_snapshot_preference_header
        FOREIGN KEY (snapshot_id) REFERENCES formation_input_snapshots (id) ON DELETE CASCADE,
    CONSTRAINT uk_snapshot_preference
        UNIQUE (snapshot_id, student_id, preferred_student_id),
    CONSTRAINT chk_snapshot_preference_rank CHECK (preference_rank IN (1, 2)),
    CONSTRAINT chk_snapshot_preference_self CHECK (student_id <> preferred_student_id),
    INDEX idx_snapshot_preference_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE formation_input_snapshot_constraints (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id        BIGINT      NOT NULL,
    source_constraint_id BIGINT    NULL,
    constraint_type    VARCHAR(20) NOT NULL,
    rule_type          VARCHAR(50) NOT NULL,
    rule_configuration VARCHAR(2000) NOT NULL,
    weight             DOUBLE      NULL,
    source_version     INT         NOT NULL,
    CONSTRAINT fk_snapshot_constraint_header
        FOREIGN KEY (snapshot_id) REFERENCES formation_input_snapshots (id) ON DELETE CASCADE,
    INDEX idx_snapshot_constraint_snapshot (snapshot_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE formation_input_snapshot_skill_names (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    snapshot_id BIGINT       NOT NULL,
    skill_id    BIGINT       NOT NULL,
    skill_name  VARCHAR(100) NOT NULL,
    CONSTRAINT fk_snapshot_skill_name_header
        FOREIGN KEY (snapshot_id) REFERENCES formation_input_snapshots (id) ON DELETE CASCADE,
    CONSTRAINT uk_snapshot_skill_name UNIQUE (snapshot_id, skill_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 4. Engine 4 provenance (nullable; no fabricated backfill)
-- -----------------------------------------------------------------------------
ALTER TABLE finalized_teams
    ADD COLUMN formation_job_id  BIGINT NULL AFTER project_id,
    ADD COLUMN candidate_team_id BIGINT NULL AFTER formation_job_id,
    ADD COLUMN constraint_version INT   NULL AFTER candidate_team_id,
    ADD COLUMN seed              BIGINT NULL AFTER constraint_version,
    ADD COLUMN algorithm_version VARCHAR(50) NULL AFTER seed,
    ADD COLUMN compatibility_score DOUBLE NULL AFTER algorithm_version;

ALTER TABLE finalized_teams
    ADD CONSTRAINT fk_finalized_teams_job_project
        FOREIGN KEY (formation_job_id, project_id)
        REFERENCES formation_jobs (id, project_id) ON DELETE RESTRICT,
    ADD CONSTRAINT fk_finalized_teams_candidate_job
        FOREIGN KEY (candidate_team_id, formation_job_id)
        REFERENCES candidate_teams (id, formation_job_id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_finalized_teams_candidate UNIQUE (candidate_team_id),
    ADD INDEX idx_finalized_teams_job (formation_job_id);

ALTER TABLE finalized_team_memberships
    ADD COLUMN candidate_team_member_id BIGINT NULL AFTER student_id;

ALTER TABLE finalized_team_memberships
    ADD CONSTRAINT fk_finalized_members_candidate
        FOREIGN KEY (candidate_team_member_id)
        REFERENCES candidate_team_members (id) ON DELETE RESTRICT,
    ADD CONSTRAINT uk_finalized_members_candidate UNIQUE (candidate_team_member_id),
    ADD INDEX idx_finalized_members_candidate (candidate_team_member_id);

-- -----------------------------------------------------------------------------
-- 5. Schema version baseline
-- -----------------------------------------------------------------------------
INSERT INTO app_metadata (meta_key, meta_value)
VALUES ('schema_version', '6.0.0')
ON DUPLICATE KEY UPDATE meta_value = '6.0.0', updated_at = CURRENT_TIMESTAMP;
