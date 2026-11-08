package com.teamformation.backend.service;

import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.dto.PreferenceResponse;
import com.teamformation.backend.dto.UpdatePreferencesRequest;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.TeammatePreference;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.ProjectRepository;
import com.teamformation.backend.repository.ProjectRosterRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.TeammatePreferenceRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Student-submitted ranked teammate preferences and mentor review. Preferences
 * are confidential: a student can only read their own, while the owning mentor
 * can review every preference in their project.
 */
@Service
public class PreferenceService {

    private final ProjectRepository projectRepository;
    private final ProjectRosterRepository projectRosterRepository;
    private final TeammatePreferenceRepository teammatePreferenceRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final CurrentUserService currentUserService;
    private final ProjectService projectService;

    public PreferenceService(
            ProjectRepository projectRepository,
            ProjectRosterRepository projectRosterRepository,
            TeammatePreferenceRepository teammatePreferenceRepository,
            StudentProfileRepository studentProfileRepository,
            CurrentUserService currentUserService,
            ProjectService projectService) {
        this.projectRepository = projectRepository;
        this.projectRosterRepository = projectRosterRepository;
        this.teammatePreferenceRepository = teammatePreferenceRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.currentUserService = currentUserService;
        this.projectService = projectService;
    }

    /**
     * Replaces all of the student's preferences for one project in a single
     * transaction. The request order defines rank 1, 2, ...
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public List<PreferenceResponse> replaceMyPreferences(
            Authentication authentication, Long projectId, UpdatePreferencesRequest request) {
        User student = currentUserService.requireCurrentUserWithRole(authentication, UserRole.STUDENT);
        // Lock the project row so a preference replacement cannot interleave
        // with a formation input snapshot (T1). Preferences are the student's
        // own rows, so the project lock (not the student) is the serialisation
        // point that T1 also takes.
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (projectService.arePreferencesLocked(project)) {
            throw new BusinessException(
                    "Preferences are locked for this project", HttpStatus.CONFLICT);
        }
        if (project.getStatus() != ProjectStatus.PUBLISHED) {
            throw new BusinessException("Project is not published");
        }
        if (!projectRosterRepository.existsByProjectIdAndStudentId(projectId, student.getId())) {
            throw new BusinessException("The student is not on this project's roster");
        }

        List<Long> preferredIds = request.preferredStudentIds();
        if (preferredIds.size() > 2) {
            throw new BusinessException("At most two preferred teammates are allowed");
        }

        Set<Long> seen = new HashSet<>();
        for (Long preferredId : preferredIds) {
            if (preferredId == null) {
                throw new BusinessException("Preferred student ids must not be null");
            }
            if (preferredId.equals(student.getId())) {
                throw new BusinessException("A student cannot prefer themselves");
            }
            if (!seen.add(preferredId)) {
                throw new BusinessException("A preferred student can appear only once");
            }
            if (!projectRosterRepository.existsByProjectIdAndStudentId(projectId, preferredId)) {
                throw new BusinessException("Preferred student is not on this project's roster");
            }
        }

        teammatePreferenceRepository.deleteForStudent(projectId, student.getId());
        List<TeammatePreference> saved = new ArrayList<>();
        for (int index = 0; index < preferredIds.size(); index++) {
            User preferred = projectRosterRepository
                    .findByProjectIdAndStudentId(projectId, preferredIds.get(index))
                    .orElseThrow(() -> new BusinessException("Preferred student is not on this project's roster"))
                    .getStudent();
            saved.add(teammatePreferenceRepository.save(
                    new TeammatePreference(project, student, preferred, index + 1)));
        }

        return toResponses(saved);
    }

    @Transactional(readOnly = true)
    public List<PreferenceResponse> getMyPreferences(Authentication authentication, Long projectId) {
        User student = currentUserService.requireCurrentUserWithRole(authentication, UserRole.STUDENT);
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        // A student can see the project only when published (or already locked
        // after publication) and only when on the roster. Anything else is 404.
        if (project.getStatus() == ProjectStatus.DRAFT
                || !projectRosterRepository.existsByProjectIdAndStudentId(projectId, student.getId())) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }

        return toResponses(teammatePreferenceRepository
                .findByProjectIdAndStudentIdOrderByRankAsc(projectId, student.getId()));
    }

    @Transactional(readOnly = true)
    public PageResponse<PreferenceResponse> listProjectPreferences(
            Authentication authentication, Long projectId, Pageable pageable) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        Page<TeammatePreference> page = teammatePreferenceRepository
                .findByProjectIdOrderByStudentIdAscRankAsc(projectId, pageable);
        Map<Long, StudentProfile> profilesByUserId = profilesByUserId(page.getContent());
        return PageResponse.from(page.map(preference -> toResponse(
                preference,
                profilesByUserId.get(preference.getStudent().getId()),
                profilesByUserId.get(preference.getPreferredStudent().getId()))));
    }

    private List<PreferenceResponse> toResponses(List<TeammatePreference> preferences) {
        if (preferences.isEmpty()) {
            return List.of();
        }
        Map<Long, StudentProfile> profilesByUserId = profilesByUserId(preferences);
        return preferences.stream()
                .map(preference -> toResponse(
                        preference,
                        profilesByUserId.get(preference.getStudent().getId()),
                        profilesByUserId.get(preference.getPreferredStudent().getId())))
                .toList();
    }

    private Map<Long, StudentProfile> profilesByUserId(List<TeammatePreference> preferences) {
        List<Long> userIds = preferences.stream()
                .flatMap(preference -> List.of(
                        preference.getStudent().getId(),
                        preference.getPreferredStudent().getId()).stream())
                .distinct()
                .toList();
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return studentProfileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(
                        profile -> profile.getUser().getId(),
                        Function.identity(),
                        (first, second) -> first));
    }

    private PreferenceResponse toResponse(
            TeammatePreference preference, StudentProfile studentProfile, StudentProfile preferredProfile) {
        return new PreferenceResponse(
                preference.getId(),
                preference.getStudent().getId(),
                studentProfile != null ? studentProfile.getSrn() : null,
                preference.getStudent().getFullName(),
                preference.getPreferredStudent().getId(),
                preferredProfile != null ? preferredProfile.getSrn() : null,
                preference.getPreferredStudent().getFullName(),
                preference.getRank(),
                preference.getSubmittedAt());
    }
}
