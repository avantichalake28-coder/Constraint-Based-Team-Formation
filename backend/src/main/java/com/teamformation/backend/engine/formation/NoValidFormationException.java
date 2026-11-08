package com.teamformation.backend.engine.formation;

/**
 * Raised when the bounded search completes without finding any complete plan
 * that satisfies every hard constraint.
 */
public class NoValidFormationException extends RuntimeException {

    public NoValidFormationException(String message) {
        super(message);
    }
}
