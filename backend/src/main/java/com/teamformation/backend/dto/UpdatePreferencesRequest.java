package com.teamformation.backend.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Ordered preferred teammate ids. Position 1 is the first choice and position
 * 2 is the second choice. Zero, one or two entries are allowed.
 */
public record UpdatePreferencesRequest(
        @NotNull(message = "Preferred student ids are required")
        @Size(max = 2, message = "At most two preferred teammates are allowed")
        List<@NotNull(message = "Preferred student id must not be null") Long> preferredStudentIds
) {
}
