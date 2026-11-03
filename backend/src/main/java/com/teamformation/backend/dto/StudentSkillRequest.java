package com.teamformation.backend.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * One skill choice from the catalogue with proficiency 1 to 5.
 */
public record StudentSkillRequest(
        @NotNull(message = "Skill id is required")
        Long skillId,

        @NotNull(message = "Proficiency is required")
        @Min(value = 1, message = "Proficiency must be between 1 and 5")
        @Max(value = 5, message = "Proficiency must be between 1 and 5")
        Integer proficiency
) {
}
