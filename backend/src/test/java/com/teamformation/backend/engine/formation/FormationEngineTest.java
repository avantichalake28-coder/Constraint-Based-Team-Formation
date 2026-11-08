package com.teamformation.backend.engine.formation;

import com.teamformation.backend.engine.constraint.MandatorySkill;
import com.teamformation.backend.engine.constraint.ProhibitedPair;
import com.teamformation.backend.engine.constraint.TeamSizePlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormationEngineTest {

    private final FormationEngine engine = new FormationEngine();
    private final FormationPlanValidator validator = new FormationPlanValidator();
    private final FormationSearchLimits limits = new FormationSearchLimits(250_000, 100);

    @Test
    @DisplayName("The same input and seed produce the same plan")
    void form_isDeterministicForTheSameSeed() {
        FormationInput input = input(7L, List.of(3, 3), students(1, 2, 3, 4, 5, 6),
                List.of(new FormationPreference(1, 2, 1),
                        new FormationPreference(2, 1, 1),
                        new FormationPreference(3, 5, 2)),
                List.of(), List.of(), Map.of());

        FormationPlan first = engine.form(input, limits);
        FormationPlan second = engine.form(input, limits);

        assertThat(first).isEqualTo(second);
        validator.validate(input, first);
    }

    @Test
    @DisplayName("Mutual first preferences are both satisfied when they can share a team")
    void form_satisfiesMutualPreferences() {
        FormationInput input = input(11L, List.of(2, 2), students(1, 2, 3, 4),
                List.of(new FormationPreference(1, 2, 1),
                        new FormationPreference(2, 1, 1),
                        new FormationPreference(3, 4, 1)),
                List.of(), List.of(), Map.of());

        FormationPlan plan = engine.form(input, limits);

        assertThat(plan.totalPreferenceScore()).isEqualTo(3.0);
        assertThat(plan.unsatisfiedPreferenceStudents()).isZero();
        assertThat(plan.studentsWithoutPreferences()).isEqualTo(1);
        assertThat(teamContaining(plan, 1)).isEqualTo(teamContaining(plan, 2));
        assertThat(teamContaining(plan, 3)).isEqualTo(teamContaining(plan, 4));
        validator.validate(input, plan);
    }

    @Test
    @DisplayName("A prohibited pair never shares a candidate team")
    void form_separatesProhibitedPair() {
        FormationInput input = input(19L, List.of(2, 2), students(1, 2, 3, 4),
                List.of(), List.of(new ProhibitedPair(1L, 2L)), List.of(), Map.of());

        FormationPlan plan = engine.form(input, limits);

        assertThat(teamContaining(plan, 1)).isNotEqualTo(teamContaining(plan, 2));
        validator.validate(input, plan);
    }

    @Test
    @DisplayName("Every team covers a mandatory skill at the minimum proficiency")
    void form_coversMandatorySkillPerTeam() {
        Map<Long, Integer> firstSkills = Map.of(10L, 4);
        List<FormationStudent> students = List.of(
                new FormationStudent(1, firstSkills),
                new FormationStudent(2, Map.of()),
                new FormationStudent(3, Map.of(10L, 5)),
                new FormationStudent(4, Map.of()));
        FormationInput input = input(23L, List.of(2, 2), students, List.of(),
                List.of(), List.of(new MandatorySkill(10L, 4)), Map.of(10L, "Java"));

        FormationPlan plan = engine.form(input, limits);

        assertThat(teamContaining(plan, 1)).isNotEqualTo(teamContaining(plan, 3));
        validator.validate(input, plan);
    }

    @Test
    @DisplayName("An impossible hard constraint produces no partial plan")
    void form_withoutValidPlanThrows() {
        FormationInput input = input(29L, List.of(2), students(1, 2),
                List.of(), List.of(new ProhibitedPair(1L, 2L)), List.of(), Map.of());

        assertThatThrownBy(() -> engine.form(input, limits))
                .isInstanceOf(NoValidFormationException.class);
    }

    @Test
    @DisplayName("Reaching the node budget stops the search without a partial plan")
    void form_stopsAtNodeBudget() {
        FormationInput input = input(31L, List.of(2, 2), students(1, 2, 3, 4),
                List.of(), List.of(), List.of(), Map.of());
        FormationSearchLimits tiny = new FormationSearchLimits(1, 100);

        assertThatThrownBy(() -> engine.form(input, tiny))
                .isInstanceOf(FormationSearchLimitReachedException.class);
    }

    @Test
    @DisplayName("Teams are ordered by descending size and named Team 01, Team 02")
    void form_ordersAndNamesTeamsDeterministically() {
        FormationInput input = input(37L, List.of(3, 2), students(1, 2, 3, 4, 5),
                List.of(), List.of(), List.of(), Map.of());

        FormationPlan plan = engine.form(input, limits);

        assertThat(plan.teamCount()).isEqualTo(2);
        assertThat(plan.teams().get(0).teamName()).isEqualTo("Team 01");
        assertThat(plan.teams().get(1).teamName()).isEqualTo("Team 02");
        assertThat(plan.teams().get(0).ordinal()).isZero();
        assertThat(plan.teams().get(1).ordinal()).isEqualTo(1);
        assertThat(plan.teams().get(0).plannedSize()).isEqualTo(3);
        assertThat(plan.teams().get(1).plannedSize()).isEqualTo(2);
        validator.validate(input, plan);
    }

    private FormationInput input(
            long seed,
            List<Integer> sizes,
            List<FormationStudent> students,
            List<FormationPreference> preferences,
            List<ProhibitedPair> prohibitedPairs,
            List<MandatorySkill> mandatorySkills,
            Map<Long, String> skillNames) {
        return new FormationInput(
                1L,
                TeamSizePlan.feasible(sizes, false),
                2,
                sizes.stream().mapToInt(Integer::intValue).max().orElse(2),
                0,
                0,
                seed,
                "3.0.0",
                students,
                preferences,
                prohibitedPairs,
                mandatorySkills,
                skillNames);
    }

    private static List<FormationStudent> students(long... ids) {
        return java.util.Arrays.stream(ids)
                .mapToObj(id -> new FormationStudent(id, Map.of()))
                .toList();
    }

    private static int teamContaining(FormationPlan plan, long studentId) {
        for (int index = 0; index < plan.teams().size(); index++) {
            if (plan.teams().get(index).memberIds().contains(studentId)) {
                return index;
            }
        }
        throw new AssertionError("Student " + studentId + " is not assigned");
    }
}
