package com.teamformation.backend.service;

import com.teamformation.backend.dto.AdminFormationJobStatusCount;
import com.teamformation.backend.dto.AdminMentorResponse;
import com.teamformation.backend.dto.AdminOverviewResponse;
import com.teamformation.backend.dto.AdminProjectResponse;
import com.teamformation.backend.dto.AdminProjectStatusCount;
import com.teamformation.backend.dto.AdminStudentResponse;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.FormationJobRepository;
import com.teamformation.backend.repository.ProjectRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

@Service
public class AdminReadService {

    private final StudentProfileRepository studentProfileRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;
    private final FormationJobRepository formationJobRepository;
    private final CurrentUserService currentUserService;
    private final ProjectService projectService;

    public AdminReadService(
            StudentProfileRepository studentProfileRepository,
            UserRepository userRepository,
            ProjectRepository projectRepository,
            FormationJobRepository formationJobRepository,
            CurrentUserService currentUserService,
            ProjectService projectService) {
        this.studentProfileRepository = studentProfileRepository;
        this.userRepository = userRepository;
        this.projectRepository = projectRepository;
        this.formationJobRepository = formationJobRepository;
        this.currentUserService = currentUserService;
        this.projectService = projectService;
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminStudentResponse> getStudents(
            Authentication authentication, String search, Pageable pageable) {
        requireAdmin(authentication);
        return PageResponse.from(studentProfileRepository.findAdminStudents(
                UserRole.STUDENT, normalizeSearch(search), pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminMentorResponse> getMentors(
            Authentication authentication, String search, Pageable pageable) {
        requireAdmin(authentication);
        return PageResponse.from(userRepository.findAdminMentors(
                UserRole.MENTOR, normalizeSearch(search), pageable));
    }

    @Transactional(readOnly = true)
    public PageResponse<AdminProjectResponse> getProjects(
            Authentication authentication, ProjectStatus status, Pageable pageable) {
        requireAdmin(authentication);
        Instant now = Instant.now();
        return PageResponse.from(projectRepository.findAdminProjectRows(status, pageable)
                .map(row -> new AdminProjectResponse(
                        row.id(),
                        row.title(),
                        row.mentorName(),
                        row.status(),
                        row.rosterSize(),
                        projectService.arePreferencesLocked(row.preferencesLockedAt(), row.preferenceDeadline(), now),
                        row.latestFormationJobStatus())));
    }

    @Transactional(readOnly = true)
    public AdminOverviewResponse getOverview(Authentication authentication) {
        requireAdmin(authentication);

        Map<String, Long> projectsByStatus = new LinkedHashMap<>();
        for (ProjectStatus status : ProjectStatus.values()) {
            projectsByStatus.put(status.name(), 0L);
        }
        for (AdminProjectStatusCount count : projectRepository.countAdminProjectsByStatus()) {
            projectsByStatus.put(count.status().name(), count.count());
        }

        Map<String, Long> formationJobsByStatus = new LinkedHashMap<>();
        for (JobStatus status : JobStatus.values()) {
            formationJobsByStatus.put(status.name(), 0L);
        }
        for (AdminFormationJobStatusCount count : formationJobRepository.countAdminFormationJobsByStatus()) {
            formationJobsByStatus.put(count.status().name(), count.count());
        }

        return new AdminOverviewResponse(
                userRepository.countByRole(UserRole.STUDENT),
                userRepository.countByRole(UserRole.MENTOR),
                projectsByStatus,
                formationJobsByStatus);
    }

    private void requireAdmin(Authentication authentication) {
        currentUserService.requireCurrentUserWithRole(authentication, UserRole.ADMIN);
    }

    private String normalizeSearch(String search) {
        if (search == null || search.isBlank()) {
            return null;
        }
        return "%" + search.trim().toLowerCase(Locale.ROOT) + "%";
    }
}
