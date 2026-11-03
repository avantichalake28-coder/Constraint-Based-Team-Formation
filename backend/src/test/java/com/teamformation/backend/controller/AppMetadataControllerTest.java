package com.teamformation.backend.controller;

import com.teamformation.backend.dto.AppMetadataDTO;
import com.teamformation.backend.exception.GlobalExceptionHandler;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.service.AppMetadataService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AppMetadataControllerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        AppMetadataService stubService = new AppMetadataService(null, null) {
            @Override
            public List<AppMetadataDTO> getAllMetadata() {
                return List.of(new AppMetadataDTO("schema_version", "1.0.0", Instant.now(), Instant.now()));
            }

            @Override
            public AppMetadataDTO getMetadataByKey(String key) {
                if ("schema_version".equals(key)) {
                    return new AppMetadataDTO("schema_version", "1.0.0", Instant.now(), Instant.now());
                }
                throw new ResourceNotFoundException("AppMetadata", "metaKey", key);
            }
        };

        AppMetadataController controller = new AppMetadataController(stubService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("GET /api/v1/metadata returns 200 with list wrapped in ApiResponse")
    void getAllMetadata_Returns200WithList() throws Exception {
        mockMvc.perform(get("/api/v1/metadata").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Application metadata retrieved successfully"))
                .andExpect(jsonPath("$.data[0].metaKey").value("schema_version"))
                .andExpect(jsonPath("$.data[0].metaValue").value("1.0.0"));
    }

    @Test
    @DisplayName("GET /api/v1/metadata/{key} returns 200 with DTO wrapped in ApiResponse")
    void getMetadataByKey_ExistingKey_Returns200() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/schema_version").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Metadata item retrieved successfully"))
                .andExpect(jsonPath("$.data.metaKey").value("schema_version"))
                .andExpect(jsonPath("$.data.metaValue").value("1.0.0"));
    }

    @Test
    @DisplayName("GET /api/v1/metadata/{key} returns 404 with ErrorResponse when key does not exist")
    void getMetadataByKey_NonExistingKey_Returns404() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/missing_key").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("AppMetadata not found with metaKey: 'missing_key'"))
                .andExpect(jsonPath("$.path").value("/api/v1/metadata/missing_key"));
    }
}
