-- =============================================================================
-- V3__schema_corrections.sql — Schema Corrections and Constraints
-- =============================================================================
-- Implements Task 2.2b. All affected V2 tables were verified empty before this
-- migration was written (the development database had only V1 applied, so the
-- V2 tables did not yet exist).
--
-- 1. Projects: mentor-set min/max/slack team sizes with CHECK, drop
--    target_team_size, add preferences_locked_at.
-- 2. Users/Student Profiles: move SRN to student_profiles (required, unique,
--    case-sensitive regex CHECK), drop users.srn and availability_details.
-- 3. Student Skills: proficiency 1-5 CHECK, drop verification_status.
-- 4. Project Roster: rename project_rosters to project_roster and keep its
--    UNIQUE(project_id, student_id) as a composite foreign-key target.
-- 5. Teammate Preferences: rank 1-2 CHECK, drop is_mutual, no self-preference
--    CHECK, composite FKs to project_roster.
-- 6. Formation Jobs: add seed and algorithm_version.
-- 7. Candidate Teams: rename teams/team_members, add provenance and
--    one-team-per-student-per-run constraints.
-- 8. Finalized Teams: add finalized_teams and finalized_team_memberships with
--    one-team-per-student-per-project constraint.
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Users & Student Profiles (SRN & Availability)
-- -----------------------------------------------------------------------------
ALTER TABLE users DROP COLUMN srn;

ALTER TABLE student_profiles
    ADD COLUMN srn VARCHAR(50) NOT NULL AFTER user_id,
    DROP COLUMN availability_details;

ALTER TABLE student_profiles
    ADD CONSTRAINT uk_student_profiles_srn UNIQUE (srn),
    ADD CONSTRAINT chk_student_profiles_srn
        CHECK (REGEXP_LIKE(srn, '^[0-9]{2}FE[0-9]{2}[A-Z]{3}[0-9]{3}$', 'c'));

-- -----------------------------------------------------------------------------
-- 2. Projects (Team Size min/max/slack, Preferences Locked At)
-- -----------------------------------------------------------------------------
ALTER TABLE projects
    ADD COLUMN min_team_size INT NOT NULL DEFAULT 2 AFTER course_code,
    ADD COLUMN max_team_size INT NOT NULL DEFAULT 4 AFTER min_team_size,
    ADD COLUMN team_size_slack INT NOT NULL DEFAULT 1 AFTER max_team_size,
    ADD COLUMN preferences_locked_at TIMESTAMP NULL AFTER preference_deadline,
    DROP COLUMN target_team_size;

ALTER TABLE projects
    ADD CONSTRAINT chk_projects_team_size
        CHECK (min_team_size >= 2
               AND min_team_size <= max_team_size
               AND team_size_slack >= 0);

-- -----------------------------------------------------------------------------
-- 3. Student Skills (Proficiency 1-5, Drop Verification Status)
-- -----------------------------------------------------------------------------
ALTER TABLE student_skills
    DROP COLUMN verification_status,
    MODIFY COLUMN proficiency_level INT NOT NULL;

ALTER TABLE student_skills
    ADD CONSTRAINT chk_student_skills_proficiency
        CHECK (proficiency_level >= 1 AND proficiency_level <= 5);

-- -----------------------------------------------------------------------------
-- 4. Project Roster (Rename & Composite FK Target)
-- -----------------------------------------------------------------------------
-- uk_project_student_roster(project_id, student_id) is retained from V2.
RENAME TABLE project_rosters TO project_roster;

-- -----------------------------------------------------------------------------
-- 5. Teammate Preferences (Rank 1-2, Drop is_mutual, Self-check, Composite FKs)
-- -----------------------------------------------------------------------------
-- V2 used priority_rank for the rank column; the real column name is retained.
ALTER TABLE teammate_preferences
    DROP FOREIGN KEY fk_preferences_student,
    DROP FOREIGN KEY fk_preferences_preferred_student,
    DROP COLUMN is_mutual;

