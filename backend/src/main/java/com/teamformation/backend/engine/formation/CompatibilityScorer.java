package com.teamformation.backend.engine.formation;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Pure directed preference scoring. Rank 1 is worth 1.0, rank 2 is worth 0.5,
 * and a preference is satisfied only when the requesting student and the
 * preferred student occupy the same team. Mutual preferences are counted in
 * both directions. No project-constraint weight and no skill-diversity term is
 * used.
 *
 * <p>All achievable totals are sums of 1.0 and 0.5, which are exactly
 * representable in binary floating point. Comparisons therefore use
 * {@link Double#compare(double, double)} without an epsilon.</p>
 */
public final class CompatibilityScorer {

    private CompatibilityScorer() {
    }

    public static Map<Long, List<FormationPreference>> indexByStudent(
            List<FormationPreference> preferences) {
        Map<Long, List<FormationPreference>> index = new HashMap<>();
        for (FormationPreference preference : preferences) {
            index.computeIfAbsent(preference.studentId(), ignored -> new java.util.ArrayList<>())
                    .add(preference);
        }
        index.replaceAll((ignored, value) -> List.copyOf(value));
        return Map.copyOf(index);
    }

    public static Map<Long, List<Long>> indexPreferredBy(
            List<FormationPreference> preferences) {
        Map<Long, List<Long>> index = new HashMap<>();
        for (FormationPreference preference : preferences) {
            index.computeIfAbsent(preference.preferredStudentId(), ignored -> new java.util.ArrayList<>())
                    .add(preference.studentId());
        }
        index.replaceAll((ignored, value) -> List.copyOf(value));
        return Map.copyOf(index);
    }

    /**
     * Scores every directed preference whose requester and target are in the
     * supplied team.
     */
    public static double scoreTeam(
            Collection<Long> memberIds,
            Map<Long, List<FormationPreference>> preferencesByStudent) {
        Set<Long> members = new LinkedHashSet<>(memberIds);
        double score = 0.0;
        for (Long studentId : memberIds) {
            for (FormationPreference preference
                    : preferencesByStudent.getOrDefault(studentId, List.of())) {
                if (members.contains(preference.preferredStudentId())) {
                    score += preference.weight();
                }
            }
        }
        return score;
    }

    public static double scoreTeam(
            Collection<Long> memberIds, List<FormationPreference> preferences) {
        return scoreTeam(memberIds, indexByStudent(preferences));
    }

    public static double scorePlan(
            Collection<List<Long>> teams, List<FormationPreference> preferences) {
        Map<Long, List<FormationPreference>> byStudent = indexByStudent(preferences);
        double total = 0.0;
        for (List<Long> team : teams) {
            total += scoreTeam(team, byStudent);
        }
        return total;
    }

    /**
     * Counts eligible students who submitted at least one preference and
     * satisfied none. Students without preferences are excluded.
     */
    public static int unsatisfiedPreferenceStudents(
            Collection<Long> eligibleStudentIds,
            Map<Long, Integer> teamOf,
            Map<Long, List<FormationPreference>> preferencesByStudent) {
        int count = 0;
        for (Long studentId : eligibleStudentIds) {
            List<FormationPreference> preferences =
                    preferencesByStudent.getOrDefault(studentId, List.of());
            if (preferences.isEmpty()) {
                continue;
            }
            Integer team = teamOf.get(studentId);
            boolean satisfied = false;
            if (team != null) {
                for (FormationPreference preference : preferences) {
                    if (team.equals(teamOf.get(preference.preferredStudentId()))) {
                        satisfied = true;
                        break;
                    }
                }
            }
            if (!satisfied) {
                count++;
            }
        }
        return count;
    }

    public static int unsatisfiedPreferenceStudents(
            Collection<Long> eligibleStudentIds,
            Map<Long, Integer> teamOf,
            List<FormationPreference> preferences) {
        return unsatisfiedPreferenceStudents(
                eligibleStudentIds, teamOf, indexByStudent(preferences));
    }

    /**
     * Counts eligible students who submitted no preference at all.
     */
    public static int studentsWithoutPreferences(
            Collection<Long> eligibleStudentIds,
            Map<Long, List<FormationPreference>> preferencesByStudent) {
        int count = 0;
        for (Long studentId : eligibleStudentIds) {
            if (preferencesByStudent.getOrDefault(studentId, List.of()).isEmpty()) {
                count++;
            }
        }
        return count;
    }

    public static int studentsWithoutPreferences(
            Collection<Long> eligibleStudentIds, List<FormationPreference> preferences) {
        return studentsWithoutPreferences(eligibleStudentIds, indexByStudent(preferences));
    }
}
