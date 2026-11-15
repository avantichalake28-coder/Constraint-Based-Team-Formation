package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;

public record AdminProjectResponse(
        Long id,
        String title,
        String mentorName,
        ProjectStatus status,
        long rosterSize,
        boolean locked,
        JobStatus latestFormationJobStatus
) {
}
