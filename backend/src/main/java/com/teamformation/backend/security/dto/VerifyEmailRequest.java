package com.teamformation.backend.security.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * POST form of email verification. The token is accepted in the request body
 * so it is not written to access logs as a query string.
 */
public record VerifyEmailRequest(
        @NotBlank(message = "Token is required")
        String token
) {
}
