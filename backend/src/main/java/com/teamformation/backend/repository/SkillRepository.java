package com.teamformation.backend.repository;

import com.teamformation.backend.entity.Skill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Skill}.
 * Supports case-insensitive skill lookup (FR-02, FR-05).
 */
@Repository
public interface SkillRepository extends JpaRepository<Skill, Long> {

    /**
     * Use case: resolve a skill typed by a student or mentor without creating
     * a duplicate when only the letter case differs.
     */
    Optional<Skill> findByNameIgnoreCase(String name);
}
