package com.teamformation.backend.security.dto;

import com.teamformation.backend.entity.enums.UserRole;

/**
 * Current authenticated user. {@code srn} is populated for students only.
 */
public record MeResponse(Long id, String name, String email, UserRole role, String srn) {
}
