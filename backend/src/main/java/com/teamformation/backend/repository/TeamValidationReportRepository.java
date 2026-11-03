package com.teamformation.backend.repository;

import com.teamformation.backend.entity.TeamValidationReport;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TeamValidationReport}.
 * Supports reviewing validation results per formation run (FR-12, FR-13).
 */
@Repository
public interface TeamValidationReportRepository extends JpaRepository<TeamValidationReport, Long> {

    /**
     * Use case: a mentor pages through the validation reports of one run,
     * newest first.
     */
    Page<TeamValidationReport> findByFormationJobIdOrderByEvaluatedAtDescIdDesc(
            Long formationJobId, Pageable pageable);
}
