package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.dto.AppMetadataDTO;
import com.teamformation.backend.service.AppMetadataService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller for application metadata and system baseline status.
 * Communicates strictly through {@link AppMetadataService} and exchanges DTOs.
 */
@RestController
@RequestMapping("/api/v1/metadata")
public class AppMetadataController {

    private final AppMetadataService metadataService;

    public AppMetadataController(AppMetadataService metadataService) {
        this.metadataService = metadataService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<AppMetadataDTO>>> getAllMetadata() {
        List<AppMetadataDTO> metadataList = metadataService.getAllMetadata();
        return ResponseEntity.ok(ApiResponse.success(metadataList, "Application metadata retrieved successfully"));
    }

    @GetMapping("/{key}")
    public ResponseEntity<ApiResponse<AppMetadataDTO>> getMetadataByKey(@PathVariable String key) {
        AppMetadataDTO metadata = metadataService.getMetadataByKey(key);
        return ResponseEntity.ok(ApiResponse.success(metadata, "Metadata item retrieved successfully"));
    }
}

