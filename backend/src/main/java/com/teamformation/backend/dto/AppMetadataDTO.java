package com.teamformation.backend.dto;

import java.time.Instant;

/**
 * Data Transfer Object for application metadata.
 * Ensures database entities are never directly exposed through REST APIs.
 */
public record AppMetadataDTO(
        String metaKey,
        String metaValue,
        Instant createdAt,
        Instant updatedAt
) {
}

