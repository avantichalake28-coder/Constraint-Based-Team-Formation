package com.teamformation.backend.engine.constraint;

/**
 * A hard rule that two students must never share a team.
 */
public record ProhibitedPair(Long studentId1, Long studentId2) {

    public ProhibitedPair {
        if (studentId1 == null || studentId2 == null) {
            throw new IllegalArgumentException("both student ids are required");
        }
        if (studentId1.equals(studentId2)) {
            throw new IllegalArgumentException("a prohibited pair must reference two different students");
        }
    }
}
