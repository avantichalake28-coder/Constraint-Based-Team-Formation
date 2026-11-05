package com.teamformation.backend.engine.constraint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pure tests for the Engine 2 feasibility checker. No Spring context or
 * database is involved.
 */
class FeasibilityCheckerTest {

    private static List<RosterStudentSkills> rosterOf(int count) {
        return IntStream.rangeClosed(1, count)
                .mapToObj(id -> new RosterStudentSkills(id, Map.of()))
                .toList();
    }

    private static ConstraintConfiguration config(
            List<ProhibitedPair> pairs,
            List<MandatorySkill> mandatory) {
        return new ConstraintConfiguration(1, pairs, mandatory);
    }

    @Test
    @DisplayName("A feasible population with no constraints is FEASIBLE and explains nothing")
    void check_feasibleWithoutConstraints() {
        FeasibilityReport report = FeasibilityChecker.check(
                10, 3, 5, 0, ConstraintConfiguration.empty(0), rosterOf(10), Map.of());

        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(report.eligibleStudents()).isEqualTo(10);
        assertThat(report.planner().feasible()).isTrue();
        assertThat(report.planner().teamSizes()).containsExactly(5, 5);
        assertThat(report.mandatorySkills()).isEmpty();
        assertThat(report.findings()).isEmpty();
        assertThat(report.disclaimer()).isEqualTo(FeasibilityChecker.DISCLAIMER);
    }

