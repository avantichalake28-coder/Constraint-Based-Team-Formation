package com.teamformation.backend.service;

import com.teamformation.backend.dto.AppMetadataDTO;
import com.teamformation.backend.entity.AppMetadata;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.mapper.AppMetadataMapper;
import com.teamformation.backend.repository.AppMetadataRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service managing application metadata retrieval and management.
 * Encapsulates business/application operations and interacts with {@link AppMetadataRepository}.
 */
@Service
@Transactional(readOnly = true)
public class AppMetadataService {

    private final AppMetadataRepository repository;
    private final AppMetadataMapper mapper;

    public AppMetadataService(AppMetadataRepository repository, AppMetadataMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public List<AppMetadataDTO> getAllMetadata() {
        return repository.findAll()
                .stream()
                .map(mapper::toDTO)
                .toList();
    }

    public AppMetadataDTO getMetadataByKey(String key) {
        AppMetadata metadata = repository.findById(key)
                .orElseThrow(() -> new ResourceNotFoundException("AppMetadata", "metaKey", key));
        return mapper.toDTO(metadata);
    }
}

