package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.dto.AddRosterRequest;
import com.teamformation.backend.dto.ClassmateResponse;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.dto.PreferenceResponse;
import com.teamformation.backend.dto.ProjectLockResponse;
import com.teamformation.backend.dto.ProjectRequest;
import com.teamformation.backend.dto.ProjectResponse;
import com.teamformation.backend.dto.RosterAddResponse;
import com.teamformation.backend.dto.RosterEntryResponse;
import com.teamformation.backend.dto.UpdatePreferencesRequest;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.service.PreferenceService;
import com.teamformation.backend.service.ProjectService;
import com.teamformation.backend.service.RosterService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Project, roster, preference and locking endpoints. Role and ownership checks
 * live in the services; the controller only maps HTTP to those calls.
 */
@RestController
@RequestMapping("/api/v1/projects")
public class ProjectController {

    private final ProjectService projectService;
    private final RosterService rosterService;
    private final PreferenceService preferenceService;

    public ProjectController(
            ProjectService projectService,
            RosterService rosterService,
            PreferenceService preferenceService) {
        this.projectService = projectService;
        this.rosterService = rosterService;
        this.preferenceService = preferenceService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ProjectResponse>> createProject(
            Authentication authentication, @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                projectService.createProject(authentication, request), "Project created successfully"));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> updateProject(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody ProjectRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                projectService.updateProject(authentication, id, request), "Project updated successfully"));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ProjectResponse>>> listProjects(
            Authentication authentication,
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                projectService.listProjects(authentication, status, pageable), "Projects retrieved successfully"));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProjectResponse>> getProject(
            Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                projectService.getProject(authentication, id), "Project retrieved successfully"));
    }

    @PostMapping("/{id}/publish")
    public ResponseEntity<ApiResponse<ProjectResponse>> publishProject(
            Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                projectService.publishProject(authentication, id), "Project published successfully"));
    }

    @PostMapping("/{id}/lock-preferences")
    public ResponseEntity<ApiResponse<ProjectLockResponse>> lockPreferences(
            Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                projectService.lockPreferences(authentication, id), "Preferences locked successfully"));
    }

    @PostMapping("/{id}/roster")
    public ResponseEntity<ApiResponse<RosterAddResponse>> addToRoster(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody AddRosterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                rosterService.addToRoster(authentication, id, request), "Roster updated successfully"));
    }

    @GetMapping("/{id}/roster")
    public ResponseEntity<ApiResponse<PageResponse<RosterEntryResponse>>> getRoster(
            Authentication authentication,
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                rosterService.getRoster(authentication, id, pageable), "Roster retrieved successfully"));
    }

    @GetMapping("/{id}/classmates")
    public ResponseEntity<ApiResponse<PageResponse<ClassmateResponse>>> getClassmates(
            Authentication authentication,
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                rosterService.getClassmates(authentication, id, pageable),
                "Classmates retrieved successfully"));
    }

    @DeleteMapping("/{id}/roster/{studentId}")
    public ResponseEntity<ApiResponse<Void>> removeFromRoster(
            Authentication authentication, @PathVariable Long id, @PathVariable Long studentId) {
        rosterService.removeFromRoster(authentication, id, studentId);
        return ResponseEntity.ok(ApiResponse.success(null, "Student removed from roster"));
    }

    @PutMapping("/{id}/preferences")
    public ResponseEntity<ApiResponse<List<PreferenceResponse>>> replaceMyPreferences(
            Authentication authentication,
            @PathVariable Long id,
            @Valid @RequestBody UpdatePreferencesRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.replaceMyPreferences(authentication, id, request),
                "Preferences updated successfully"));
    }

    @GetMapping("/{id}/preferences/me")
    public ResponseEntity<ApiResponse<List<PreferenceResponse>>> getMyPreferences(
            Authentication authentication, @PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.getMyPreferences(authentication, id), "Preferences retrieved successfully"));
    }

    @GetMapping("/{id}/preferences")
    public ResponseEntity<ApiResponse<PageResponse<PreferenceResponse>>> listProjectPreferences(
            Authentication authentication,
            @PathVariable Long id,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                preferenceService.listProjectPreferences(authentication, id, pageable),
                "Project preferences retrieved successfully"));
    }
}
