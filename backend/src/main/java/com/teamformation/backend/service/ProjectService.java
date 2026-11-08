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
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Objects;

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
     * deadline has passed. Every roster, preference and project-settings rule
     * calls this method instead of duplicating the comparison.
     */
    public boolean arePreferencesLocked(Project project, Instant now) {
        if (project.getPreferencesLockedAt() != null) {
            return true;
        }
        return project.getPreferenceDeadline() != null && !project.getPreferenceDeadline().isAfter(now);
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

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectResponse updateProject(Authentication authentication, Long projectId, ProjectRequest request) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = requireOwnedProjectForUpdate(projectId, mentor);

        if (arePreferencesLocked(project) && changesLockedSettings(project, request)) {
            throw new BusinessException(
                    "Team size and preference settings cannot be changed after preferences are locked",
                    HttpStatus.CONFLICT);
        }

        Instant previousDeadline = project.getPreferenceDeadline();
        project.setTitle(request.title().trim());
        project.setProblemStatement(request.problemStatement().trim());
        project.setCourseCode(request.courseCode().trim());
        project.setMinTeamSize(request.minTeamSize());
        project.setMaxTeamSize(request.maxTeamSize());
        project.setTeamSizeSlack(request.teamSizeSlack());
        project.setPreferenceDeadline(request.preferenceDeadline());
        project.setUpdatedAt(Instant.now());
        validateProjectSettings(project, request.preferenceDeadline(), previousDeadline);
        return toResponse(project);
    }

    /**
     * Role-dispatched listing: a mentor sees their own projects (optionally
     * filtered by status); a student sees only published projects they are on
     * the roster for.
     */
    @Transactional(readOnly = true)
    public PageResponse<ProjectResponse> listProjects(
            Authentication authentication, ProjectStatus status, Pageable pageable) {
        User user = currentUserService.requireCurrentUser(authentication);

        if (user.getRole() == UserRole.MENTOR) {
            if (status == null) {
                return PageResponse.from(
                        projectRepository.findByMentorId(user.getId(), pageable).map(this::toResponse));
            }
            return PageResponse.from(
                    projectRepository.findByMentorIdAndStatus(user.getId(), status, pageable).map(this::toResponse));
        }

        if (user.getRole() == UserRole.STUDENT) {
            // The optional mentor status filter has no meaning for a student.
            return PageResponse.from(
                    projectRepository.findVisibleRosteredForStudent(user.getId(), pageable).map(this::toResponse));
        }

        throw new BusinessException(
                "You do not have permission to access this resource", HttpStatus.FORBIDDEN);
    }

    /**
     * Role-dispatched single-project read. A mentor can read only their own
     * project; a student can read only a published project they are rostered
     * for. Both cases return 404 for anything else.
     */
    @Transactional(readOnly = true)
    public ProjectResponse getProject(Authentication authentication, Long projectId) {
        User user = currentUserService.requireCurrentUser(authentication);

        if (user.getRole() == UserRole.MENTOR) {
            Project project = projectRepository.findByIdAndMentorId(projectId, user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
            return toResponse(project);
        }

        if (user.getRole() == UserRole.STUDENT) {
            Project project = projectRepository.findVisibleRosteredByIdForStudent(projectId, user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
            return toResponse(project);
        }

        throw new BusinessException(
                "You do not have permission to access this resource", HttpStatus.FORBIDDEN);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectResponse publishProject(Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = requireOwnedProjectForUpdate(projectId, mentor);

        if (project.getStatus() == ProjectStatus.PUBLISHED) {
            return toResponse(project);
        }
        if (project.getStatus() != ProjectStatus.DRAFT) {
            throw new BusinessException(
                    "Only a draft project can be published", HttpStatus.CONFLICT);
        }
        project.setStatus(ProjectStatus.PUBLISHED);
        project.setUpdatedAt(Instant.now());
        return toResponse(project);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ProjectLockResponse lockPreferences(Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        // Load with a pessimistic write lock so two concurrent requests are
        // serialised and observe one consistent locked state.
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (!Objects.equals(project.getMentor().getId(), mentor.getId())) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }

        if (project.getPreferencesLockedAt() != null) {
            // Idempotent: a second lock request changes nothing and returns 200.
            return new ProjectLockResponse(
                    project.getId(), true, project.getPreferencesLockedAt(), project.getStatus());
        }

        Instant now = Instant.now();
        project.setPreferencesLockedAt(now);
        if (project.getStatus() == ProjectStatus.DRAFT || project.getStatus() == ProjectStatus.PUBLISHED) {
            project.setStatus(ProjectStatus.PREFERENCES_LOCKED);
        } else {
            log.info("ASSUMPTION: locking project {} without changing later status {}", projectId, project.getStatus());
        }
        project.setUpdatedAt(now);
        return new ProjectLockResponse(
                project.getId(), true, project.getPreferencesLockedAt(), project.getStatus());
    }

    private boolean changesLockedSettings(Project project, ProjectRequest request) {
        return !Objects.equals(project.getMinTeamSize(), request.minTeamSize())
                || !Objects.equals(project.getMaxTeamSize(), request.maxTeamSize())
                || !Objects.equals(project.getTeamSizeSlack(), request.teamSizeSlack())
                || !sameStoredInstant(project.getPreferenceDeadline(), request.preferenceDeadline());
    }

    /**
     * The schema stores deadlines and lock timestamps as second-precision
     * {@code TIMESTAMP} values. MySQL rounds a sub-second value on write, so a
     * request carrying nanoseconds from {@link Instant#now()} can differ from
     * the persisted value by one whole second without being a real change. A
     * one-second tolerance treats both truncation and rounding as unchanged.
     */
    private boolean sameStoredInstant(Instant left, Instant right) {
        if (left == null || right == null) {
            return left == right;
        }
        return Math.abs(left.getEpochSecond() - right.getEpochSecond()) <= 1;
    }

    private void validateProjectSettings(
            Project project, Instant deadline, Instant previousDeadline) {
        if (project.getMinTeamSize() < 2) {
            throw new BusinessException("Minimum team size must be at least 2");
        }
        if (project.getMaxTeamSize() < project.getMinTeamSize()) {
            throw new BusinessException("Minimum team size must be less than or equal to maximum team size");
        }
        if (project.getTeamSizeSlack() < 0) {
            throw new BusinessException("Team size slack must be at least 0");
        }
        // An unchanged deadline may already be in the past after an automatic
        // lock; only a newly supplied or changed deadline must be in the future.
        if (deadline != null && !sameStoredInstant(deadline, previousDeadline)
                && !deadline.isAfter(Instant.now())) {
            throw new BusinessException("Preference deadline must be in the future when set");
        }
    }

    /**
     * Locks the project row before a lifecycle mutation. Ownership is checked
     * after the lock so a cross-mentor request cannot observe or mutate another
     * mentor's project. All project-scoped mutators take this lock first; the
     * global lock order is Project, then FormationJob, then dependent rows.
     */
    private Project requireOwnedProjectForUpdate(Long projectId, User mentor) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (!Objects.equals(project.getMentor().getId(), mentor.getId())) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }
        return project;
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
