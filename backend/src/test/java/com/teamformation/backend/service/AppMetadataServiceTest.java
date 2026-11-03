package com.teamformation.backend.service;

import com.teamformation.backend.dto.AppMetadataDTO;
import com.teamformation.backend.entity.AppMetadata;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.mapper.AppMetadataMapper;
import com.teamformation.backend.repository.AppMetadataRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AppMetadataServiceTest {

    private AppMetadataService service;

    @BeforeEach
    void setUp() {
        AppMetadataRepository repository = (AppMetadataRepository) Proxy.newProxyInstance(
                AppMetadataRepository.class.getClassLoader(),
                new Class<?>[]{AppMetadataRepository.class},
                (proxy, method, args) -> {
                    if ("findAll".equals(method.getName())) {
                        return List.of(
                                new AppMetadata("schema_version", "1.0.0"),
                                new AppMetadata("environment", "development")
                        );
                    }
                    if ("findById".equals(method.getName())) {
                        String id = (String) args[0];
                        if ("schema_version".equals(id)) {
                            return Optional.of(new AppMetadata("schema_version", "1.0.0"));
                        }
                        return Optional.empty();
                    }
                    return null;
                }
        );

        AppMetadataMapper mapper = new AppMetadataMapper();
        service = new AppMetadataService(repository, mapper);
    }

    @Test
    @DisplayName("getAllMetadata should return all records mapped to DTOs")
    void getAllMetadata_ReturnsMappedDTOs() {
        List<AppMetadataDTO> results = service.getAllMetadata();

        assertThat(results).hasSize(2);
        assertThat(results.get(0).metaKey()).isEqualTo("schema_version");
        assertThat(results.get(0).metaValue()).isEqualTo("1.0.0");
        assertThat(results.get(1).metaKey()).isEqualTo("environment");
        assertThat(results.get(1).metaValue()).isEqualTo("development");
    }

    @Test
    @DisplayName("getMetadataByKey should return DTO when entity exists")
    void getMetadataByKey_ExistingKey_ReturnsDTO() {
        AppMetadataDTO result = service.getMetadataByKey("schema_version");

        assertThat(result).isNotNull();
        assertThat(result.metaKey()).isEqualTo("schema_version");
        assertThat(result.metaValue()).isEqualTo("1.0.0");
    }

    @Test
    @DisplayName("getMetadataByKey should throw ResourceNotFoundException when key does not exist")
    void getMetadataByKey_NonExistingKey_ThrowsResourceNotFoundException() {
        assertThatThrownBy(() -> service.getMetadataByKey("unknown_key"))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("AppMetadata not found with metaKey: 'unknown_key'");
    }
}
