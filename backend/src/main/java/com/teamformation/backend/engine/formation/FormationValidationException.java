package com.teamformation.backend.engine.formation;

import java.util.List;

/**
 * Raised when a generated plan violates one or more structural or hard-constraint
 * invariants. The violations are safe, human-readable descriptions.
 */
public class FormationValidationException extends RuntimeException {

    private final List<String> violations;

    public FormationValidationException(List<String> violations) {
        super("Formation plan is invalid: " + String.join("; ", violations));
        this.violations = List.copyOf(violations);
    }

    public List<String> getViolations() {
        return violations;
    }
}
