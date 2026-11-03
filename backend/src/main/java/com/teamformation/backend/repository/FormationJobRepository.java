package com.teamformation.backend.repository;

import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.enums.JobStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FormationJob}.
 * Supports formation job history and active-job checks (Engine E3 lifecycle).
 */
@Repository
public interface FormationJobRepository extends JpaRepository<FormationJob, Long> {

    /**
     * Use case: a mentor pages through formation jobs of a project by status.
     * The triggering user is fetched with each job for the history view.
     */
    @EntityGraph(attributePaths = "triggeredBy")
    Page<FormationJob> findByProjectIdAndJobStatus(Long projectId, JobStatus jobStatus, Pageable pageable);

    /**
     * Use case: prevent a second formation run while one is already queued or
     * running. The caller passes the active statuses (READY, RUNNING).
     */
    boolean existsByProjectIdAndJobStatusIn(Long projectId, Collection<JobStatus> jobStatuses);

    /**
     * Use case: show the most recent formation job of a project.
     * The id is the tie-breaker when two jobs share the same creation instant.
     */
    Optional<FormationJob> findFirstByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);
}
