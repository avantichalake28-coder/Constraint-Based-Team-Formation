package com.teamformation.backend.security.dto;

/**
 * Successful login response. The access token is the only credential returned.
 */
public record LoginResponse(String accessToken, String tokenType, long expiresInSeconds) {
}
