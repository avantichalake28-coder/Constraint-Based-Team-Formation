package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;

import java.time.Instant;

public record AdminProjectRow(
        Long id,
        String title,
        String mentorName,
        ProjectStatus status,
        long rosterSize,
        Instant preferencesLockedAt,
        Instant preferenceDeadline,
        JobStatus latestFormationJobStatus
) {
}
