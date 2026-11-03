package com.teamformation.backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Adds students to a project roster by SRN, all or nothing.
 */
public record AddRosterRequest(
        @NotEmpty(message = "At least one SRN is required")
        List<@NotBlank(message = "SRN must not be blank") String> srns
) {
}
