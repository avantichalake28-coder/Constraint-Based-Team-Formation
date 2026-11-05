package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.dto.StudentProfileResponse;
import com.teamformation.backend.dto.StudentSkillResponse;
import com.teamformation.backend.dto.UpdateStudentProfileRequest;
import com.teamformation.backend.dto.UpdateStudentSkillsRequest;
import com.teamformation.backend.service.StudentProfileService;
import com.teamformation.backend.service.StudentSkillService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The authenticated student's own profile and skill set. There is deliberately
 * no endpoint that accepts another student's id: every operation is scoped to
 * the user resolved from the access token.
 */
@RestController
@RequestMapping("/api/v1/students")
public class StudentController {

    private final StudentProfileService studentProfileService;
    private final StudentSkillService studentSkillService;

    public StudentController(
            StudentProfileService studentProfileService,
            StudentSkillService studentSkillService) {
        this.studentProfileService = studentProfileService;
        this.studentSkillService = studentSkillService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> getMyProfile(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                studentProfileService.getMyProfile(authentication), "Student profile retrieved successfully"));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<StudentProfileResponse>> updateMyProfile(
            Authentication authentication, @Valid @RequestBody UpdateStudentProfileRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                studentProfileService.updateMyProfile(authentication, request), "Student profile updated successfully"));
    }

    @GetMapping("/me/skills")
    public ResponseEntity<ApiResponse<List<StudentSkillResponse>>> getMySkills(Authentication authentication) {
        return ResponseEntity.ok(ApiResponse.success(
                studentSkillService.getMySkills(authentication), "Student skills retrieved successfully"));
    }

    @PutMapping("/me/skills")
    public ResponseEntity<ApiResponse<List<StudentSkillResponse>>> replaceMySkills(
            Authentication authentication, @Valid @RequestBody UpdateStudentSkillsRequest request) {
        return ResponseEntity.ok(ApiResponse.success(
                studentSkillService.replaceMySkills(authentication, request), "Student skills replaced successfully"));
    }
}
