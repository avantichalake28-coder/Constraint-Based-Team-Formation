package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.RosterEligibilityStatus;

import java.time.Instant;

/**
 * Roster entry projection. Only verified students can be added.
 */
public record RosterEntryResponse(
        Long id,
        Long studentId,
        String srn,
        String name,
        String email,
        RosterEligibilityStatus eligibilityStatus,
        Instant enrolledAt
) {
}
