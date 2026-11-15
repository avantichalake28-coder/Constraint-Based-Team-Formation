package com.teamformation.backend.dto;

import java.util.Map;

public record AdminOverviewResponse(
        long studentCount,
        long mentorCount,
        Map<String, Long> projectsByStatus,
        Map<String, Long> formationJobsByStatus
) {
}
