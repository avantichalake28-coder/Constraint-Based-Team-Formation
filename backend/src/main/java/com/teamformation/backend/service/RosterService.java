package com.teamformation.backend.service;

import com.teamformation.backend.dto.AddRosterRequest;
import com.teamformation.backend.dto.ClassmateResponse;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.dto.RosterAddResponse;
import com.teamformation.backend.dto.RosterEntryResponse;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectRoster;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.exception.InvalidRosterSrnsException;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.CandidateTeamMemberRepository;
import com.teamformation.backend.repository.FinalizedTeamMembershipRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Mentor-built eligibility roster for one owned project. A student is on the
 * roster only when an existing, verified student matches the supplied SRN.
 * No eligibility calculation is performed here.
 */
@Service
public class RosterService {

    private final ProjectRepository projectRepository;
    private final ProjectRosterRepository projectRosterRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final TeammatePreferenceRepository teammatePreferenceRepository;
    private final CandidateTeamMemberRepository candidateTeamMemberRepository;
    private final FinalizedTeamMembershipRepository finalizedTeamMembershipRepository;
    private final CurrentUserService currentUserService;
    private final ProjectService projectService;

    public RosterService(
            ProjectRepository projectRepository,
            ProjectRosterRepository projectRosterRepository,
            StudentProfileRepository studentProfileRepository,
            TeammatePreferenceRepository teammatePreferenceRepository,
            CandidateTeamMemberRepository candidateTeamMemberRepository,
            FinalizedTeamMembershipRepository finalizedTeamMembershipRepository,
            CurrentUserService currentUserService,
            ProjectService projectService) {
        this.projectRepository = projectRepository;
        this.projectRosterRepository = projectRosterRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.teammatePreferenceRepository = teammatePreferenceRepository;
        this.candidateTeamMemberRepository = candidateTeamMemberRepository;
        this.finalizedTeamMembershipRepository = finalizedTeamMembershipRepository;
        this.currentUserService = currentUserService;
        this.projectService = projectService;
    }

    @Transactional
    public RosterAddResponse addToRoster(
            Authentication authentication, Long projectId, AddRosterRequest request) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = requireOwnedProject(projectId, mentor);
        if (projectService.arePreferencesLocked(project)) {
            throw new BusinessException(
                    "The roster cannot change after preferences are locked", HttpStatus.CONFLICT);
        }

        // Normalise and collapse repeated SRNs while preserving request order.
        Set<String> requestedSrns = request.srns().stream()
                .map(RosterService::normalizeSrn)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        Map<String, StudentProfile> profilesBySrn = studentProfileRepository.findBySrnIn(requestedSrns)
                .stream()
                .collect(Collectors.toMap(
                        profile -> profile.getSrn().toUpperCase(Locale.ROOT),
                        Function.identity(),
                        (first, second) -> first,
                        LinkedHashMap::new));

        List<String> invalidSrns = new ArrayList<>();
        for (String srn : requestedSrns) {
            StudentProfile profile = profilesBySrn.get(srn);
            if (profile == null || !isVerifiedStudent(profile.getUser())) {
                invalidSrns.add(srn);
            }
        }
        if (!invalidSrns.isEmpty()) {
            // All or nothing: nothing below this point has written to the database.
            throw new InvalidRosterSrnsException(invalidSrns);
        }

        Set<Long> alreadyOnRosterIds = projectRosterRepository.findByProjectId(projectId).stream()
                .map(entry -> entry.getStudent().getId())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        List<RosterEntryResponse> added = new ArrayList<>();
        List<String> alreadyOnRoster = new ArrayList<>();
        for (String srn : requestedSrns) {
            StudentProfile profile = profilesBySrn.get(srn);
            User student = profile.getUser();
            if (alreadyOnRosterIds.contains(student.getId())) {
                alreadyOnRoster.add(profile.getSrn());
                continue;
            }
            ProjectRoster entry = projectRosterRepository.save(
                    new ProjectRoster(project, student, RosterEligibilityStatus.ELIGIBLE));
            added.add(toResponse(entry, profile));
        }

        return new RosterAddResponse(added, alreadyOnRoster);
    }

    @Transactional(readOnly = true)
    public PageResponse<RosterEntryResponse> getRoster(
            Authentication authentication, Long projectId, Pageable pageable) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        requireOwnedProject(projectId, mentor);

        Page<ProjectRoster> page = projectRosterRepository.findByProjectId(projectId, pageable);
        Map<Long, StudentProfile> profilesByUserId = profilesByUserId(page.getContent());
        return PageResponse.from(page.map(entry ->
                toResponse(entry, profilesByUserId.get(entry.getStudent().getId()))));
    }

    /**
     * A rostered student pages through the other students on the same
     * non-draft project. Only id, full name and SRN are exposed. A draft,
     * missing or non-rostered project is reported as 404 through the same
     * visibility query used for single-project reads.
     */
    @Transactional(readOnly = true)
    public PageResponse<ClassmateResponse> getClassmates(
            Authentication authentication, Long projectId, Pageable pageable) {
        User student = currentUserService.requireCurrentUserWithRole(authentication, UserRole.STUDENT);
        projectRepository.findVisibleRosteredByIdForStudent(projectId, student.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        Page<ProjectRoster> page = projectRosterRepository
                .findByProjectIdAndStudentIdNot(projectId, student.getId(), pageable);
        Map<Long, StudentProfile> profilesByUserId = profilesByUserId(page.getContent());
        return PageResponse.from(page.map(entry -> {
            StudentProfile profile = profilesByUserId.get(entry.getStudent().getId());
            return new ClassmateResponse(
                    entry.getStudent().getId(),
                    entry.getStudent().getFullName(),
                    profile != null ? profile.getSrn() : null);
        }));
    }

    @Transactional
    public void removeFromRoster(Authentication authentication, Long projectId, Long studentId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = requireOwnedProject(projectId, mentor);
        if (projectService.arePreferencesLocked(project)) {
            throw new BusinessException(
                    "The roster cannot change after preferences are locked", HttpStatus.CONFLICT);
        }

        ProjectRoster entry = projectRosterRepository.findByProjectIdAndStudentId(projectId, studentId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "ProjectRoster", "studentId", studentId));

        if (teammatePreferenceRepository.existsByProjectIdAndStudentId(projectId, studentId)
                || candidateTeamMemberRepository.existsByStudentIdAndCandidateTeamProjectId(studentId, projectId)
                || finalizedTeamMembershipRepository.existsByProjectIdAndStudentId(projectId, studentId)) {
            throw new BusinessException(
                    "The student has preferences or team memberships in this project",
                    HttpStatus.CONFLICT);
        }

        projectRosterRepository.delete(entry);
    }

    private Project requireOwnedProject(Long projectId, User mentor) {
        return projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private boolean isVerifiedStudent(User user) {
        return user.getRole() == UserRole.STUDENT && user.isActive();
    }

    static String normalizeSrn(String srn) {
        return srn == null ? null : srn.trim().toUpperCase(Locale.ROOT);
    }

    private Map<Long, StudentProfile> profilesByUserId(List<ProjectRoster> entries) {
        List<Long> userIds = entries.stream()
                .map(entry -> entry.getStudent().getId())
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

    private RosterEntryResponse toResponse(ProjectRoster entry, StudentProfile profile) {
        User student = entry.getStudent();
        return new RosterEntryResponse(
                entry.getId(),
                student.getId(),
                profile != null ? profile.getSrn() : null,
                student.getFullName(),
                student.getEmail(),
                entry.getEligibilityStatus(),
                entry.getEnrolledAt());
    }
}
