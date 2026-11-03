package com.teamformation.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Mentor/admin skill catalogue creation payload. The name is trimmed and
 * compared ignoring case.
 */
public record CreateSkillRequest(
        @NotBlank(message = "Skill name is required")
        @Size(max = 100, message = "Skill name must be at most 100 characters")
        String name,

        @Size(max = 50, message = "Category must be at most 50 characters")
        String category,

        @Size(max = 500, message = "Description must be at most 500 characters")
        String description
) {
}
