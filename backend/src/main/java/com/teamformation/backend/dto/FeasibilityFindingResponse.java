package com.teamformation.backend.dto;

import java.util.Map;

public record FeasibilityFindingResponse(
        String code,
        String severity,
        String message,
        Map<String, Object> data
) {
}
