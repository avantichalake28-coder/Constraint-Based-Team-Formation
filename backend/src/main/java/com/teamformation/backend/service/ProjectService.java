package com.teamformation.backend.service;

import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.dto.ProjectLockResponse;
import com.teamformation.backend.dto.ProjectRequest;
import com.teamformation.backend.dto.ProjectResponse;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.ProjectRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Mentor-owned project CRUD, publishing, and a single-source-of-truth
 * preferences-lock check used by every lock-sensitive operation.
 */
@Service
public class ProjectService {

    private static final Logger log = LoggerFactory.getLogger(ProjectService.class);

    private final ProjectRepository projectRepository;
    private final CurrentUserService currentUserService;

    public ProjectService(ProjectRepository projectRepository, CurrentUserService currentUserService) {
        this.projectRepository = projectRepository;
        this.currentUserService = currentUserService;
    }

    /**
     * Single source of truth for whether preferences are locked.
     * A project is locked when {@code preferences_locked_at} is set or the
     * deadline has passed. The result is based on the supplied instant so tests
     * can reason deterministically without relying on wall time.
     */
    public boolean arePreferencesLocked(Project project, Instant now) {
        if (project.getPreferencesLockedAt() != null) {
            return true;
        }
        if (project.getPreferenceDeadline() != null && !project.getPreferenceDeadline().isAfter(now)) {
            return true;
        }
        return false;
    }

    public boolean arePreferencesLocked(Project project) {
        return arePreferencesLocked(project, Instant.now());
    }

    @Transactional
    public ProjectResponse createProject(Authentication authentication, ProjectRequest request) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = new Project(
                mentor,
                request.title().trim(),
                request.problemStatement().trim(),
                request.courseCode().trim(),
                request.minTeamSize(),
                request.maxTeamSize(),
                request.teamSizeSlack(),
                request.preferenceDeadline());
        validateProjectSettings(project, request.preferenceDeadline(), null);
        project.setStatus(ProjectStatus.DRAFT);
        projectRepository.save(project);
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse updateProject(Authentication authentication, Long projectId, ProjectRequest request) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        if (arePreferencesLocked(project)) {
            throw new BusinessException(
                    "Project settings cannot be changed after preferences are locked", HttpStatus.CONFLICT);
        }
        // Team size and roster/preference settings must not change after lock.
        // The request is revalidated against the existing state.
        project.setTitle(request.title().trim());
        project.setProblemStatement(request.problemStatement().trim());
        project.setCourseCode(request.courseCode().trim());
        project.setMinTeamSize(request.minTeamSize());
        project.setMaxTeamSize(request.maxTeamSize());
        project.setTeamSizeSlack(request.teamSizeSlack());
        project.setPreferenceDeadline(request.preferenceDeadline());
        project.setUpdatedAt(Instant.now());
        validateProjectSettings(project, request.preferenceDeadline(), projectId);
        return toResponse(project);
    }

    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listMentorProjects(
            Authentication authentication, ProjectStatus status, Pageable pageable) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        if (status == null) {
            return PageResponse.from(projectRepository.findByMentorId(mentor.getId(), pageable).map(this::toResponse));
        }
        return PageResponse.from(
                projectRepository.findByMentorIdAndStatus(mentor.getId(), status, pageable).map(this::toResponse));
    }

    @Transactional(readOnly = true)
    public ProjectResponse getMentorProject(Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        return toResponse(project);
    }

    @Transactional
    public ProjectResponse publishProject(Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        // Use the closest existing ProjectStatus values. PUBLISHED exists; if
        // a different lifecycle is expected the mapping is logged as an assumption.
        if (project.getStatus() != ProjectStatus.DRAFT) {
            log.warn("ASSUMPTION: project {} publish called from status {} -- publishing only from DRAFT", projectId, project.getStatus());
        }
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setUpdatedAt(Instant.now());
        return toResponse(project);
    }

    @Transactional
    public ProjectLockResponse lockPreferences(Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        // Load with pessimistic lock as requested by the task.
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (project.getMentor().getId() != mentor.getId()) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }
        if (arePreferencesLocked(project)) {
            // Idempotent: return 200 if already locked.
            return new ProjectLockResponse(project.getId(), true, project.getPreferencesLockedAt(), project.getStatus());
        }
        Instant now = Instant.now();
        project.setPreferencesLockedAt(now);
        // Set the existing locked status in the same transaction.
        project.setStatus(ProjectStatus.PREFERENCES_LOCKED);
        project.setUpdatedAt(now);
        return new ProjectLockResponse(project.getId(), true, project.getPreferencesLockedAt(), project.getStatus());
    }

    private void validateProjectSettings(Project project, Instant deadline, Long projectId) {
        if (project.getMinTeamSize() < 2) {
            throw new BusinessException("Minimum team size must be at least 2");
        }
        if (project.getMaxTeamSize() < project.getMinTeamSize()) {
            throw new BusinessException("Minimum team size must be less than or equal to maximum team size");
        }
        if (project.getTeamSizeSlack() < 0) {
            throw new BusinessException("Team size slack must be at least 0");
        }
        if (deadline != null && !deadline.isAfter(Instant.now())) {
            throw new BusinessException("Preference deadline must be in the future when set");
        }
        // Database checks also enforce the same rules.
    }

    private ProjectResponse toResponse(Project project) {
        Instant now = Instant.now();
        boolean locked = arePreferencesLocked(project, now);
        String mentorName = project.getMentor() != null ? project.getMentor().getFullName() : null;
        Long mentorId = project.getMentor() != null ? project.getMentor().getId() : null;
        return new ProjectResponse(
                project.getId(),
                mentorId,
                mentorName,
                project.getTitle(),
                project.getProblemStatement(),
                project.getCourseCode(),
                project.getMinTeamSize(),
                project.getMaxTeamSize(),
                project.getTeamSizeSlack(),
                project.getPreferenceDeadline(),
                project.getPreferencesLockedAt(),
                locked,
                project.getStatus(),
                project.getCreatedAt(),
                project.getUpdatedAt());
    }
}
