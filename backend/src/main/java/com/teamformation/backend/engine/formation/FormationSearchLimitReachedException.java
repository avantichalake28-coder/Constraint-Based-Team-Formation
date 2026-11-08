package com.teamformation.backend.engine.formation;

/**
 * Raised when the placement search reaches its configured node budget before a
 * complete valid plan is found. A partial plan is never returned.
 */
public class FormationSearchLimitReachedException extends RuntimeException {

    public FormationSearchLimitReachedException(int maxNodes) {
        super("The formation search reached its node budget of " + maxNodes
                + " without finding a complete valid plan");
    }
}
