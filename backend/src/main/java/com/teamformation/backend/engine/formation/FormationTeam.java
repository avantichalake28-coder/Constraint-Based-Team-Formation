package com.teamformation.backend.engine.formation;

import java.util.List;

/**
 * One candidate team in an immutable formation plan.
 *
 * @param ordinal         canonical zero-based position after ordering by size
 *                        descending and smallest student id ascending
 * @param teamName        deterministic display name such as {@code Team 01}
 * @param plannedSize     exact size required by the Engine 2 planner
 * @param memberIds       member student ids in ascending order
 * @param preferenceScore directed preference score earned inside this team
 */
public record FormationTeam(
        int ordinal,
        String teamName,
        int plannedSize,
        List<Long> memberIds,
        double preferenceScore
) {

    public FormationTeam {
        if (ordinal < 0) {
            throw new IllegalArgumentException("ordinal must be at least 0");
        }
        if (plannedSize < 0) {
            throw new IllegalArgumentException("plannedSize must be at least 0");
        }
        if (!Double.isFinite(preferenceScore) || preferenceScore < 0.0) {
            throw new IllegalArgumentException("preferenceScore must be finite and non-negative");
        }
        memberIds = memberIds == null ? List.of() : List.copyOf(memberIds);
    }

    public int actualSize() {
        return memberIds.size();
    }
}
