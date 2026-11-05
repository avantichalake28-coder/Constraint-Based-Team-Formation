package com.teamformation.backend.repository;

import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.enums.ProjectStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Project}.
 * Supports mentor-scoped project browsing, student visibility and locking
 * (FR-03, FR-05).
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    /**
     * Use case: a mentor pages through their own projects.
     */
    @EntityGraph(attributePaths = "mentor")
    Page<Project> findByMentorId(Long mentorId, Pageable pageable);

    /**
     * Use case: a mentor pages through their own projects filtered by status.
     */
    @EntityGraph(attributePaths = "mentor")
    Page<Project> findByMentorIdAndStatus(Long mentorId, ProjectStatus status, Pageable pageable);

    /**
     * Use case: authorization lookup that only returns a project when it
     * belongs to the requesting mentor.
     */
    Optional<Project> findByIdAndMentorId(Long id, Long mentorId);

    /**
     * Use case: student-facing project list restricted to non-draft projects
     * the student is on the roster for. Published projects remain visible after
     * the preference lock moves their status to PREFERENCES_LOCKED.
     */
    @Query("""
            select p from Project p
            where p.status <> com.teamformation.backend.entity.enums.ProjectStatus.DRAFT
              and exists (select r from ProjectRoster r where r.project = p and r.student.id = :studentId)
            """)
    Page<Project> findVisibleRosteredForStudent(@Param("studentId") Long studentId, Pageable pageable);

    /**
     * Use case: student-facing single project lookup. A draft project or one
     * without a roster entry is invisible to the student.
     */
    @Query("""
            select p from Project p
            where p.id = :id
              and p.status <> com.teamformation.backend.entity.enums.ProjectStatus.DRAFT
              and exists (select r from ProjectRoster r where r.project = p and r.student.id = :studentId)
            """)
    Optional<Project> findVisibleRosteredByIdForStudent(
            @Param("id") Long id, @Param("studentId") Long studentId);

    /**
     * Use case: serialise state changes such as locking preferences, so
     * concurrent requests cannot both act on the project. JPQL is used because
     * the locking hint is clearer than a derived name.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Project p where p.id = :id")
    Optional<Project> findByIdForUpdate(@Param("id") Long id);
}
