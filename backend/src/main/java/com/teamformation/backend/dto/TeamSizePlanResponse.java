package com.teamformation.backend.dto;

import java.util.List;

public record TeamSizePlanResponse(
        boolean feasible,
        int teamCount,
        List<Integer> teamSizes,
        boolean slackUsed,
        String reasonCode,
        String explanation,
        List<List<Integer>> suggestedOptions
) {
}
