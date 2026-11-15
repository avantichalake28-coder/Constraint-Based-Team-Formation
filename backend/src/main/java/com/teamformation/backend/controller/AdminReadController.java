package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.dto.AdminMentorResponse;
import com.teamformation.backend.dto.AdminOverviewResponse;
import com.teamformation.backend.dto.AdminProjectResponse;
import com.teamformation.backend.dto.AdminStudentResponse;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.service.AdminReadService;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
public class AdminReadController {

    private final AdminReadService adminReadService;

    public AdminReadController(AdminReadService adminReadService) {
        this.adminReadService = adminReadService;
    }

    @GetMapping("/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminStudentResponse>>> getStudents(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                adminReadService.getStudents(authentication, search, pageable),
                "Students retrieved successfully"));
    }

    @GetMapping("/mentors")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminMentorResponse>>> getMentors(
            Authentication authentication,
            @RequestParam(required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                adminReadService.getMentors(authentication, search, pageable),
                "Mentors retrieved successfully"));
    }

    @GetMapping("/projects")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminProjectResponse>>> getProjects(
            Authentication authentication,
            @RequestParam(required = false) ProjectStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                adminReadService.getProjects(authentication, status, pageable),
                "Projects retrieved successfully"));
    }

    @GetMapping("/overview")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<AdminOverviewResponse>> getOverview(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                adminReadService.getOverview(authentication),
                "Admin overview retrieved successfully"));
    }
}
