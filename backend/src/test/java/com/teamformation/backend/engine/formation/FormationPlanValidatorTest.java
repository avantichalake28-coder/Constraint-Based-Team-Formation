package com.teamformation.backend.engine.formation;

import com.teamformation.backend.engine.constraint.MandatorySkill;
import com.teamformation.backend.engine.constraint.ProhibitedPair;
import com.teamformation.backend.engine.constraint.TeamSizePlan;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FormationPlanValidatorTest {

    private final FormationPlanValidator validator = new FormationPlanValidator();

    @Test
    @DisplayName("A structurally valid plan passes every invariant")
    void validate_acceptsValidPlan() {
        FormationInput input = input(List.of(), List.of(), Map.of());
        FormationPlan plan = plan("Team 01", "Team 02", 2.0);
        assertThatCode(() -> validator.validate(input, plan)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("A student may not appear in two teams")
    void validate_rejectsDuplicateStudent() {
        FormationInput input = input(List.of(), List.of(), Map.of());
        FormationPlan plan = new FormationPlan(
                List.of(
                        new FormationTeam(0, "Team 01", 2, List.of(1L, 2L), 2.0),
                        new FormationTeam(1, "Team 02", 2, List.of(2L, 3L), 0.0)),
                2.0, 0, 2, "3.0.0", 5L, 0L);

        assertThatThrownBy(() -> validator.validate(input, plan))
                .isInstanceOf(FormationValidationException.class)
                .hasMessageContaining("more than one team");
    }

    @Test
    @DisplayName("A persisted score must equal the directed preference score")
    void validate_rejectsWrongPreferenceScore() {
        FormationInput input = input(List.of(), List.of(), Map.of());
        FormationPlan plan = plan("Team 01", "Team 02", 99.0);

        assertThatThrownBy(() -> validator.validate(input, plan))
                .isInstanceOf(FormationValidationException.class)
                .hasMessageContaining("records total score");
    }

    @Test
    @DisplayName("A prohibited pair in one team is rejected")
    void validate_rejectsProhibitedPair() {
        FormationInput input = input(
                List.of(new ProhibitedPair(3L, 4L)), List.of(), Map.of());
        FormationPlan plan = plan("Team 01", "Team 02", 2.0);

        assertThatThrownBy(() -> validator.validate(input, plan))
                .isInstanceOf(FormationValidationException.class)
                .hasMessageContaining("share a team");
    }

    @Test
    @DisplayName("A team without a mandatory skill at the minimum proficiency is rejected")
    void validate_rejectsMissingMandatorySkill() {
        FormationInput input = input(
                List.of(), List.of(new MandatorySkill(10L, 3)), Map.of(10L, "Java"));
        FormationPlan plan = plan("Team 01", "Team 02", 2.0);

        assertThatThrownBy(() -> validator.validate(input, plan))
                .isInstanceOf(FormationValidationException.class)
                .hasMessageContaining("does not contain Java");
    }

    @Test
    @DisplayName("Non-canonical team names are rejected")
    void validate_rejectsWrongNames() {
        FormationInput input = input(List.of(), List.of(), Map.of());
        FormationPlan plan = plan("Alpha", "Beta", 2.0);

        assertThatThrownBy(() -> validator.validate(input, plan))
                .isInstanceOf(FormationValidationException.class)
                .hasMessageContaining("Team 01");
    }

    private FormationInput input(
            List<ProhibitedPair> prohibitedPairs,
            List<MandatorySkill> mandatorySkills,
            Map<Long, String> skillNames) {
        return new FormationInput(
                1L,
                TeamSizePlan.feasible(List.of(2, 2), false),
                2,
                2,
                0,
                0,
                5L,
                "3.0.0",
                List.of(
                        new FormationStudent(1, Map.of(10L, 4)),
                        new FormationStudent(2, Map.of()),
                        new FormationStudent(3, Map.of()),
                        new FormationStudent(4, Map.of())),
                List.of(
                        new FormationPreference(1, 2, 1),
                        new FormationPreference(2, 1, 1)),
                prohibitedPairs,
                mandatorySkills,
                skillNames);
    }

    private FormationPlan plan(String firstName, String secondName, double totalScore) {
        Map<Long, Integer> teamOf = Map.of(1L, 0, 2L, 0, 3L, 1, 4L, 1);
        long tieBreak = DeterministicTieBreaker.planTieBreak(
                5L, teamOf, List.of(1L, 2L, 3L, 4L));
        return new FormationPlan(
                List.of(
                        new FormationTeam(0, firstName, 2, List.of(1L, 2L), 2.0),
                        new FormationTeam(1, secondName, 2, List.of(3L, 4L), 0.0)),
                totalScore,
                0,
                2,
                "3.0.0",
                5L,
                tieBreak);
    }
}
