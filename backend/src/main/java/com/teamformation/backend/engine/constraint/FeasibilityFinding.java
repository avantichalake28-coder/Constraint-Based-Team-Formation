package com.teamformation.backend.engine.constraint;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One explainable feasibility finding. The data map carries the numbers or
 * identifiers behind the message so a client can display them directly.
 */
public record FeasibilityFinding(
        String code,
        FindingSeverity severity,
        String message,
        Map<String, Object> data
) {

    public FeasibilityFinding {
        data = data == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(data));
    }
}