    @Test
    @DisplayName("Mandatory-skill shortage is critical and makes the result INFEASIBLE")
    void check_mandatorySkillShortage() {
        List<RosterStudentSkills> roster = List.of(
                new RosterStudentSkills(1, Map.of(7L, 5)),
                new RosterStudentSkills(2, Map.of(7L, 3)),
                new RosterStudentSkills(3, Map.of()),
                new RosterStudentSkills(4, Map.of()));
        ConstraintConfiguration configuration = config(
                List.of(), List.of(new MandatorySkill(7L, 4)));

        FeasibilityReport report = FeasibilityChecker.check(
                10, 3, 5, 0, configuration, roster, Map.of(7L, "Java"));

        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.INFEASIBLE);
        assertThat(report.mandatorySkills()).singleElement().satisfies(coverage -> {
            assertThat(coverage.skillId()).isEqualTo(7L);
            assertThat(coverage.skillName()).isEqualTo("Java");
            assertThat(coverage.minimumProficiency()).isEqualTo(4);
            assertThat(coverage.qualifiedStudents()).isEqualTo(1);
            assertThat(coverage.teamsNeeded()).isEqualTo(2);
            assertThat(coverage.shortage()).isTrue();
            assertThat(coverage.qualifiedStudents()).isLessThan(coverage.teamsNeeded());
        });
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("MANDATORY_SKILL_SHORTAGE");
            assertThat(finding.severity()).isEqualTo(FindingSeverity.CRITICAL);
            assertThat(finding.data()).containsEntry("qualifiedStudents", 1L);
            assertThat(finding.data()).containsEntry("teamsNeeded", 2);
        });
    }

    @Test
    @DisplayName("Enough students meeting the minimum make the mandatory skill feasible")
    void check_mandatorySkillSatisfied() {
        List<RosterStudentSkills> roster = List.of(
                new RosterStudentSkills(1, Map.of(7L, 4)),
                new RosterStudentSkills(2, Map.of(7L, 5)),
                new RosterStudentSkills(3, Map.of(7L, 3)),
                new RosterStudentSkills(4, Map.of()));
        ConstraintConfiguration configuration = config(
                List.of(), List.of(new MandatorySkill(7L, 4)));

        FeasibilityReport report = FeasibilityChecker.check(
                4, 2, 2, 0, configuration, roster, null);

        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(report.mandatorySkills()).singleElement().satisfies(coverage -> {
            assertThat(coverage.qualifiedStudents()).isEqualTo(2);
            assertThat(coverage.teamsNeeded()).isEqualTo(2);
            assertThat(coverage.shortage()).isFalse();
            assertThat(coverage.skillName()).isEqualTo("Skill 7");
        });
        assertThat(report.findings()).isEmpty();
    }

    @Test
    @DisplayName("An empty roster is INFEASIBLE with a critical team-size finding")
    void check_emptyRoster() {
        FeasibilityReport report = FeasibilityChecker.check(
                0, 2, 4, 1, ConstraintConfiguration.empty(0), List.of(), Map.of());

        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.INFEASIBLE);
        assertThat(report.eligibleStudents()).isZero();
        assertThat(report.planner().feasible()).isFalse();
        assertThat(report.planner().reasonCode()).isEqualTo(PlannerInfeasibilityReason.EMPTY_POPULATION);
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("TEAM_SIZE_INFEASIBLE");
            assertThat(finding.severity()).isEqualTo(FindingSeverity.CRITICAL);
            assertThat(finding.data()).containsEntry("reasonCode", "EMPTY_POPULATION");
        });
    }

    @Test
    @DisplayName("An off-roster prohibited pair is a warning and does not flip the result")
    void check_offRosterProhibitedPairIsWarningOnly() {
        ConstraintConfiguration configuration = config(
                List.of(new ProhibitedPair(1L, 999L)), List.of());

        FeasibilityReport report = FeasibilityChecker.check(
                6, 3, 5, 1, configuration, rosterOf(6), Map.of());

        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("PROHIBITED_PAIR_STUDENT_NOT_ON_ROSTER");
            assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING);
            assertThat(finding.data()).containsEntry("studentId1OnRoster", true);
            assertThat(finding.data()).containsEntry("studentId2OnRoster", false);
        });
    }

    @Test
    @DisplayName("A prohibited pair that cannot be separated is a warning, not an INFEASIBLE verdict")
    void check_nonSeparableProhibitedPairIsWarningOnly() {
        ConstraintConfiguration configuration = config(
                List.of(new ProhibitedPair(1L, 2L)), List.of());

        FeasibilityReport report = FeasibilityChecker.check(
                3, 3, 5, 1, configuration, rosterOf(3), Map.of());

        assertThat(report.planner().feasible()).isTrue();
        assertThat(report.planner().teamSizes()).containsExactly(3);
        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("PROHIBITED_PAIR_UNSATISFIABLE");
            assertThat(finding.severity()).isEqualTo(FindingSeverity.WARNING);
        });
    }

    @Test
    @DisplayName("An infeasible planner result is critical and carries advisory options")
    void check_plannerInfeasible() {
        FeasibilityReport report = FeasibilityChecker.check(
                21, 3, 5, 0, ConstraintConfiguration.empty(0), rosterOf(21), Map.of());

        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.INFEASIBLE);
        assertThat(report.planner().reasonCode())
                .isEqualTo(PlannerInfeasibilityReason.REMAINDER_NOT_PLACEABLE);
        assertThat(report.planner().suggestedOptions()).isNotEmpty();
        assertThat(report.findings()).singleElement().satisfies(finding -> {
            assertThat(finding.code()).isEqualTo("TEAM_SIZE_INFEASIBLE");
            assertThat(finding.severity()).isEqualTo(FindingSeverity.CRITICAL);
            assertThat((String) finding.data().get("reasonCode"))
                    .isEqualTo("REMAINDER_NOT_PLACEABLE");
            assertThat(finding.data().get("suggestedOptions")).isNotNull();
        });
    }

    @Test
    @DisplayName("A missing proficiency entry never satisfies a mandatory skill")
    void check_missingProficiencyDoesNotQualify() {
        List<RosterStudentSkills> roster = List.of(
                new RosterStudentSkills(1, Map.of(7L, 3)),
                new RosterStudentSkills(2, Map.of()),
                new RosterStudentSkills(3, Map.of()),
                new RosterStudentSkills(4, Map.of()));
        ConstraintConfiguration configuration = config(
                List.of(), List.of(new MandatorySkill(7L, 3)));

        FeasibilityReport report = FeasibilityChecker.check(
                4, 2, 2, 0, configuration, roster, Map.of(7L, "SQL"));

        assertThat(report.mandatorySkills()).singleElement()
                .satisfies(coverage -> assertThat(coverage.qualifiedStudents()).isEqualTo(1));
        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.INFEASIBLE);
    }

    @Test
    @DisplayName("Multiple critical findings keep every explanation")
    void check_multipleCriticalFindings() {
        List<RosterStudentSkills> roster = List.of(
                new RosterStudentSkills(1, Map.of(7L, 5)),
                new RosterStudentSkills(2, Map.of()));
        ConstraintConfiguration configuration = config(
                List.of(), List.of(new MandatorySkill(7L, 4)));

        FeasibilityReport report = FeasibilityChecker.check(
                2, 3, 5, 0, configuration, roster, Map.of(7L, "Java"));

        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.INFEASIBLE);
        assertThat(report.findings()).extracting(FeasibilityFinding::code)
                .contains("TEAM_SIZE_INFEASIBLE");
        assertThat(report.findings()).allMatch(finding ->
                finding.severity() == FindingSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Null configuration, roster or optional skill names are handled defensively")
    void check_nullArguments() {
        assertThatThrownBy(() -> FeasibilityChecker.check(
                5, 2, 4, 1, null, List.of(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("configuration");
        assertThatThrownBy(() -> FeasibilityChecker.check(
                5, 2, 4, 1, ConstraintConfiguration.empty(0), null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("roster");

        FeasibilityReport report = FeasibilityChecker.check(
                5, 2, 4, 1, ConstraintConfiguration.empty(0), rosterOf(5), null);
        assertThat(report.overallStatus()).isEqualTo(FeasibilityStatus.FEASIBLE);
    }
}
