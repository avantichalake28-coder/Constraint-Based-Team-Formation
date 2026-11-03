package com.teamformation.backend.repository;

import com.teamformation.backend.entity.ValidationFinding;
import com.teamformation.backend.entity.enums.FindingSeverity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link ValidationFinding}.
 * Supports drill-down into validation findings by severity (FR-12, FR-14).
 */
@Repository
public interface ValidationFindingRepository extends JpaRepository<ValidationFinding, Long> {

    /**
     * Use case: a mentor pages through the findings of one report filtered by
     * severity. The candidate team is fetched with each finding so the UI can
     * name the affected team without an N+1 query.
     */
    @EntityGraph(attributePaths = "candidateTeam")
    Page<ValidationFinding> findByReportIdAndSeverityOrderByCreatedAtAscIdAsc(
            Long reportId, FindingSeverity severity, Pageable pageable);
}
