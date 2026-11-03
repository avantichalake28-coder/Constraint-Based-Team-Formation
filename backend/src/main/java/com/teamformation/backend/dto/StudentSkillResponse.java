package com.teamformation.backend.dto;

/**
 * Student's skill after selection from the catalogue. Skills are trusted as
 * entered; there is no verification status.
 */
public record StudentSkillResponse(
        Long skillId,
        String skillName,
        String category,
        Integer proficiency
) {
}
