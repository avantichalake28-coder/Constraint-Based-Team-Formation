package com.teamformation.backend.entity.enums;

/**
 * Coarse lifecycle phase in which a formation job failed. Kept deliberately
 * small so the value remains stable for operations and Engine 4.
 */
public enum FailurePhase {
    PRE_JOB,
    INPUT_SNAPSHOT,
    EXECUTION,
    PERSISTENCE,
    VERIFICATION,
    RECOVERY,
    INTERNAL
}
