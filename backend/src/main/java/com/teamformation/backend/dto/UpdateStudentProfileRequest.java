package com.teamformation.backend.dto;

import jakarta.validation.constraints.Size;

/**
 * Editable student profile fields. SRN and email are deliberately absent, so
 * a request containing them is rejected as an unknown JSON field.
 */
public record UpdateStudentProfileRequest(
        @Size(max = 150, message = "Full name must be at most 150 characters")
        String fullName,

        @Size(max = 100, message = "Department must be at most 100 characters")
        String department,

        Integer semester,

        @Size(max = 1000, message = "Interests must be at most 1000 characters")
        String interests
) {
}
