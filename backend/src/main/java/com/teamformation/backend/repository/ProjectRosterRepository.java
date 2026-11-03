package com.teamformation.backend.repository;

import com.teamformation.backend.entity.ProjectRoster;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ProjectRoster}.
 * Supports the mentor-built eligibility roster (FR-03).
 */
@Repository
public interface ProjectRosterRepository extends JpaRepository<ProjectRoster, Long> {

    /**
     * Use case: a mentor pages through the eligible students of a project.
     * The student is fetched with each entry to avoid an N+1 query.
     */
    @EntityGraph(attributePaths = "student")
    Page<ProjectRoster> findByProjectId(Long projectId, Pageable pageable);

    /**
     * Use case: check whether a student is eligible before accepting a
     * preference or assigning a team.
     */
    boolean existsByProjectIdAndStudentId(Long projectId, Long studentId);

    /**
     * Use case: remove one student from the roster and load the entry for the
     * response before deletion.
     */
    @EntityGraph(attributePaths = "student")
    Optional<ProjectRoster> findByProjectIdAndStudentId(Long projectId, Long studentId);
}