ALTER TABLE teammate_preferences
    ADD CONSTRAINT chk_preferences_rank CHECK (priority_rank IN (1, 2)),
    ADD CONSTRAINT chk_preferences_no_self CHECK (student_id <> preferred_student_id),
    ADD CONSTRAINT uk_preferences_project_student_rank
        UNIQUE (project_id, student_id, priority_rank),
    ADD CONSTRAINT fk_preferences_roster_student
        FOREIGN KEY (project_id, student_id)
        REFERENCES project_roster (project_id, student_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_preferences_roster_preferred
        FOREIGN KEY (project_id, preferred_student_id)
        REFERENCES project_roster (project_id, student_id) ON DELETE CASCADE;

-- -----------------------------------------------------------------------------
-- 6. Formation Jobs (Seed & Algorithm Version)
-- -----------------------------------------------------------------------------
ALTER TABLE formation_jobs
    ADD COLUMN seed BIGINT NULL,
    ADD COLUMN algorithm_version VARCHAR(50) NULL;

-- -----------------------------------------------------------------------------
-- 7. Candidate Teams & Members (Separate from Finalized Teams)
-- -----------------------------------------------------------------------------
RENAME TABLE teams TO candidate_teams;

ALTER TABLE candidate_teams
    ADD CONSTRAINT uk_candidate_teams_id_job UNIQUE (id, formation_job_id);

-- Repoint validation findings from the old teams table.
ALTER TABLE validation_findings
    DROP FOREIGN KEY fk_validation_findings_team,
    CHANGE COLUMN team_id candidate_team_id BIGINT NULL;

ALTER TABLE validation_findings
    ADD CONSTRAINT fk_validation_findings_candidate_team
        FOREIGN KEY (candidate_team_id)
        REFERENCES candidate_teams (id) ON DELETE SET NULL;

RENAME TABLE team_members TO candidate_team_members;

ALTER TABLE candidate_team_members
    DROP FOREIGN KEY fk_team_members_team,
    DROP KEY uk_team_member,
    CHANGE COLUMN team_id candidate_team_id BIGINT NOT NULL,
    ADD COLUMN formation_job_id BIGINT NOT NULL AFTER candidate_team_id,
    ADD COLUMN source VARCHAR(20) NOT NULL DEFAULT 'ALGORITHM' AFTER student_id,
    ADD COLUMN changed_by BIGINT NULL AFTER source,
    ADD COLUMN changed_at TIMESTAMP NULL AFTER changed_by;

ALTER TABLE candidate_team_members
    ADD CONSTRAINT fk_candidate_members_team_job
        FOREIGN KEY (candidate_team_id, formation_job_id)
        REFERENCES candidate_teams (id, formation_job_id) ON DELETE CASCADE,
    ADD CONSTRAINT fk_candidate_members_changed_by
        FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE SET NULL,
    ADD CONSTRAINT chk_candidate_members_source
        CHECK (source IN ('ALGORITHM', 'MANUAL_OVERRIDE')),
    ADD CONSTRAINT uk_candidate_team_member UNIQUE (candidate_team_id, student_id),
    ADD CONSTRAINT uk_candidate_members_job_student UNIQUE (formation_job_id, student_id);

-- -----------------------------------------------------------------------------
-- 8. Finalized Teams & Finalized Team Memberships
-- -----------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS finalized_teams (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id      BIGINT       NOT NULL,
    team_name       VARCHAR(100) NOT NULL,
    finalized_by    BIGINT       NOT NULL,
    finalized_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_finalized_teams_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_finalized_teams_user
        FOREIGN KEY (finalized_by) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_finalized_teams_id_project UNIQUE (id, project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS finalized_team_memberships (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    finalized_team_id BIGINT      NOT NULL,
    project_id        BIGINT      NOT NULL,
    student_id        BIGINT      NOT NULL,
    assigned_at       TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    source            VARCHAR(20) NOT NULL DEFAULT 'ALGORITHM',
    CONSTRAINT fk_finalized_members_team_proj
        FOREIGN KEY (finalized_team_id, project_id)
        REFERENCES finalized_teams (id, project_id) ON DELETE CASCADE,
    CONSTRAINT fk_finalized_members_roster
        FOREIGN KEY (project_id, student_id)
        REFERENCES project_roster (project_id, student_id) ON DELETE CASCADE,
    CONSTRAINT chk_finalized_members_source
        CHECK (source IN ('ALGORITHM', 'MANUAL_OVERRIDE')),
    CONSTRAINT uk_finalized_members_project_student
        UNIQUE (project_id, student_id),
    CONSTRAINT uk_finalized_members_team_student
        UNIQUE (finalized_team_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 9. Update Schema Version Baseline
-- -----------------------------------------------------------------------------
INSERT INTO app_metadata (meta_key, meta_value)
VALUES ('schema_version', '3.0.0')
ON DUPLICATE KEY UPDATE meta_value = '3.0.0', updated_at = CURRENT_TIMESTAMP;
