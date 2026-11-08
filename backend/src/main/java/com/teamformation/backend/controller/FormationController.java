package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.dto.FormationJobResponse;
import com.teamformation.backend.dto.FormationJobSummaryResponse;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.service.FormationService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Engine 3 endpoints: start a synchronous formation run, read one result with
 * its candidate teams, and page through job history. Authorisation and
 * ownership checks live in the service.
 */
@RestController
@RequestMapping("/api/v1/projects/{projectId}/formation-jobs")
public class FormationController {

    private final FormationService formationService;

    public FormationController(FormationService formationService) {
        this.formationService = formationService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<FormationJobResponse>> startFormation(
            Authentication authentication, @PathVariable Long projectId) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                formationService.startFormation(authentication, projectId),
                "Formation job completed successfully"));
    }

    @GetMapping("/{jobId}")
    public ResponseEntity<ApiResponse<FormationJobResponse>> getJob(
            Authentication authentication,
            @PathVariable Long projectId,
            @PathVariable Long jobId) {
        return ResponseEntity.ok(ApiResponse.success(
                formationService.getJob(authentication, projectId, jobId),
                "Formation job retrieved successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<FormationJobSummaryResponse>>> listJobs(
            Authentication authentication,
            @PathVariable Long projectId,
            @RequestParam(required = false) JobStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                formationService.listJobs(authentication, projectId, status, pageable),
                "Formation jobs retrieved successfully"));
    }
}
