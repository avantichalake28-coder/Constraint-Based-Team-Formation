package com.teamformation.backend.dto;

public record MandatorySkillCoverageResponse(
        Long skillId,
        String skillName,
        int minimumProficiency,
        long qualifiedStudents,
        int teamsNeeded,
        boolean shortage,
        String message
) {
}
