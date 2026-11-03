package com.teamformation.backend.repository;

import com.teamformation.backend.entity.ProjectConstraint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link ProjectConstraint}.
 * Supports loading the active constraint set for a formation run (FR-05, FR-06).
 */
@Repository
public interface ProjectConstraintRepository extends JpaRepository<ProjectConstraint, Long> {

    /**
     * Use case: a formation run reads only the active constraints of a project.
     * A list is used because a project holds a small, bounded configuration set.
     */
    List<ProjectConstraint> findByProjectIdAndActiveTrue(Long projectId);
}
