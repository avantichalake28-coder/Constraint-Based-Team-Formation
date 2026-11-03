package com.teamformation.backend.repository;

import com.teamformation.backend.entity.CandidateTeam;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

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
}
