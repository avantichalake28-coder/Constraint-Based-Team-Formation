package com.teamformation.backend.engine.formation;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CompatibilityScorerTest {

    @Test
    @DisplayName("Rank 1 is 1.0, rank 2 is 0.5, and mutual preferences count twice")
    void scoreTeam_directedRanksAndMutualPreferences() {
        List<FormationPreference> preferences = List.of(
                new FormationPreference(1, 2, 1),
                new FormationPreference(2, 1, 2),
                new FormationPreference(3, 4, 1));

        double score = CompatibilityScorer.scoreTeam(List.of(1L, 2L, 3L), preferences);

        assertThat(score).isEqualTo(1.5);
    }

    @Test
    @DisplayName("A preference whose target is in another team is not satisfied")
    void scoreTeam_ignoresCrossTeamPreference() {
        List<FormationPreference> preferences = List.of(
                new FormationPreference(1, 2, 1),
                new FormationPreference(1, 3, 2));

        assertThat(CompatibilityScorer.scoreTeam(List.of(1L, 2L), preferences))
                .isEqualTo(1.0);
        assertThat(CompatibilityScorer.scoreTeam(List.of(1L, 3L), preferences))
                .isEqualTo(0.5);
    }

    @Test
    @DisplayName("The plan score is the sum of every team score")
    void scorePlan_sumsTeams() {
        List<FormationPreference> preferences = List.of(
                new FormationPreference(1, 2, 1),
                new FormationPreference(3, 4, 2));

        double score = CompatibilityScorer.scorePlan(
                List.of(List.of(1L, 2L), List.of(3L, 4L)), preferences);

        assertThat(score).isEqualTo(1.5);
    }

    @Test
    @DisplayName("Unsatisfied counts exclude students without preferences")
    void unsatisfied_excludesStudentsWithoutPreferences() {
        List<FormationPreference> preferences = List.of(
                new FormationPreference(1, 2, 1),
                new FormationPreference(3, 1, 1));
        Map<Long, Integer> teamOf = Map.of(1L, 0, 2L, 0, 3L, 1, 4L, 1);

        int unsatisfied = CompatibilityScorer.unsatisfiedPreferenceStudents(
                List.of(1L, 2L, 3L, 4L), teamOf, preferences);

        assertThat(unsatisfied).isEqualTo(1);
        assertThat(CompatibilityScorer.studentsWithoutPreferences(
                List.of(1L, 2L, 3L, 4L), preferences)).isEqualTo(2);
    }

    @Test
    @DisplayName("Unsatisfied counts every requester once even with several preferences")
    void unsatisfied_countsEachStudentOnce() {
        List<FormationPreference> preferences = List.of(
                new FormationPreference(1, 2, 1),
                new FormationPreference(1, 3, 2));
        Map<Long, Integer> teamOf = Map.of(1L, 0, 2L, 1, 3L, 1);

        assertThat(CompatibilityScorer.unsatisfiedPreferenceStudents(
                List.of(1L, 2L, 3L), teamOf, preferences)).isEqualTo(1);
    }
}
