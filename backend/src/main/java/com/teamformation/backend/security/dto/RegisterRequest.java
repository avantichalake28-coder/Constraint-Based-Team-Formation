package com.teamformation.backend.security.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Public student registration payload. Only these four fields are accepted.
 * Unknown JSON properties (for example {@code phone} or {@code role}) are
 * rejected by Jackson before this DTO is bound.
 */
public record RegisterRequest(
        @NotBlank(message = "Full name is required")
        @Size(max = 150, message = "Full name must be at most 150 characters")
        String fullName,

        @NotBlank(message = "Email is required")
        @Email(message = "Email must be valid")
        @Size(max = 150, message = "Email must be at most 150 characters")
        String email,

        @NotBlank(message = "Password is required")
        @Size(min = 10, max = 72, message = "Password must be between 10 and 72 characters")
        String password,

        @NotBlank(message = "SRN is required")
        @Size(max = 50, message = "SRN must be at most 50 characters")
        String srn
) {
}
