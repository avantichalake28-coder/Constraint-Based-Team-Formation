-- =============================================================================
-- V1__init.sql — Initial Flyway Migration
-- =============================================================================
-- Purpose: Establishes the minimum necessary database foundation.
--          Business schema tables will be added in subsequent migrations.
-- =============================================================================

-- Verify connectivity and baseline the migration history.
-- This migration intentionally does not create business tables yet.

-- Create a lightweight application metadata table to confirm Flyway execution.
CREATE TABLE IF NOT EXISTS app_metadata (
    meta_key   VARCHAR(100)  NOT NULL,
    meta_value VARCHAR(500)  NULL,
    created_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (meta_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- Seed a version record to confirm successful migration.
INSERT INTO app_metadata (meta_key, meta_value)
VALUES ('schema_version', '1.0.0')
ON DUPLICATE KEY UPDATE meta_value = '1.0.0', updated_at = CURRENT_TIMESTAMP;

