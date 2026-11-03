-- =============================================================================
-- V2__domain_foundation.sql — Domain & JPA Entity Foundation
-- =============================================================================
-- Implements the persistence schema strictly derived from the S1 Functional
-- Requirements Engineering Workbook (Team 6.docx).
--
-- Preserves existing baseline tables (V1__init.sql / app_metadata).
-- All foreign keys follow strict parent-before-child creation order.
-- =============================================================================

-- 1. Users table (Students, Mentors, Admins) — FR-01
CREATE TABLE IF NOT EXISTS users (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    srn           VARCHAR(50)  NULL UNIQUE,
    email         VARCHAR(150) NOT NULL UNIQUE,
    full_name     VARCHAR(150) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    is_active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_users_role (role),
    INDEX idx_users_srn (srn)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Student profiles table — FR-02
CREATE TABLE IF NOT EXISTS student_profiles (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id              BIGINT        NOT NULL UNIQUE,
    department           VARCHAR(100)  NULL,
    semester             INT           NULL,
    interests            VARCHAR(1000) NULL,
    availability_details VARCHAR(500)  NULL,
    created_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at           TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_profiles_user FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 3. Skills catalogue — FR-02, FR-05
CREATE TABLE IF NOT EXISTS skills (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    category    VARCHAR(50)  NULL,
    description VARCHAR(500) NULL,
    INDEX idx_skills_category (category)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 4. Student skills (proficiency & verified status) — FR-02
CREATE TABLE IF NOT EXISTS student_skills (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    profile_id          BIGINT      NOT NULL,
    skill_id            BIGINT      NOT NULL,
    proficiency_level   VARCHAR(20) NOT NULL DEFAULT 'BEGINNER',
    verification_status VARCHAR(20) NOT NULL DEFAULT 'CLAIMED',
    created_at          TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_student_skills_profile FOREIGN KEY (profile_id) REFERENCES student_profiles (id) ON DELETE CASCADE,
    CONSTRAINT fk_student_skills_skill FOREIGN KEY (skill_id) REFERENCES skills (id) ON DELETE RESTRICT,
    CONSTRAINT uk_student_skill UNIQUE (profile_id, skill_id),
    INDEX idx_student_skills_verification (verification_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 5. Projects table — FR-03, FR-04
CREATE TABLE IF NOT EXISTS projects (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    mentor_id           BIGINT        NOT NULL,
    title               VARCHAR(200)  NOT NULL,
    problem_statement   VARCHAR(2000) NOT NULL,
    course_code         VARCHAR(50)   NOT NULL,
    target_team_size    INT           NOT NULL,
    preference_deadline TIMESTAMP     NULL,
    status              VARCHAR(30)   NOT NULL DEFAULT 'DRAFT',
    created_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_projects_mentor FOREIGN KEY (mentor_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_projects_course (course_code),
    INDEX idx_projects_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 6. Project roster (enrolled eligible students) — FR-03, FR-10, FR-11
CREATE TABLE IF NOT EXISTS project_rosters (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id         BIGINT      NOT NULL,
    student_id         BIGINT      NOT NULL,
    eligibility_status VARCHAR(20) NOT NULL DEFAULT 'ELIGIBLE',
    enrolled_at        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_project_rosters_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_project_rosters_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_project_student_roster UNIQUE (project_id, student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 7. Teammate preferences — FR-07, FR-09
CREATE TABLE IF NOT EXISTS teammate_preferences (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id           BIGINT    NOT NULL,
    student_id           BIGINT    NOT NULL,
    preferred_student_id BIGINT    NOT NULL,
    priority_rank        INT       NOT NULL DEFAULT 1,
    is_mutual            BOOLEAN   NOT NULL DEFAULT FALSE,
    submitted_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_preferences_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_preferences_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_preferences_preferred_student FOREIGN KEY (preferred_student_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_student_preference UNIQUE (project_id, student_id, preferred_student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 8. Project constraints & versioned rule sets — FR-05, FR-06
CREATE TABLE IF NOT EXISTS project_constraints (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id         BIGINT        NOT NULL,
    constraint_type    VARCHAR(20)   NOT NULL,
    rule_type          VARCHAR(50)   NOT NULL,
    rule_configuration VARCHAR(2000) NOT NULL,
    weight             DOUBLE        NULL DEFAULT 1.0,
    version            INT           NOT NULL DEFAULT 1,
    is_active          BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_constraints_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    INDEX idx_constraints_version (project_id, version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 9. Formation jobs (job queue & states) — Engine E3 lifecycle
CREATE TABLE IF NOT EXISTS formation_jobs (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id         BIGINT        NOT NULL,
    job_status         VARCHAR(20)   NOT NULL DEFAULT 'READY',
    constraint_version INT           NOT NULL DEFAULT 1,
    triggered_by_id    BIGINT        NOT NULL,
    started_at         TIMESTAMP     NULL,
    completed_at       TIMESTAMP     NULL,
    error_message      VARCHAR(2000) NULL,
    created_at         TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_formation_jobs_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_formation_jobs_triggered_by FOREIGN KEY (triggered_by_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_formation_jobs_status (job_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 10. Teams table — FR-08, FR-10
CREATE TABLE IF NOT EXISTS teams (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id          BIGINT       NOT NULL,
    formation_job_id    BIGINT       NULL,
    team_name           VARCHAR(100) NOT NULL,
    status              VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    compatibility_score DOUBLE       NULL DEFAULT 0.0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT fk_teams_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_teams_formation_job FOREIGN KEY (formation_job_id) REFERENCES formation_jobs (id) ON DELETE SET NULL,
    INDEX idx_teams_project_status (project_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 11. Team members (exactly-once membership invariant) — FR-10
CREATE TABLE IF NOT EXISTS team_members (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    team_id     BIGINT    NOT NULL,
    student_id  BIGINT    NOT NULL,
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_team_members_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE CASCADE,
    CONSTRAINT fk_team_members_student FOREIGN KEY (student_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT uk_team_member UNIQUE (team_id, student_id),
    INDEX idx_team_members_student (student_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 12. Team validation reports — FR-12, FR-13
CREATE TABLE IF NOT EXISTS team_validation_reports (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id        BIGINT        NOT NULL,
    formation_job_id  BIGINT        NULL,
    validation_status VARCHAR(20)   NOT NULL DEFAULT 'PASSED',
    summary           VARCHAR(2000) NULL,
    evaluated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_validation_reports_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_validation_reports_job FOREIGN KEY (formation_job_id) REFERENCES formation_jobs (id) ON DELETE SET NULL,
    INDEX idx_validation_reports_status (validation_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 13. Validation findings (granular violation explainability) — FR-12, FR-14
CREATE TABLE IF NOT EXISTS validation_findings (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_id  BIGINT        NOT NULL,
    team_id    BIGINT        NULL,
    severity   VARCHAR(20)   NOT NULL DEFAULT 'WARNING',
    rule_code  VARCHAR(100)  NOT NULL,
    message    VARCHAR(2000) NOT NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_validation_findings_report FOREIGN KEY (report_id) REFERENCES team_validation_reports (id) ON DELETE CASCADE,
    CONSTRAINT fk_validation_findings_team FOREIGN KEY (team_id) REFERENCES teams (id) ON DELETE SET NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 14. Mentor approvals (audit trail & decision timestamp) — NFR-06, FR-12
CREATE TABLE IF NOT EXISTS mentor_approvals (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    project_id           BIGINT        NOT NULL,
    mentor_id            BIGINT        NOT NULL,
    validation_report_id BIGINT        NOT NULL,
    approval_status      VARCHAR(20)   NOT NULL DEFAULT 'APPROVED',
    decision_timestamp   TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    remarks              VARCHAR(1000) NULL,
    CONSTRAINT fk_mentor_approvals_project FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_mentor_approvals_mentor FOREIGN KEY (mentor_id) REFERENCES users (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mentor_approvals_report FOREIGN KEY (validation_report_id) REFERENCES team_validation_reports (id) ON DELETE RESTRICT,
    INDEX idx_mentor_approvals_decision (decision_timestamp)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Update schema version baseline
INSERT INTO app_metadata (meta_key, meta_value)
VALUES ('schema_version', '2.0.0')
ON DUPLICATE KEY UPDATE meta_value = '2.0.0', updated_at = CURRENT_TIMESTAMP;

