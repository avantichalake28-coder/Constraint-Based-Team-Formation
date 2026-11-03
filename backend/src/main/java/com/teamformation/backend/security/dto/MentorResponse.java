package com.teamformation.backend.security.dto;

import com.teamformation.backend.entity.enums.UserRole;

/**
 * Created mentor account. No password or password hash is returned.
 */
public record MentorResponse(Long id, String name, String email, UserRole role) {
}
