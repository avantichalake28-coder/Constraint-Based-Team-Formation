package com.teamformation.backend.repository;

import com.teamformation.backend.entity.TeammatePreference;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link TeammatePreference}.
 * Supports student submissions and project-level preference review (FR-07, FR-09).
 */
@Repository
public interface TeammatePreferenceRepository extends JpaRepository<TeammatePreference, Long> {

    /**
     * Use case: load one student's ranked preferences for a project.
     * At most two rows exist, so a list is sufficient; both users are fetched
     * with the preferences to avoid an N+1 query.
     */
    @EntityGraph(attributePaths = {"student", "preferredStudent"})
    List<TeammatePreference> findByProjectIdAndStudentIdOrderByRankAsc(Long projectId, Long studentId);

    /**
     * Use case: a mentor pages through all preferences submitted for a project.
     */
    @EntityGraph(attributePaths = {"student", "preferredStudent"})
    Page<TeammatePreference> findByProjectIdOrderByStudentIdAscRankAsc(Long projectId, Pageable pageable);

    /**
     * Use case: the formation input loader needs every preference of one
     * project in one unpaged query. The project holds at most two rows per
     * rostered student.
     */
    @EntityGraph(attributePaths = {"student", "preferredStudent"})
    List<TeammatePreference> findByProjectIdOrderByStudentIdAscRankAsc(Long projectId);

    /**
     * Use case: roster removal is blocked while the student still has any
     * preference in the project.
     */
    boolean existsByProjectIdAndStudentId(Long projectId, Long studentId);

    /**
     * Use case: replace one student's preferences in one statement before
     * inserting the new ranks, avoiding unique-key clashes.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from TeammatePreference p where p.project.id = :projectId and p.student.id = :studentId")
    int deleteForStudent(@Param("projectId") Long projectId, @Param("studentId") Long studentId);
}
