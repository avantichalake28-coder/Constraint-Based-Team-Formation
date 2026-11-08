package com.teamformation.backend.engine.formation;

import java.util.Map;

/**
 * Seed-derived deterministic ordering helpers. The same seed and ids always
 * produce the same rank on every JVM. No random source is consulted here.
 */
public final class DeterministicTieBreaker {

    private static final long GOLDEN_GAMMA = 0x9E3779B97F4A7C15L;
    private static final long MIX_A = 0xBF58476D1CE4E5B9L;
    private static final long MIX_B = 0x94D049BB133111EBL;
    private static final long TEAM_SALT = 1_000_003L;

    private DeterministicTieBreaker() {
    }

    /**
     * Returns a stable non-negative 31-bit rank for one student.
     */
    public static long studentRank(long seed, long studentId) {
        long z = seed + GOLDEN_GAMMA * (studentId + 1L);
        z = (z ^ (z >>> 30)) * MIX_A;
        z = (z ^ (z >>> 27)) * MIX_B;
        z = z ^ (z >>> 31);
        return z & 0x7FFFFFFFL;
    }

    /**
     * Returns a stable non-negative 31-bit rank for one team ordinal.
     */
    public static long teamRank(long seed, int teamOrdinal) {
        return studentRank(seed, TEAM_SALT + teamOrdinal);
    }

    /**
     * Scalar plan tie-break: the sum of each student's seed rank multiplied by
     * the canonical ordinal of the team the student occupies. Lower values are
     * preferred. The value changes predictably when two students swap teams.
     */
    public static long planTieBreak(long seed, Map<Long, Integer> teamOf, Iterable<Long> studentIds) {
        long total = 0L;
        for (Long studentId : studentIds) {
            Integer team = teamOf.get(studentId);
            if (team != null && team >= 0) {
                total += studentRank(seed, studentId) * team;
            }
        }
        return total;
    }
}
