package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.FailureCode;
import com.teamformation.backend.entity.enums.FailurePhase;
import com.teamformation.backend.entity.enums.JobStatus;

import java.time.Instant;

/**
 * Lightweight formation job history row. Teams are loaded only through the
 * single-job endpoint to avoid one query per job in the list.
 */
public record FormationJobSummaryResponse(
        Long id,
        Long projectId,
        JobStatus jobStatus,
        Integer constraintVersion,
        Long seed,
        String algorithmVersion,
        Instant startedAt,
        Instant completedAt,
        String errorMessage,
        FailureCode failureCode,
        FailurePhase failurePhase,
        Long durationMillis,
        Instant createdAt
) {
}
