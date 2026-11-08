package com.teamformation.backend.engine.formation;

/**
 * Raised when the optional wall-clock budget of one formation run expires
 * before the bounded search finishes. The plan is discarded and the job is
 * failed with a stable {@code EXECUTION_BUDGET_EXCEEDED} reason.
 */
public class FormationExecutionBudgetExceededException extends RuntimeException {

    public FormationExecutionBudgetExceededException() {
        super("The formation execution budget was exhausted before a valid plan was found");
    }

    public FormationExecutionBudgetExceededException(long budgetMillis) {
        super("The formation execution budget of " + budgetMillis
                + " ms was exhausted before a valid plan was found");
    }
}
