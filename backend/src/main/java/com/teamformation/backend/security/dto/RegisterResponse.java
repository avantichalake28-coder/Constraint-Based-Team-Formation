package com.teamformation.backend.security.dto;

/**
 * Registration acknowledgement. It deliberately contains no password, token
 * or token hash.
 */
public record RegisterResponse(Long id, String email) {
}
