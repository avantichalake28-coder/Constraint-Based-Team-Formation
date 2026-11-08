package com.teamformation.backend.repository;

import com.teamformation.backend.entity.FinalizedTeam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link FinalizedTeam}.
 * Supports reading the approved teams of a project (FR-12, FR-15).
 */
@Repository
public interface FinalizedTeamRepository extends JpaRepository<FinalizedTeam, Long> {

    /**
     * Use case: list the finalized teams of a project.
     * The approving mentor is fetched with each team for the audit view.
     */
    @EntityGraph(attributePaths = "finalizedBy")
    Page<FinalizedTeam> findByProjectIdOrderByTeamNameAsc(Long projectId, Pageable pageable);

    /**
     * Use case: the post-completion constraint policy must know whether the
     * project already has finalized history.
     */
    boolean existsByProjectId(Long projectId);
}
