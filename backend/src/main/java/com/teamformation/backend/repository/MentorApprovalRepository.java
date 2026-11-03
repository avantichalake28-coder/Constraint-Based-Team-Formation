package com.teamformation.backend.repository;

import com.teamformation.backend.entity.MentorApproval;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link MentorApproval}.
 * Supports the auditable approval history of a project (NFR-06, FR-12).
 */
@Repository
public interface MentorApprovalRepository extends JpaRepository<MentorApproval, Long> {

    /**
     * Use case: list a project's approval decisions, newest first.
     * The mentor and validation report are fetched with each decision.
     */
    @EntityGraph(attributePaths = {"mentor", "validationReport"})
    Page<MentorApproval> findByProjectIdOrderByDecisionTimestampDescIdDesc(
            Long projectId, Pageable pageable);
}
