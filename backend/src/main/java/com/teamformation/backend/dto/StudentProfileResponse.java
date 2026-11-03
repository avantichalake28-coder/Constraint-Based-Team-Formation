package com.teamformation.backend.dto;

import java.time.Instant;

/**
 * Student's own profile. The SRN and email are read-only identity fields.
 */
public record StudentProfileResponse(
        Long id,
        String srn,
        String name,
        String email,
        String department,
        Integer semester,
        String interests,
        Instant createdAt,
        Instant updatedAt
) {
}
