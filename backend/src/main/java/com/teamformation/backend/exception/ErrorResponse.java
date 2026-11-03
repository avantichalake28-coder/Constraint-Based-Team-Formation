package com.teamformation.backend.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Standard API error response model returned by the centralized exception handler.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String path,
        Instant timestamp,
        Map<String, String> validationErrors
) {

    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, path, Instant.now(), null);
    }

    public static ErrorResponse of(int status, String error, String message, String path, Map<String, String> validationErrors) {
        return new ErrorResponse(status, error, message, path, Instant.now(), validationErrors);
    }
}

