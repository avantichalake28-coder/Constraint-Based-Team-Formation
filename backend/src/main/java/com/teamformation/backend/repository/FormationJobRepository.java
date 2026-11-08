package com.teamformation.backend.repository;

import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.enums.JobStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link FormationJob}.
 * Supports formation job history, active-job checks and stale-job recovery
 * (Engine E3 lifecycle).
 */
@Repository
public interface FormationJobRepository extends JpaRepository<FormationJob, Long> {

    /**
     * Use case: a mentor pages through formation jobs of a project by status.
     * The triggering user is fetched with each job for the history view.
     */
    @EntityGraph(attributePaths = "triggeredBy")
    Page<FormationJob> findByProjectId(Long projectId, Pageable pageable);

    @EntityGraph(attributePaths = "triggeredBy")
    Page<FormationJob> findByProjectIdAndJobStatus(Long projectId, JobStatus jobStatus, Pageable pageable);

    /**
     * Use case: prevent a second formation run while one is already queued or
     * running. The caller passes the active statuses (READY, RUNNING).
     */
    boolean existsByProjectIdAndJobStatusIn(Long projectId, Collection<JobStatus> jobStatuses);

    /**
     * Use case: enforce the post-completion constraint policy. A project that
     * already has a COMPLETED formation job has authoritative result data and
     * must not silently change its input configuration.
     */
    boolean existsByProjectIdAndJobStatus(Long projectId, JobStatus jobStatus);

    /**
     * Use case: show the most recent formation job of a project.
     * The id is the tie-breaker when two jobs share the same creation instant.
     */
    Optional<FormationJob> findFirstByProjectIdOrderByCreatedAtDescIdDesc(Long projectId);

    /**
     * Use case: show the authoritative most recent COMPLETED job of a project.
     * A formation POST is deliberately non-idempotent; this read identifies the
     * current result when a duplicate run is triggered.
     */
    Optional<FormationJob> findFirstByProjectIdAndJobStatusOrderByCompletedAtDescIdDesc(
            Long projectId, JobStatus jobStatus);

    /**
     * Use case: project-scoped job lookup for mentor-facing reads. A job from a
     * different project is never returned.
     */
    Optional<FormationJob> findByIdAndProjectId(Long id, Long projectId);

    /**
     * Use case: serialise the READY to RUNNING transition and the failure
     * transition of one formation job. JPQL is used so the pessimistic write
     * lock is explicit.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select j from FormationJob j where j.id = :id")
    Optional<FormationJob> findByIdForUpdate(@Param("id") Long id);

    /**
     * Use case: the stale-job reaper needs candidate job ids without loading
     * entities or taking a lock. Locking and the age/status recheck happen per
     * job afterwards, so this query is only a discovery step and never a blind
     * bulk update.
     *
     * <p>READY jobs are anchored on their creation instant because they never
     * started. RUNNING jobs are anchored on their start instant. A null anchor
     * is treated as stale so a malformed legacy row cannot block the project
     * forever.</p>
     */
    @Query("""
            select j.id from FormationJob j
            where j.jobStatus in :statuses
              and (coalesce(j.startedAt, j.createdAt) is null
                   or coalesce(j.startedAt, j.createdAt) < :cutoff)
            order by j.id asc
            """)
    List<Long> findStaleJobIds(
            @Param("statuses") Collection<JobStatus> statuses,
            @Param("cutoff") Instant cutoff);
}
