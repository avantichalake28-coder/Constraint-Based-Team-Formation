package com.teamformation.backend.engine.constraint;

/**
 * Machine-readable reason why the team-size planner could not produce a plan.
 */
public enum PlannerInfeasibilityReason {
    EMPTY_POPULATION,
    BELOW_MINIMUM,
    REMAINDER_NOT_PLACEABLE
}
