-- =============================================================================
-- V4__auth_and_formation_job_integrity.sql — Task 2.4
-- =============================================================================
-- V1, V2 and V3 are immutable. This migration:
--
-- 1. Makes candidate_teams.formation_job_id NOT NULL so a candidate team
--    cannot exist without the formation run that produced it. The old
--    ON DELETE SET NULL foreign key is replaced with ON DELETE CASCADE
--    because SET NULL is invalid for a NOT NULL column.
-- 2. Creates email_verification_tokens for student email verification.
--
-- Data safety: before this migration was written, the development database
-- (student_team_formation) was queried and candidate_teams contained 0 rows.
-- No row is deleted or modified by this migration. The V4 table is new.
--
-- Emptiness checks that were run:
--   SELECT COUNT(*) FROM candidate_teams;                 -- returned 0
--   SELECT COUNT(*) FROM information_schema.TABLES
--    WHERE TABLE_SCHEMA = DATABASE()
--      AND TABLE_NAME = 'email_verification_tokens';      -- returned 0
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 1. Candidate teams always belong to a formation run
-- -----------------------------------------------------------------------------
-- The V2 foreign key used ON DELETE SET NULL, which is incompatible with a
-- NOT NULL column. A candidate team cannot exist without the run that produced
-- it, so deleting a formation job now deletes its generated candidate teams
-- (their members already cascade from candidate_teams).
ALTER TABLE candidate_teams
    DROP FOREIGN KEY fk_teams_formation_job;

ALTER TABLE candidate_teams
    MODIFY COLUMN formation_job_id BIGINT NOT NULL;

ALTER TABLE candidate_teams
    ADD CONSTRAINT fk_teams_formation_job
        FOREIGN KEY (formation_job_id) REFERENCES formation_jobs (id) ON DELETE CASCADE;

-- -----------------------------------------------------------------------------
-- 2. Email verification tokens
-- -----------------------------------------------------------------------------
-- Only a SHA-256 hash of the random token is stored. The raw token is sent to
-- the student and never persisted. used_at is NULL until the token is consumed.
CREATE TABLE IF NOT EXISTS email_verification_tokens (
    id         BIGINT       AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL,
    token_hash VARCHAR(64)  NOT NULL,
    expires_at TIMESTAMP    NOT NULL,
    used_at    TIMESTAMP    NULL,
    created_at TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens_user
        FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE CASCADE,
    INDEX idx_email_verification_tokens_user (user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- 3. Update Schema Version Baseline
-- -----------------------------------------------------------------------------
INSERT INTO app_metadata (meta_key, meta_value)
VALUES ('schema_version', '4.0.0')
ON DUPLICATE KEY UPDATE meta_value = '4.0.0', updated_at = CURRENT_TIMESTAMP;
