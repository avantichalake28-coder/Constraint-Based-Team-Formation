package com.teamformation.backend.dto;

/**
 * Two student ids that must never share a team.
 */
public record ProhibitedPairDto(
        Long studentId1,
        Long studentId2
) {
}
