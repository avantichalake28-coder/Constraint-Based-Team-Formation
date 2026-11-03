package com.teamformation.backend.repository;

import com.teamformation.backend.entity.StudentSkill;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link StudentSkill}.
 * Supports loading a student's skill set (FR-02).
 */
@Repository
public interface StudentSkillRepository extends JpaRepository<StudentSkill, Long> {

    /**
     * Use case: show every skill of one student profile.
     * The skill is fetched with the mapping to avoid one query per row.
     * A list is used because one profile holds only a small, bounded set.
     */
    @EntityGraph(attributePaths = "skill")
    List<StudentSkill> findByStudentProfileId(Long profileId);

    /**
     * Use case: replace the whole skill set in one transaction. The delete is
     * executed immediately so new rows cannot collide with old unique keys.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("delete from StudentSkill s where s.studentProfile.id = :profileId")
    int deleteByStudentProfileId(@Param("profileId") Long profileId);
}
