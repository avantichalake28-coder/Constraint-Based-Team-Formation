package com.teamformation.backend.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/**
 * Replaces the whole skill set in one transaction. An empty list clears it.
 */
public record UpdateStudentSkillsRequest(
        @NotNull(message = "Skills list is required")
        List<@Valid StudentSkillRequest> skills
) {
}
