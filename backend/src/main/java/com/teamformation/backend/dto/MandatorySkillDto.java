package com.teamformation.backend.dto;

/**
 * A skill every team must contain. A null minimum proficiency defaults to 3.
 */
public record MandatorySkillDto(
        Long skillId,
        Integer minimumProficiency
) {
}
