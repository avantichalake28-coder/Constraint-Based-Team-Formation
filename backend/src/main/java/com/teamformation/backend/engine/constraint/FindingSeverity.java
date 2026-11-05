package com.teamformation.backend.engine.constraint;

/**
 * Severity of a feasibility finding. Critical findings make the overall result
 * INFEASIBLE; warnings are reported without changing it.
 */
public enum FindingSeverity {
    CRITICAL,
    WARNING,
    INFO
}
