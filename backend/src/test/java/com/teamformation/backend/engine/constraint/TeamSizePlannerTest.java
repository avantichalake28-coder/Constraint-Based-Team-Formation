package com.teamformation.backend.engine.constraint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure tests for the Engine 2 team-size planner. No Spring context or database
 * is involved.
 */
class TeamSizePlannerTest {

    static Stream<Arguments> feasibleCases() {
        return Stream.of(
                Arguments.of(5, 3, 5, 0, List.of(5), false),
                Arguments.of(10, 3, 5, 0, List.of(5, 5), false),
                Arguments.of(13, 3, 5, 0, List.of(5, 5, 3), false),
                Arguments.of(8, 3, 5, 1, List.of(5, 3), false),
                Arguments.of(6, 3, 5, 2, List.of(6), true),
                Arguments.of(11, 3, 5, 2, List.of(6, 5), true),
                Arguments.of(4, 2, 2, 0, List.of(2, 2), false),
                Arguments.of(5, 4, 4, 1, List.of(5), true),
                Arguments.of(9, 4, 5, 1, List.of(5, 4), false),
                Arguments.of(20, 10, 10, 0, List.of(10, 10), false),
                Arguments.of(4, 3, 5, 2, List.of(4), false));
    }

    @ParameterizedTest(name = "N={0}, min={1}, max={2}, slack={3} -> {4}")
    @MethodSource("feasibleCases")
    @DisplayName("Feasible populations produce descending teams that sum to the population")
    void plan_feasibleCases(
            int population, int min, int max, int slack,
            List<Integer> expectedSizes, boolean expectedSlackUsed) {
        TeamSizePlan plan = TeamSizePlanner.plan(population, min, max, slack);

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.teamSizes()).containsExactlyElementsOf(expectedSizes);
        assertThat(plan.slackUsed()).isEqualTo(expectedSlackUsed);
        assertThat(plan.reasonCode()).isNull();
        assertThat(plan.explanation()).isNull();
        assertThat(plan.teamSizes()).isSortedAccordingTo((left, right) -> right - left);
        assertThat(plan.teamSizes().stream().mapToInt(Integer::intValue).sum())
                .isEqualTo(population);
        assertThat(plan.teamCount()).isEqualTo(expectedSizes.size());
    }

    static Stream<Arguments> defaultProjectCases() {
        return Stream.of(
                Arguments.of(2, List.of(2)),
                Arguments.of(3, List.of(3)),
                Arguments.of(4, List.of(4)),
                Arguments.of(5, List.of(5)),
                Arguments.of(6, List.of(4, 2)),
                Arguments.of(7, List.of(4, 3)),
                Arguments.of(8, List.of(4, 4)),
                Arguments.of(9, List.of(5, 4)),
                Arguments.of(10, List.of(4, 4, 2)),
                Arguments.of(11, List.of(4, 4, 3)),
                Arguments.of(12, List.of(4, 4, 4)),
                Arguments.of(13, List.of(5, 4, 4)),
                Arguments.of(14, List.of(4, 4, 4, 2)),
                Arguments.of(15, List.of(4, 4, 4, 3)),
                Arguments.of(16, List.of(4, 4, 4, 4)),
                Arguments.of(17, List.of(5, 4, 4, 4)),
                Arguments.of(18, List.of(4, 4, 4, 4, 2)),
                Arguments.of(19, List.of(4, 4, 4, 4, 3)),
                Arguments.of(20, List.of(4, 4, 4, 4, 4)),
                Arguments.of(21, List.of(5, 4, 4, 4, 4)),
                Arguments.of(22, List.of(4, 4, 4, 4, 4, 2)),
                Arguments.of(40, List.of(4, 4, 4, 4, 4, 4, 4, 4, 4, 4)));
    }

    @ParameterizedTest(name = "default project settings: N={0} -> {1}")
    @MethodSource("defaultProjectCases")
    @DisplayName("The default project settings (min 2, max 4, slack 1) produce the documented sizes")
    void plan_defaultProjectSettings(int population, List<Integer> expectedSizes) {
        TeamSizePlan plan = TeamSizePlanner.plan(population, 2, 4, 1);

        assertThat(plan.feasible()).isTrue();
        assertThat(plan.teamSizes()).containsExactlyElementsOf(expectedSizes);
        assertThat(plan.teamSizes().stream().mapToInt(Integer::intValue).sum()).isEqualTo(population);
    }

    @Test
    @DisplayName("Default project settings cannot place a single student")
    void plan_defaultProjectSettingsRejectOneStudent() {
        TeamSizePlan plan = TeamSizePlanner.plan(1, 2, 4, 1);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.reasonCode()).isEqualTo(PlannerInfeasibilityReason.BELOW_MINIMUM);
    }

    @Test
    @DisplayName("With zero slack, 21 and 5 are infeasible but carry valid alternatives")
    void plan_zeroSlackInfeasibleWithAlternatives() {
        TeamSizePlan twentyOne = TeamSizePlanner.plan(21, 2, 4, 0);
        TeamSizePlan five = TeamSizePlanner.plan(5, 2, 4, 0);

        assertThat(twentyOne.feasible()).isFalse();
        assertThat(twentyOne.reasonCode()).isEqualTo(PlannerInfeasibilityReason.REMAINDER_NOT_PLACEABLE);
        assertThat(twentyOne.suggestedOptions()).isNotEmpty();
        assertThat(twentyOne.suggestedOptions()).allSatisfy(option -> {
            assertThat(option.stream().mapToInt(Integer::intValue).sum()).isEqualTo(21);
            assertThat(option).allMatch(size -> size >= 2 && size <= 4);
        });

        assertThat(five.feasible()).isFalse();
        assertThat(five.suggestedOptions()).contains(List.of(3, 2));
        assertThat(five.suggestedOptions()).allMatch(option -> option.stream().mapToInt(Integer::intValue).sum() == 5);
    }

    @Test
    @DisplayName("An empty population is infeasible with EMPTY_POPULATION")
    void plan_emptyPopulation() {
        TeamSizePlan plan = TeamSizePlanner.plan(0, 2, 4, 1);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.reasonCode()).isEqualTo(PlannerInfeasibilityReason.EMPTY_POPULATION);
        assertThat(plan.teamSizes()).isEmpty();
        assertThat(plan.suggestedOptions()).isEmpty();
        assertThat(plan.explanation()).contains("no eligible students");
    }

    @Test
    @DisplayName("A population below the minimum is infeasible with BELOW_MINIMUM")
    void plan_belowMinimum() {
        TeamSizePlan plan = TeamSizePlanner.plan(1, 3, 5, 0);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.reasonCode()).isEqualTo(PlannerInfeasibilityReason.BELOW_MINIMUM);
        assertThat(plan.teamSizes()).isEmpty();
        assertThat(plan.suggestedOptions()).isEmpty();
    }

    @Test
    @DisplayName("A remainder that cannot be placed is infeasible and suggests valid alternatives")
    void plan_remainderNotPlaceableSuggestsAlternatives() {
        TeamSizePlan plan = TeamSizePlanner.plan(21, 3, 5, 0);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.reasonCode()).isEqualTo(PlannerInfeasibilityReason.REMAINDER_NOT_PLACEABLE);
        assertThat(plan.teamSizes()).isEmpty();
        assertThat(plan.suggestedOptions())
                .isNotEmpty()
                .contains(List.of(5, 5, 5, 3, 3));
        assertThat(plan.suggestedOptions()).allSatisfy(option -> {
            assertThat(option.stream().mapToInt(Integer::intValue).sum()).isEqualTo(21);
            assertThat(option).allMatch(size -> size >= 3 && size <= 5);
        });
        assertThat(plan.explanation()).contains("remainder of 1");
    }

    @Test
    @DisplayName("Without slack the excess cannot be absorbed even when another split exists")
    void plan_withoutSlackDoesNotRebalanceSilently() {
        TeamSizePlan plan = TeamSizePlanner.plan(7, 3, 5, 0);

        assertThat(plan.feasible()).isFalse();
        assertThat(plan.reasonCode()).isEqualTo(PlannerInfeasibilityReason.REMAINDER_NOT_PLACEABLE);
        assertThat(plan.suggestedOptions()).contains(List.of(4, 3));
    }

    @Test
    @DisplayName("min == max == 2 is feasible only for even populations")
    void plan_fixedPairSize() {
        assertThat(TeamSizePlanner.plan(6, 2, 2, 0).teamSizes())
                .containsExactly(2, 2, 2);
        TeamSizePlan odd = TeamSizePlanner.plan(5, 2, 2, 0);
        assertThat(odd.feasible()).isFalse();
        assertThat(odd.reasonCode()).isEqualTo(PlannerInfeasibilityReason.REMAINDER_NOT_PLACEABLE);
        assertThat(odd.suggestedOptions()).isEmpty();
    }

    @Test
    @DisplayName("Invalid planner inputs are rejected")
    void plan_rejectsInvalidInputs() {
        assertThatThrownBy(() -> TeamSizePlanner.plan(-1, 2, 4, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("population");
        assertThatThrownBy(() -> TeamSizePlanner.plan(5, 1, 4, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("min team size");
        assertThatThrownBy(() -> TeamSizePlanner.plan(5, 5, 4, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("less than or equal");
        assertThatThrownBy(() -> TeamSizePlanner.plan(5, 2, 4, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slack");
    }

    @Test
    @DisplayName("Every population from 0 to 300 with defaults keeps the documented invariants and is deterministic")
    void plan_invariantsForZeroToThreeHundredWithDefaults() {
        assertInvariants(2, 4, 1);
    }

    @Test
    @DisplayName("Every population from 0 to 300 with min 3, max 5, slack 2 keeps the invariants")
    void plan_invariantsForThreeFiveTwo() {
        assertInvariants(3, 5, 2);
    }

    @Test
    @DisplayName("Every population from 0 to 300 with min 2, max 2, slack 0 keeps the invariants")
    void plan_invariantsForTwoTwoZero() {
        assertInvariants(2, 2, 0);
    }

    private static void assertInvariants(int min, int max, int slack) {
        IntStream.rangeClosed(0, 300).forEach(population -> {
            TeamSizePlan first = TeamSizePlanner.plan(population, min, max, slack);
            TeamSizePlan second = TeamSizePlanner.plan(population, min, max, slack);

            assertThat(TeamSizePlanner.sameSizes(first, second)).isTrue();
            assertThat(first).isEqualTo(second);
            assertThat(first.reasonCode() == null).isEqualTo(first.feasible());

            if (first.feasible()) {
                assertThat(first.teamSizes()).isNotEmpty();
                assertThat(first.teamSizes().stream().mapToInt(Integer::intValue).sum())
                        .isEqualTo(population);
                assertThat(first.teamSizes()).allMatch(size -> size >= min);
                assertThat(first.teamSizes()).allMatch(size -> size <= max + slack);
                assertThat(first.teamSizes().stream().filter(size -> size > max).count())
                        .isLessThanOrEqualTo(1);
                assertThat(first.teamSizes())
                        .isSortedAccordingTo((left, right) -> right - left);
                assertThat(first.suggestedOptions()).isEmpty();
            } else {
                assertThat(first.teamSizes()).isEmpty();
                assertThat(first.reasonCode()).isNotNull();
            }
        });
    }

    @Test
    @DisplayName("Suggestions never exceed one over-max team and never apply themselves")
    void plan_suggestionsAreValidButAdvisory() {
        TeamSizePlan plan = TeamSizePlanner.plan(22, 3, 5, 1);

        assertThat(plan.feasible()).isFalse();
        for (List<Integer> option : plan.suggestedOptions()) {
            assertThat(option.stream().mapToInt(Integer::intValue).sum()).isEqualTo(22);
            assertThat(option).allMatch(size -> size >= 3);
            assertThat(option.stream().filter(size -> size > 5).count()).isLessThanOrEqualTo(1);
            assertThat(option).allMatch(size -> size <= 6);
        }
    }
}
