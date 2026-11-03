package com.teamformation.backend.mapper;

import com.teamformation.backend.dto.AppMetadataDTO;
import com.teamformation.backend.entity.AppMetadata;
import org.springframework.stereotype.Component;

/**
 * Mapper responsible for converting between {@link AppMetadata} entities and {@link AppMetadataDTO} objects.
 */
@Component
public class AppMetadataMapper {

    public AppMetadataDTO toDTO(AppMetadata entity) {
        if (entity == null) {
            return null;
        }
        return new AppMetadataDTO(
                entity.getMetaKey(),
                entity.getMetaValue(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}

