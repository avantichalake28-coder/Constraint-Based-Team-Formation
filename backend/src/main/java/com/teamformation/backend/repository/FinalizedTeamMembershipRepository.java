package com.teamformation.backend.repository;

import com.teamformation.backend.entity.FinalizedTeamMembership;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FinalizedTeamMembership}.
 * Supports reading and authorising finalized memberships (FR-10, FR-12).
 */
@Repository
public interface FinalizedTeamMembershipRepository extends JpaRepository<FinalizedTeamMembership, Long> {

    /**
     * Use case: list every finalized membership of a project.
     * The student and team are fetched with each row to avoid N+1 queries.
     */
    @EntityGraph(attributePaths = {"student", "finalizedTeam"})
    Page<FinalizedTeamMembership> findByProjectIdOrderByStudentIdAsc(Long projectId, Pageable pageable);

    /**
     * Use case: resolve the single finalized team of one student in a project.
     */
    @EntityGraph(attributePaths = {"student", "finalizedTeam"})
    Optional<FinalizedTeamMembership> findByProjectIdAndStudentId(Long projectId, Long studentId);

    /**
     * Use case: roster removal is blocked while the student has any finalized
     * team membership in the project.
     */
    boolean existsByProjectIdAndStudentId(Long projectId, Long studentId);
}
