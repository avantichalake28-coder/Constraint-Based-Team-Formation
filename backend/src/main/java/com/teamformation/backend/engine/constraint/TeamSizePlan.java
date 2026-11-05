package com.teamformation.backend.engine.constraint;

import java.util.List;

/**
 * Immutable result of {@link TeamSizePlanner}. A feasible plan has descending
 * team sizes and no reason code. An infeasible plan has a reason code, a plain
 * explanation and optional alternative splits that are suggestions only; the
 * planner never applies a suggested split.
 */
public record TeamSizePlan(
        boolean feasible,
        List<Integer> teamSizes,
        boolean slackUsed,
        PlannerInfeasibilityReason reasonCode,
        String explanation,
        List<List<Integer>> suggestedOptions
) {

    public TeamSizePlan {
        teamSizes = teamSizes == null ? List.of() : List.copyOf(teamSizes);
        suggestedOptions = suggestedOptions == null
                ? List.of()
                : suggestedOptions.stream().map(List::copyOf).toList();
    }

    public static TeamSizePlan feasible(List<Integer> teamSizes, boolean slackUsed) {
        return new TeamSizePlan(true, teamSizes, slackUsed, null, null, List.of());
    }

    public static TeamSizePlan infeasible(
            PlannerInfeasibilityReason reasonCode,
            String explanation,
            List<List<Integer>> suggestedOptions) {
        return new TeamSizePlan(false, List.of(), false, reasonCode, explanation, suggestedOptions);
    }

    public int teamCount() {
        return teamSizes.size();
    }
}
