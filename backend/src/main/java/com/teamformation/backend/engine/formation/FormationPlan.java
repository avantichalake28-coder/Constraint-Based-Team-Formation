package com.teamformation.backend.engine.formation;

import java.util.List;

/**
 * Immutable output of the pure formation engine. A plan is only persisted after
 * {@link FormationPlanValidator} accepts it.
 *
 * @param teams                        canonical ordered teams
 * @param totalPreferenceScore         sum of all satisfied directed preferences
 * @param unsatisfiedPreferenceStudents students who submitted at least one
 *                                     preference and satisfied none
 * @param studentsWithoutPreferences   students who submitted no preference
 * @param algorithmVersion             version label recorded with the run
 * @param seed                         seed used by the run
 * @param deterministicTieBreak        seed-derived plan tie-break value; lower
 *                                     is preferred
 */
public record FormationPlan(
        List<FormationTeam> teams,
        double totalPreferenceScore,
        int unsatisfiedPreferenceStudents,
        int studentsWithoutPreferences,
        String algorithmVersion,
        long seed,
        long deterministicTieBreak
) {

    public FormationPlan {
        if (teams == null) {
            throw new IllegalArgumentException("teams are required");
        }
        teams = List.copyOf(teams);
        if (!Double.isFinite(totalPreferenceScore) || totalPreferenceScore < 0.0) {
            throw new IllegalArgumentException("totalPreferenceScore must be finite and non-negative");
        }
        if (unsatisfiedPreferenceStudents < 0) {
            throw new IllegalArgumentException("unsatisfiedPreferenceStudents must be at least 0");
        }
        if (studentsWithoutPreferences < 0) {
            throw new IllegalArgumentException("studentsWithoutPreferences must be at least 0");
        }
        if (algorithmVersion == null || algorithmVersion.isBlank()) {
            throw new IllegalArgumentException("algorithmVersion is required");
        }
    }

    public int teamCount() {
        return teams.size();
    }

    public int assignedStudentCount() {
        return teams.stream().mapToInt(FormationTeam::actualSize).sum();
    }
}
