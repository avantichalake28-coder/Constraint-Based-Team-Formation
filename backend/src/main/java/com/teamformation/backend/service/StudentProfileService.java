package com.teamformation.backend.service;

import com.teamformation.backend.dto.StudentProfileResponse;
import com.teamformation.backend.dto.UpdateStudentProfileRequest;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Student-owned profile operations. The SRN and email are never editable
 * through the update payload.
 */
@Service
public class StudentProfileService {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

    public StudentProfileService(
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            CurrentUserService currentUserService) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public StudentProfileResponse getMyProfile(Authentication authentication) {
        StudentProfile profile = loadOwnProfile(authentication);
        return toResponse(profile);
    }

    @Transactional
    public StudentProfileResponse updateMyProfile(
            Authentication authentication, UpdateStudentProfileRequest request) {
        StudentProfile profile = loadOwnProfile(authentication);
        User user = profile.getUser();
        Instant now = Instant.now();

        if (request.fullName() != null) {
            String fullName = request.fullName().trim();
            if (fullName.isEmpty()) {
                throw new BusinessException("Full name must not be blank");
            }
            user.setFullName(fullName);
            user.setUpdatedAt(now);
            userRepository.save(user);
        }
        if (request.department() != null) {
            profile.setDepartment(normalizeNullable(request.department()));
        }
        if (request.semester() != null) {
            profile.setSemester(request.semester());
        }
        if (request.interests() != null) {
            profile.setInterests(normalizeNullable(request.interests()));
        }
        profile.setUpdatedAt(now);
        studentProfileRepository.save(profile);
        return toResponse(profile);
    }

    private StudentProfile loadOwnProfile(Authentication authentication) {
        User student = currentUserService.requireCurrentUserWithRole(authentication, UserRole.STUDENT);
        return studentProfileRepository.findByUserId(student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("StudentProfile", "userId", student.getId()));
    }

    private String normalizeNullable(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private StudentProfileResponse toResponse(StudentProfile profile) {
        User user = profile.getUser();
        return new StudentProfileResponse(
                profile.getId(),
                profile.getSrn(),
                user.getFullName(),
                user.getEmail(),
                profile.getDepartment(),
                profile.getSemester(),
                profile.getInterests(),
                profile.getCreatedAt(),
                profile.getUpdatedAt());
    }
}
