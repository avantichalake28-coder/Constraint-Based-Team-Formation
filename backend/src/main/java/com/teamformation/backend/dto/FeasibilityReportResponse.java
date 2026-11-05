package com.teamformation.backend.dto;

import java.util.List;

/**
 * Result of POST /api/v1/projects/{id}/feasibility. It changes nothing and
 * never searches for an arrangement.
 */
public record FeasibilityReportResponse(
        Long projectId,
        int eligibleStudents,
        int constraintVersion,
        int minTeamSize,
        int maxTeamSize,
        int slack,
        String status,
        TeamSizePlanResponse planner,
        List<MandatorySkillCoverageResponse> mandatorySkills,
        List<FeasibilityFindingResponse> findings,
        String disclaimer
) {
}
