package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.ProjectStatus;

import java.time.Instant;

/**
 * Project projection. {@code preferencesLocked} is derived in one place from
 * the timestamp and the deadline.
 */
public record ProjectResponse(
        Long id,
        Long mentorId,
        String mentorName,
        String title,
        String problemStatement,
        String courseCode,
        Integer minTeamSize,
        Integer maxTeamSize,
        Integer teamSizeSlack,
        Instant preferenceDeadline,
        Instant preferencesLockedAt,
        boolean preferencesLocked,
        ProjectStatus status,
        Instant createdAt,
        Instant updatedAt
) {
}
