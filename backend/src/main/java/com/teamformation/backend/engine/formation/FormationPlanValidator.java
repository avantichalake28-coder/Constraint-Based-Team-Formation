package com.teamformation.backend.engine.formation;

import com.teamformation.backend.engine.constraint.MandatorySkill;
import com.teamformation.backend.engine.constraint.ProhibitedPair;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure structural and hard-constraint validation of a generated plan.
 *
 * <p>The validator never repairs a plan. A violation is reported as a safe,
 * human-readable description and the caller must discard the plan.</p>
 */
@Component
public class FormationPlanValidator {

    public void validate(FormationInput input, FormationPlan plan) {
        Objects.requireNonNull(input, "input is required");
        Objects.requireNonNull(plan, "plan is required");

        List<String> violations = new ArrayList<>();
        Map<Long, FormationStudent> studentById = new HashMap<>();
        for (FormationStudent student : input.students()) {
            studentById.put(student.studentId(), student);
        }

        if (plan.seed() != input.seed()) {
            violations.add("the plan seed does not match the run seed");
        }
        if (!Objects.equals(plan.algorithmVersion(), input.algorithmVersion())) {
            violations.add("the plan algorithm version does not match the run algorithm version");
        }

        List<Integer> plannedSizes = input.teamSizes();
        if (plannedSizes.size() != plan.teamCount()) {
            violations.add("the plan has " + plan.teamCount() + " teams but the planner produced "
                    + plannedSizes.size());
        }

        Set<Long> assigned = new HashSet<>();
        Map<Long, Integer> teamOf = new HashMap<>();
        double computedTotal = 0.0;

        for (int index = 0; index < plan.teams().size(); index++) {
            FormationTeam team = plan.teams().get(index);
            if (team.ordinal() != index) {
                violations.add("team ordinal " + team.ordinal() + " is not at position " + index);
            }
            if (index < plannedSizes.size() && team.plannedSize() != plannedSizes.get(index)) {
                violations.add("team " + team.teamName() + " has planned size " + team.plannedSize()
                        + " but the planner produced " + plannedSizes.get(index));
            }
            if (team.actualSize() != team.plannedSize()) {
                violations.add("team " + team.teamName() + " has " + team.actualSize()
                        + " members but its planned size is " + team.plannedSize());
            }

            for (Long memberId : team.memberIds()) {
                if (memberId == null) {
                    violations.add("team " + team.teamName() + " contains a null member id");
                    continue;
                }
                if (!studentById.containsKey(memberId)) {
                    violations.add("team " + team.teamName() + " contains student " + memberId
                            + " who is not eligible");
                }
                if (!assigned.add(memberId)) {
                    violations.add("student " + memberId + " appears in more than one team");
                }
                teamOf.put(memberId, index);
            }

            double computedTeamScore = CompatibilityScorer.scoreTeam(
                    team.memberIds(), input.preferences());
            if (Double.compare(computedTeamScore, team.preferenceScore()) != 0) {
                violations.add("team " + team.teamName() + " records score "
                        + team.preferenceScore() + " but the directed preferences total "
                        + computedTeamScore);
            }
            computedTotal += computedTeamScore;
        }

        if (assigned.size() != input.students().size()) {
            violations.add("the plan assigns " + assigned.size() + " students but the run has "
                    + input.students().size() + " eligible students");
        }

        validateCanonicalOrder(plan, violations);
        validateUniqueNames(plan, violations);
        validateProhibitedPairs(input, teamOf, violations);
        validateMandatorySkills(input, studentById, plan, violations);

        if (Double.compare(computedTotal, plan.totalPreferenceScore()) != 0) {
            violations.add("the plan records total score " + plan.totalPreferenceScore()
                    + " but the team scores total " + computedTotal);
        }

        Set<Long> eligibleIds = studentById.keySet();
        int computedUnsatisfied = CompatibilityScorer.unsatisfiedPreferenceStudents(
                eligibleIds, teamOf, input.preferences());
        if (computedUnsatisfied != plan.unsatisfiedPreferenceStudents()) {
            violations.add("the plan records " + plan.unsatisfiedPreferenceStudents()
                    + " unsatisfied students but the directed preferences give "
                    + computedUnsatisfied);
        }
        int computedWithout = CompatibilityScorer.studentsWithoutPreferences(
                eligibleIds, input.preferences());
        if (computedWithout != plan.studentsWithoutPreferences()) {
            violations.add("the plan records " + plan.studentsWithoutPreferences()
                    + " students without preferences but the input has " + computedWithout);
        }
        long computedTieBreak = DeterministicTieBreaker.planTieBreak(
                input.seed(), teamOf, eligibleIds);
        if (computedTieBreak != plan.deterministicTieBreak()) {
            violations.add("the plan tie-break value does not match the seed-derived value");
        }

        if (!violations.isEmpty()) {
            throw new FormationValidationException(violations);
        }
    }

