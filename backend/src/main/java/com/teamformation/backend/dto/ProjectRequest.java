package com.teamformation.backend.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;

/**
 * Create/update payload for a mentor's project. Team-size and deadline
 * validation is repeated in the service because some rules span fields.
 */
public record ProjectRequest(
        @NotBlank(message = "Title is required")
        @Size(max = 200, message = "Title must be at most 200 characters")
        String title,

        @NotBlank(message = "Problem statement is required")
        @Size(max = 2000, message = "Problem statement must be at most 2000 characters")
        String problemStatement,

        @NotBlank(message = "Course code is required")
        @Size(max = 50, message = "Course code must be at most 50 characters")
        String courseCode,

        @NotNull(message = "Minimum team size is required")
        @Min(value = 2, message = "Minimum team size must be at least 2")
        Integer minTeamSize,

        @NotNull(message = "Maximum team size is required")
        @Min(value = 2, message = "Maximum team size must be at least 2")
        Integer maxTeamSize,

        @NotNull(message = "Team size slack is required")
        @Min(value = 0, message = "Team size slack must be at least 0")
        Integer teamSizeSlack,

        Instant preferenceDeadline
) {
}
