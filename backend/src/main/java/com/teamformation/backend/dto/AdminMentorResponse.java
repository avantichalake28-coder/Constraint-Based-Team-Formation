package com.teamformation.backend.dto;

public record AdminMentorResponse(
        Long id,
        String name,
        String email,
        boolean active,
        long projectCount
) {
}
