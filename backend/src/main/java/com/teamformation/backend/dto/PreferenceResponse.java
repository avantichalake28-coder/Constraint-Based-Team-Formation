package com.teamformation.backend.dto;

import java.time.Instant;

/**
 * A single ranked teammate preference. Exposed only to the owning student and
 * to the mentor who owns the project.
 */
public record PreferenceResponse(
        Long id,
        Long studentId,
        String studentSrn,
        String studentName,
        Long preferredStudentId,
        String preferredStudentSrn,
        String preferredStudentName,
        Integer rank,
        Instant submittedAt
) {
}
