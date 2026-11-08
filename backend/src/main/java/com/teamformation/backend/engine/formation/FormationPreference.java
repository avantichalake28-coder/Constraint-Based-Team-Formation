package com.teamformation.backend.engine.formation;

/**
 * One directed teammate preference. Rank 1 is worth 1.0 and rank 2 is worth
 * 0.5; no other rank or weight is supported.
 */
public record FormationPreference(long studentId, long preferredStudentId, int rank) {

    public FormationPreference {
        if (studentId <= 0 || preferredStudentId <= 0) {
            throw new IllegalArgumentException("both student ids must be positive");
        }
        if (studentId == preferredStudentId) {
            throw new IllegalArgumentException("a preference cannot point at the same student");
        }
        if (rank != 1 && rank != 2) {
            throw new IllegalArgumentException("preference rank must be 1 or 2");
        }
    }

    public double weight() {
        return rank == 1 ? 1.0 : 0.5;
    }
}
