package com.teamformation.backend.engine.constraint;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Pure feasibility checks for Engine 2. It reads only plain inputs and never
 * searches for a team arrangement, so a FEASIBLE result means only that the
 * checked conditions did not prove infeasibility.
 */
public final class FeasibilityChecker {

    public static final String DISCLAIMER =
            "These checks verify team-size divisibility, mandatory-skill availability and "
                    + "prohibited-pair configuration. Passing them does not guarantee that a "
                    + "valid team arrangement exists.";

    private FeasibilityChecker() {
    }

    /**
     * Checks the current configuration against the eligible roster.
     *
     * @param eligibleStudents number of eligible students
     * @param min              smallest allowed regular team size
     * @param max              largest allowed regular team size
     * @param slack            extra students one full team may absorb
     * @param configuration    current typed constraint configuration
     * @param roster           skill snapshot of every eligible student
     * @param skillNames       known skill names keyed by skill id (may be partial)
     */
    public static FeasibilityReport check(
            int eligibleStudents,
            int min,
            int max,
            int slack,
            ConstraintConfiguration configuration,
            List<RosterStudentSkills> roster,
            Map<Long, String> skillNames) {
        Objects.requireNonNull(configuration, "configuration is required");
        Objects.requireNonNull(roster, "roster is required");
        skillNames = skillNames == null ? Map.of() : Map.copyOf(skillNames);

        TeamSizePlan plan = TeamSizePlanner.plan(eligibleStudents, min, max, slack);
        List<FeasibilityFinding> findings = new ArrayList<>();
        List<MandatorySkillCoverage> mandatoryCoverage = new ArrayList<>();

        if (!plan.feasible()) {
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("reasonCode", plan.reasonCode().name());
            data.put("suggestedOptions", plan.suggestedOptions());
            findings.add(new FeasibilityFinding(
                    "TEAM_SIZE_INFEASIBLE",
                    FindingSeverity.CRITICAL,
                    plan.explanation(),
                    data));
        }

        int teamsNeeded = plan.feasible() ? plan.teamSizes().size() : 0;
        for (MandatorySkill mandatorySkill : configuration.mandatorySkills()) {
            long qualified = roster.stream()
                    .filter(student -> meetsMinimum(student, mandatorySkill))
                    .count();
            boolean shortage = plan.feasible() && qualified < teamsNeeded;
            String skillName = skillNames.getOrDefault(
                    mandatorySkill.skillId(), "Skill " + mandatorySkill.skillId());
            String message = shortage
                    ? skillName + " needs at least " + teamsNeeded
                            + " student(s) with proficiency " + mandatorySkill.minimumProficiency()
                            + " or higher, but only " + qualified + " qualify."
                    : skillName + " has " + qualified
                            + " student(s) at proficiency " + mandatorySkill.minimumProficiency()
                            + " or higher for " + teamsNeeded + " team(s).";

            mandatoryCoverage.add(new MandatorySkillCoverage(
                    mandatorySkill.skillId(),
                    skillName,
                    mandatorySkill.minimumProficiency(),
                    qualified,
                    teamsNeeded,
                    shortage,
                    message));

            if (shortage) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("skillId", mandatorySkill.skillId());
                data.put("skillName", skillName);
                data.put("minimumProficiency", mandatorySkill.minimumProficiency());
                data.put("qualifiedStudents", qualified);
                data.put("teamsNeeded", teamsNeeded);
                findings.add(new FeasibilityFinding(
                        "MANDATORY_SKILL_SHORTAGE",
                        FindingSeverity.CRITICAL,
                        message,
                        data));
            }
        }

        Set<Long> rosterIds = new LinkedHashSet<>();
        roster.forEach(student -> rosterIds.add(student.studentId()));
        for (ProhibitedPair pair : configuration.prohibitedPairs()) {
            boolean firstOnRoster = rosterIds.contains(pair.studentId1());
            boolean secondOnRoster = rosterIds.contains(pair.studentId2());
            if (!firstOnRoster || !secondOnRoster) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("studentId1", pair.studentId1());
                data.put("studentId2", pair.studentId2());
                data.put("studentId1OnRoster", firstOnRoster);
                data.put("studentId2OnRoster", secondOnRoster);
                findings.add(new FeasibilityFinding(
                        "PROHIBITED_PAIR_STUDENT_NOT_ON_ROSTER",
                        FindingSeverity.WARNING,
                        "Prohibited pair " + pair.studentId1() + " / " + pair.studentId2()
                                + " references a student who is not on the project roster.",
                        data));
            } else if (plan.feasible() && teamsNeeded < 2) {
                Map<String, Object> data = new LinkedHashMap<>();
                data.put("studentId1", pair.studentId1());
                data.put("studentId2", pair.studentId2());
                data.put("teamsNeeded", teamsNeeded);
                findings.add(new FeasibilityFinding(
                        "PROHIBITED_PAIR_UNSATISFIABLE",
                        FindingSeverity.WARNING,
                        "Students " + pair.studentId1() + " and " + pair.studentId2()
                                + " must never share a team, but fewer than two teams are possible, "
                                + "so the pair cannot be separated.",
                        data));
            }
        }

        boolean hasError = findings.stream()
                .anyMatch(finding -> finding.severity() == FindingSeverity.CRITICAL);
        FeasibilityStatus status = (!plan.feasible() || hasError)
                ? FeasibilityStatus.INFEASIBLE
                : FeasibilityStatus.FEASIBLE;

        return new FeasibilityReport(
                eligibleStudents,
                plan,
                mandatoryCoverage,
                findings,
                status,
                DISCLAIMER);
    }

    private static boolean meetsMinimum(RosterStudentSkills student, MandatorySkill mandatorySkill) {
        Integer proficiency = student.proficiencyBySkillId().get(mandatorySkill.skillId());
        return proficiency != null && proficiency >= mandatorySkill.minimumProficiency();
    }
}
