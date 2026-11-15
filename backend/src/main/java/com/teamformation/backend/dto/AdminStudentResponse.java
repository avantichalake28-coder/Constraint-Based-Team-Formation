package com.teamformation.backend.dto;

public record AdminStudentResponse(
        Long id,
        String name,
        String email,
        String srn,
        String department,
        Integer semester,
        boolean active,
        long projectCount
) {
}
