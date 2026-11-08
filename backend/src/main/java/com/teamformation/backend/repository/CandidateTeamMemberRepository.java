package com.teamformation.backend.repository;

import com.teamformation.backend.entity.CandidateTeamMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CandidateTeamMember}.
 * Supports loading the members of a candidate team (FR-08, FR-10).
 */
@Repository
public interface CandidateTeamMemberRepository extends JpaRepository<CandidateTeamMember, Long> {

    /**
     * Use case: show the members of one candidate team.
     * The student is fetched with each membership to avoid an N+1 query.
     * A list is used because membership is bounded by the project team size.
     */
    @EntityGraph(attributePaths = "student")
    List<CandidateTeamMember> findByCandidateTeamIdOrderByStudentIdAsc(Long candidateTeamId);

    /**
     * Use case: load every membership of one run in one query for the result
     * view and for persistence verification. The student is fetched with each
     * row; the candidate team id is a scalar on the row.
     */
    @EntityGraph(attributePaths = "student")
    List<CandidateTeamMember> findByFormationJobIdOrderByCandidateTeamIdAscStudentIdAsc(Long formationJobId);

    /**
     * Use case: verify that every planned membership was persisted before the
     * job is marked COMPLETED.
     */
    long countByFormationJobId(Long formationJobId);

    /**
     * Use case: roster removal is blocked while the student has any candidate
     * team membership in the project.
     */
    boolean existsByStudentIdAndCandidateTeamProjectId(Long studentId, Long projectId);
}
