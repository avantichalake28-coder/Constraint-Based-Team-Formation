package com.teamformation.backend.repository;

import com.teamformation.backend.entity.AppMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link AppMetadata}.
 * Handles low-level persistence operations.
 */
@Repository
public interface AppMetadataRepository extends JpaRepository<AppMetadata, String> {
}

