package com.teamformation.backend.engine.constraint;

/**
 * Coverage of one mandatory skill across the eligible roster and the number of
 * teams that would need at least one qualified member.
 */
public record MandatorySkillCoverage(
        long skillId,
        String skillName,
        int minimumProficiency,
        long qualifiedStudents,
        int teamsNeeded,
        boolean shortage,
        String message
) {
}