    private void validateCanonicalOrder(FormationPlan plan, List<String> violations) {
        record Snapshot(int position, int size, long smallestMember) {
        }
        List<Snapshot> sorted = new ArrayList<>(plan.teams().size());
        for (int index = 0; index < plan.teams().size(); index++) {
            FormationTeam team = plan.teams().get(index);
            long smallest = team.memberIds().stream()
                    .filter(Objects::nonNull)
                    .mapToLong(Long::longValue)
                    .min()
                    .orElse(Long.MAX_VALUE);
            sorted.add(new Snapshot(index, team.actualSize(), smallest));
        }
        List<Integer> actual = sorted.stream().map(Snapshot::position).toList();
        sorted.sort(Comparator
                .comparingInt(Snapshot::size).reversed()
                .thenComparingLong(Snapshot::smallestMember)
                .thenComparingInt(Snapshot::position));
        List<Integer> expected = sorted.stream().map(Snapshot::position).toList();
        if (!actual.equals(expected)) {
            violations.add("teams are not ordered by descending size and then smallest student id");
        }
    }

    private void validateUniqueNames(FormationPlan plan, List<String> violations) {
        List<String> names = plan.teams().stream().map(FormationTeam::teamName).toList();
        if (names.stream().anyMatch(name -> name == null || name.isBlank())) {
            violations.add("every team needs a non-blank name");
        }
        if (new HashSet<>(names).size() != names.size()) {
            violations.add("team names are not unique");
        }
        List<String> expectedNames = TeamNameGenerator.generate(plan.teamCount());
        if (!expectedNames.equals(names)) {
            violations.add("team names do not follow the Team 01, Team 02 convention");
        }
    }

    private void validateProhibitedPairs(
            FormationInput input, Map<Long, Integer> teamOf, List<String> violations) {
        for (ProhibitedPair pair : input.prohibitedPairs()) {
            Integer firstTeam = teamOf.get(pair.studentId1());
            Integer secondTeam = teamOf.get(pair.studentId2());
            if (firstTeam != null && firstTeam.equals(secondTeam)) {
                violations.add("prohibited students " + pair.studentId1() + " and "
                        + pair.studentId2() + " share a team");
            }
        }
    }

    private void validateMandatorySkills(
            FormationInput input,
            Map<Long, FormationStudent> studentById,
            FormationPlan plan,
            List<String> violations) {
        for (FormationTeam team : plan.teams()) {
            for (MandatorySkill mandatorySkill : input.mandatorySkills()) {
                boolean covered = team.memberIds().stream()
                        .map(studentById::get)
                        .filter(Objects::nonNull)
                        .anyMatch(student -> student.meets(
                                mandatorySkill.skillId(), mandatorySkill.minimumProficiency()));
                if (!covered) {
                    String skillName = input.skillNames().getOrDefault(
                            mandatorySkill.skillId(), "Skill " + mandatorySkill.skillId());
                    violations.add("team " + team.teamName() + " does not contain "
                            + skillName + " at proficiency "
                            + mandatorySkill.minimumProficiency() + " or higher");
                }
            }
        }
    }
}
