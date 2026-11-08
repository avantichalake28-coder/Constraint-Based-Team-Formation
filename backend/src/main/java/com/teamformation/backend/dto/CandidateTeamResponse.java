package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.TeamStatus;

import java.util.List;

/**
 * A generated candidate team and its algorithm-sourced members.
 */
public record CandidateTeamResponse(
        Long id,
        String teamName,
        TeamStatus status,
        double compatibilityScore,
        int memberCount,
        List<CandidateTeamMemberResponse> members
) {
}
