package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.FailureCode;
import com.teamformation.backend.entity.enums.FailurePhase;
import com.teamformation.backend.entity.enums.JobStatus;

import java.time.Instant;
import java.util.List;

/**
 * Full result of one formation run, including every candidate team and member.
 * The preference metrics are persisted at completion time and read back from the
 * job row; they are never recomputed from live preferences.
 */
public record FormationJobResponse(
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
        Instant createdAt,
        int teamCount,
        int assignedStudentCount,
        double totalPreferenceScore,
        int unsatisfiedPreferenceStudents,
        int studentsWithoutPreferences,
        List<CandidateTeamResponse> teams
) {
}
