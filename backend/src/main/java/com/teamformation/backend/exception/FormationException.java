package com.teamformation.backend.exception;

import com.teamformation.backend.entity.enums.FailureCode;
import com.teamformation.backend.entity.enums.FailurePhase;
import org.springframework.http.HttpStatus;

/**
 * Engine 3 formation lifecycle failures. Every factory returns a safe message
 * that can also be stored on a failed formation job. A failure carries an
 * optional stable {@link FailureCode} and {@link FailurePhase} so the job row
 * and the API never expose exception internals.
 */
public class FormationException extends BusinessException {

    private final FailureCode failureCode;
    private final FailurePhase failurePhase;

    public FormationException(String message, HttpStatus status) {
        this(message, status, null, null);
    }

    public FormationException(String message, HttpStatus status,
                              FailureCode failureCode, FailurePhase failurePhase) {
        super(message, status);
        this.failureCode = failureCode;
        this.failurePhase = failurePhase;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }

    public FailurePhase getFailurePhase() {
        return failurePhase;
    }

    public static FormationException projectNotReady(String reason) {
        return new FormationException(
                "Team formation cannot start: " + reason, HttpStatus.CONFLICT,
                FailureCode.PROJECT_NOT_READY, FailurePhase.PRE_JOB);
    }

    public static FormationException activeJob() {
        return new FormationException(
                "Team formation cannot start while another formation job is READY or RUNNING",
                HttpStatus.CONFLICT, FailureCode.ACTIVE_JOB_EXISTS, FailurePhase.PRE_JOB);
    }

    public static FormationException unsupportedConstraint(String detail) {
        return new FormationException(
                "The current constraint configuration cannot be executed by formation: " + detail,
                HttpStatus.CONFLICT, FailureCode.UNSUPPORTED_CONSTRAINT, FailurePhase.PRE_JOB);
    }

    public static FormationException infeasible(String reason) {
        return new FormationException(
                "Team formation cannot start: " + reason, HttpStatus.CONFLICT,
                FailureCode.INFEASIBLE_CONFIGURATION, FailurePhase.PRE_JOB);
    }

    public static FormationException noValidPlan(String reason) {
        return new FormationException(
                "No valid team arrangement could be produced: " + reason,
                HttpStatus.UNPROCESSABLE_ENTITY, FailureCode.NO_VALID_PLAN, FailurePhase.EXECUTION);
    }

    public static FormationException searchLimitReached(int maxNodes) {
        return new FormationException(
                "The formation search reached its node budget of " + maxNodes
                        + " without finding a complete valid arrangement",
                HttpStatus.UNPROCESSABLE_ENTITY,
                FailureCode.SEARCH_LIMIT_REACHED, FailurePhase.EXECUTION);
    }

    public static FormationException internalFailure() {
        return new FormationException(
                "Team formation failed with an internal error; the job was marked FAILED",
                HttpStatus.INTERNAL_SERVER_ERROR, FailureCode.INTERNAL_ERROR, FailurePhase.INTERNAL);
    }

    public static FormationException snapshotIntegrity(String detail) {
        return new FormationException(
                "The formation input snapshot failed its integrity check: " + detail,
                HttpStatus.CONFLICT, FailureCode.SNAPSHOT_INTEGRITY, FailurePhase.INPUT_SNAPSHOT);
    }

    public static FormationException executionBudgetExceeded() {
        return new FormationException(
                "The formation execution budget was exhausted before a valid plan was produced",
                HttpStatus.UNPROCESSABLE_ENTITY,
                FailureCode.EXECUTION_BUDGET_EXCEEDED, FailurePhase.EXECUTION);
    }

    /**
     * Controlled response when the bounded execution capacity is already in
     * use. The request is rejected immediately and is never queued.
     */
    public static FormationException throttled() {
        return new FormationException(
                "Too many formation runs are executing; retry after an active run finishes",
                HttpStatus.TOO_MANY_REQUESTS, FailureCode.THROTTLED, FailurePhase.PRE_JOB);
    }

    public static FormationException constraintVersionChanged() {
        return new FormationException(
                "The project constraint configuration changed during the formation run",
                HttpStatus.CONFLICT,
                FailureCode.CONSTRAINT_VERSION_CHANGED, FailurePhase.INPUT_SNAPSHOT);
    }
}
