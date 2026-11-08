package com.teamformation.backend.engine.formation;

/**
 * Lexicographic plan objective. A plan is better when it has:
 * <ol>
 *   <li>a higher total preference score,</li>
 *   <li>fewer students with preferences but none satisfied,</li>
 *   <li>a lower seed-derived plan tie-break value.</li>
 * </ol>
 * The comparison is deterministic and uses exact double comparison because all
 * scores are sums of 1.0 and 0.5.
 */
public record FormationObjective(
        double totalPreferenceScore,
        int unsatisfiedPreferenceStudents,
        long deterministicTieBreak
) {

    /**
     * Returns a positive value when this objective is better than {@code other}.
     */
    public int compareTo(FormationObjective other) {
        int score = Double.compare(totalPreferenceScore, other.totalPreferenceScore);
        if (score != 0) {
            return score;
        }
        int unsatisfied = Integer.compare(
                other.unsatisfiedPreferenceStudents, unsatisfiedPreferenceStudents);
        if (unsatisfied != 0) {
            return unsatisfied;
        }
        return Long.compare(other.deterministicTieBreak, deterministicTieBreak);
    }
}
