package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.ProjectStatus;

import java.time.Instant;

/**
 * Result of the idempotent lock operation.
 */
public record ProjectLockResponse(
        Long projectId,
        boolean preferencesLocked,
        Instant preferencesLockedAt,
        ProjectStatus status
) {
}
