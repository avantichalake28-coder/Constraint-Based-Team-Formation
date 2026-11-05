-- =============================================================================
-- V5__constraint_version_audit.sql — Task 2.6 Engine 2
-- =============================================================================
-- V1–V4 are immutable. This migration only adds a new table; it does not alter,
-- drop or rewrite any existing table or row.
--
-- project_constraints already stores the rows of a version. A version can be
-- empty (a mentor may save a configuration without prohibited pairs, mandatory
-- skills or desirable skills), so the actor and timestamp cannot be stored on
-- those rows. This small companion table records one audit row per saved
-- version and is also the authoritative source for the current version number.
--
-- Data safety: this is a new table. No existing data is deleted or modified.
-- =============================================================================

CREATE TABLE IF NOT EXISTS project_constraint_versions (
    id         BIGINT    AUTO_INCREMENT PRIMARY KEY,
    project_id BIGINT    NOT NULL,
    version    INT       NOT NULL,
    changed_by BIGINT    NOT NULL,
    changed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_constraint_versions_project_version UNIQUE (project_id, version),
    CONSTRAINT fk_constraint_versions_project
        FOREIGN KEY (project_id) REFERENCES projects (id) ON DELETE CASCADE,
    CONSTRAINT fk_constraint_versions_changed_by
        FOREIGN KEY (changed_by) REFERENCES users (id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- -----------------------------------------------------------------------------
-- Update Schema Version Baseline
-- -----------------------------------------------------------------------------
INSERT INTO app_metadata (meta_key, meta_value)
VALUES ('schema_version', '5.0.0')
ON DUPLICATE KEY UPDATE meta_value = '5.0.0', updated_at = CURRENT_TIMESTAMP;
