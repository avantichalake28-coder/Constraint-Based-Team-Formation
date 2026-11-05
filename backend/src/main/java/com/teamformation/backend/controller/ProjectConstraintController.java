package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.dto.ConstraintConfigurationRequest;
import com.teamformation.backend.dto.ConstraintConfigurationResponse;
import com.teamformation.backend.dto.FeasibilityReportResponse;
import com.teamformation.backend.engine.constraint.ConstraintConfigurationService;
import com.teamformation.backend.engine.constraint.FeasibilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Engine 2 endpoints: versioned constraint configuration and the read-only
 * feasibility report. Authorisation and ownership checks live in the services.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectConstraintController {

    private final ConstraintConfigurationService constraintConfigurationService;
    private final FeasibilityService feasibilityService;

    public ProjectConstraintController(
            ConstraintConfigurationService constraintConfigurationService,
            FeasibilityService feasibilityService) {
        this.constraintConfigurationService = constraintConfigurationService;
        this.feasibilityService = feasibilityService;
    }

    @PutMapping("/{id}/constraints")
    public ResponseEntity<ApiResponse<ConstraintConfigurationResponse>> replaceConstraints(
            Authentication authentication,
            @PathVariable Long id,
            @RequestBody ConstraintConfigurationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                constraintConfigurationService.replaceConstraints(authentication, id, request),
                "Constraints replaced successfully"));
    }

    @GetMapping("/{id}/constraints")
    public ResponseEntity<ApiResponse<ConstraintConfigurationResponse>> getCurrentConstraints(
            Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                constraintConfigurationService.getCurrentConstraints(authentication, id),
                "Constraints retrieved successfully"));
    }

    @PostMapping("/{id}/feasibility")
    public ResponseEntity<ApiResponse<FeasibilityReportResponse>> checkFeasibility(
            Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                feasibilityService.checkFeasibility(authentication, id),
                "Feasibility checked successfully"));
    }
}
