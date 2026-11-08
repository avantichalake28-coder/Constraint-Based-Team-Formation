package com.teamformation.backend.repository;

import com.teamformation.backend.entity.CandidateTeam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link CandidateTeam}.
 * Supports reviewing the teams produced by a formation run (FR-08, FR-10).
 */
@Repository
public interface CandidateTeamRepository extends JpaRepository<CandidateTeam, Long> {

    /**
     * Use case: a mentor pages through the candidate teams of one run.
     */
    Page<CandidateTeam> findByFormationJobIdOrderByTeamNameAsc(Long formationJobId, Pageable pageable);

    /**
     * Use case: load every candidate team of one run in deterministic team-name
     * order for the result view and for persistence verification. A run has a
     * bounded number of teams, so no page is needed.
     */
    List<CandidateTeam> findByFormationJobIdOrderByTeamNameAsc(Long formationJobId);

    /**
     * Use case: verify that every planned team was persisted before the job is
     * marked COMPLETED.
     */
    long countByFormationJobId(Long formationJobId);

    /**
     * Use case: the post-completion constraint policy must know whether a
     * project already has authoritative candidate data.
     */
    boolean existsByProjectId(Long projectId);
}
