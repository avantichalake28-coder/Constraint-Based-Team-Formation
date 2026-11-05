package com.teamformation.backend.engine.constraint;

import java.util.List;

/**
 * Result of the Engine 2 feasibility checks. The disclaimer is part of the
 * report because these checks never search for an actual team arrangement.
 */
public record FeasibilityReport(
        int eligibleStudents,
        TeamSizePlan planner,
        List<MandatorySkillCoverage> mandatorySkills,
        List<FeasibilityFinding> findings,
        FeasibilityStatus overallStatus,
        String disclaimer
) {

    public FeasibilityReport {
        planner = planner == null
                ? TeamSizePlan.infeasible(
                        PlannerInfeasibilityReason.EMPTY_POPULATION,
                        "No plan was produced.",
                        List.of())
                : planner;
        mandatorySkills = List.copyOf(mandatorySkills);
        findings = List.copyOf(findings);
    }
}
