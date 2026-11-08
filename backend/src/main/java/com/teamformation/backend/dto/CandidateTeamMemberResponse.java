package com.teamformation.backend.dto;

import com.teamformation.backend.entity.enums.MembershipSource;

/**
 * One student in a generated candidate team.
 *
 * <p>The student email is deliberately not exposed: the mentor only needs an
 * identifying name and student id for a candidate team, and the email is
 * personal data that is not required by this endpoint.</p>
 */
public record CandidateTeamMemberResponse(
        Long studentId,
        String studentName,
        MembershipSource source
) {
}
