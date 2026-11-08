package com.teamformation.backend.entity.enums;

/**
 * Stable, machine-readable reason for a failed formation job. A code is safe to
 * expose and must never contain an exception class name or a stack trace.
 */
public enum FailureCode {
    PROJECT_NOT_READY,
    ACTIVE_JOB_EXISTS,
    INFEASIBLE_CONFIGURATION,
    UNSUPPORTED_CONSTRAINT,
    CONSTRAINT_VERSION_CHANGED,
    SNAPSHOT_INTEGRITY,
    SEARCH_LIMIT_REACHED,
    EXECUTION_BUDGET_EXCEEDED,
    NO_VALID_PLAN,
    PLAN_VALIDATION_FAILED,
    PERSISTENCE_VERIFICATION_FAILED,
    THROTTLED,
    INTERNAL_ERROR,
    STALE_JOB
}
