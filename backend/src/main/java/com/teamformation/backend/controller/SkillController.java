package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.dto.CreateSkillRequest;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.dto.SkillResponse;
import com.teamformation.backend.service.SkillService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Shared skill catalogue. All authenticated users can read it; only mentors
 * and admins can add entries.
 */
@RestController
@RequestMapping("/api/v1/skills")
public class SkillController {

    private final SkillService skillService;

    public SkillController(SkillService skillService) {
        this.skillService = skillService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<SkillResponse>>> listSkills(
            Authentication authentication,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(
                skillService.listSkills(authentication, pageable), "Skills retrieved successfully"));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SkillResponse>> createSkill(
            Authentication authentication, @Valid @RequestBody CreateSkillRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(
                skillService.createSkill(authentication, request), "Skill created successfully"));
    }
}
