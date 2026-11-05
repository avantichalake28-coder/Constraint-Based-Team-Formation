package com.teamformation.backend.repository;

import com.teamformation.backend.entity.StudentProfile;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link StudentProfile}.
 * Supports profile loading and SRN uniqueness checks (FR-02).
 */
@Repository
public interface StudentProfileRepository extends JpaRepository<StudentProfile, Long> {

    /**
     * Use case: load the profile owned by a user after authentication.
     */
    @EntityGraph(attributePaths = "user")
    Optional<StudentProfile> findByUserId(Long userId);

    /**
     * Use case: locate a student by their registration number.
     * The owning user is fetched eagerly for this query because callers need
     * the student's identity alongside the profile.
     */
    @EntityGraph(attributePaths = "user")
    Optional<StudentProfile> findBySrn(String srn);

    /**
     * Use case: registration must reject an SRN that already exists.
     */
    boolean existsBySrn(String srn);

    /**
     * Use case: resolve a batch of roster SRNs in one query.
     * The owning user is fetched eagerly because roster responses need the
     * student's name, email and active state.
     */
    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findBySrnIn(Collection<String> srns);

    /**
     * Use case: decorate a page of roster or preference rows with each
     * student's SRN without issuing one query per row.
     */
    @EntityGraph(attributePaths = "user")
    List<StudentProfile> findByUserIdIn(Collection<Long> userIds);
}
