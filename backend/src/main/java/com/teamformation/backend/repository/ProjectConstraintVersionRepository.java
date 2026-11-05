package com.teamformation.backend.repository;

import com.teamformation.backend.entity.ProjectConstraintVersion;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link ProjectConstraintVersion}. The highest
 * version of a project is the current configuration; every older version remains
 * queryable.
 */
@Repository
public interface ProjectConstraintVersionRepository extends JpaRepository<ProjectConstraintVersion, Long> {

    @EntityGraph(attributePaths = "changedBy")
    Optional<ProjectConstraintVersion> findFirstByProjectIdOrderByVersionDesc(Long projectId);

    /**
     * Use case: allocate the next version while another save for the same
     * project may be waiting. A locking read sees the latest committed rows even
     * when the transaction already established an earlier consistent snapshot.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select v from ProjectConstraintVersion v
            where v.project.id = :projectId
            order by v.version desc
            """)
    List<ProjectConstraintVersion> findVersionsForUpdate(@Param("projectId") Long projectId);
}
