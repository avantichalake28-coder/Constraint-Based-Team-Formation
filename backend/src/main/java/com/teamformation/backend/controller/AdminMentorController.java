package com.teamformation.backend.controller;

import com.teamformation.backend.common.ApiResponse;
import com.teamformation.backend.security.dto.CreateMentorRequest;
import com.teamformation.backend.security.dto.MentorResponse;
import com.teamformation.backend.security.service.MentorAccountService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin-only account administration. The role check is enforced both by the
 * URL rule (authenticated) and by method security (ADMIN).
 */
@RestController
@RequestMapping("/api/v1/admin/mentors")
public class AdminMentorController {

    private final MentorAccountService mentorAccountService;

    public AdminMentorController(MentorAccountService mentorAccountService) {
        this.mentorAccountService = mentorAccountService;
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<MentorResponse>> createMentor(
            @Valid @RequestBody CreateMentorRequest request) {
        MentorResponse response = mentorAccountService.createMentor(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "Mentor account created"));
    }
}
