package com.teamformation.backend.dto;

/**
 * Minimal classmate projection. It intentionally contains only the student id,
 * full name and SRN; email, skills and preferences are never exposed here.
 */
public record ClassmateResponse(
        Long studentId,
        String fullName,
        String srn
) {
}
