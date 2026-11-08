package com.teamformation.backend.engine.constraint;

import com.teamformation.backend.dto.ConstraintConfigurationRequest;
import com.teamformation.backend.dto.ConstraintConfigurationResponse;
import com.teamformation.backend.dto.MandatorySkillDto;
import com.teamformation.backend.dto.ProhibitedPairDto;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectConstraint;
import com.teamformation.backend.entity.ProjectConstraintVersion;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ConstraintRuleType;
import com.teamformation.backend.entity.enums.ConstraintType;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.FormationJobRepository;
import com.teamformation.backend.repository.CandidateTeamRepository;
import com.teamformation.backend.repository.FinalizedTeamMembershipRepository;
import com.teamformation.backend.repository.FinalizedTeamRepository;
import com.teamformation.backend.repository.ProjectConstraintRepository;
import com.teamformation.backend.repository.ProjectConstraintVersionRepository;
import com.teamformation.backend.repository.ProjectRepository;
import com.teamformation.backend.repository.ProjectRosterRepository;
import com.teamformation.backend.repository.SkillRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Versioned project constraint configuration. A PUT stores a complete new
 * version and leaves every older version untouched. Version allocation is
 * serialised with the project row and a locking read of the version table.
 */
@Service
public class ConstraintConfigurationService {

    private static final Logger log =
            LoggerFactory.getLogger(ConstraintConfigurationService.class);

    private static final List<JobStatus> ACTIVE_JOB_STATUSES =
            List.of(JobStatus.READY, JobStatus.RUNNING);

    private final ProjectRepository projectRepository;
    private final ProjectRosterRepository projectRosterRepository;
    private final SkillRepository skillRepository;
    private final ProjectConstraintRepository projectConstraintRepository;
    private final ProjectConstraintVersionRepository versionRepository;
    private final FormationJobRepository formationJobRepository;
    private final CandidateTeamRepository candidateTeamRepository;
    private final FinalizedTeamRepository finalizedTeamRepository;
    private final FinalizedTeamMembershipRepository finalizedTeamMembershipRepository;
    private final CurrentUserService currentUserService;

    public ConstraintConfigurationService(
            ProjectRepository projectRepository,
            ProjectRosterRepository projectRosterRepository,
            SkillRepository skillRepository,
            ProjectConstraintRepository projectConstraintRepository,
            ProjectConstraintVersionRepository versionRepository,
            FormationJobRepository formationJobRepository,
            CandidateTeamRepository candidateTeamRepository,
            FinalizedTeamRepository finalizedTeamRepository,
            FinalizedTeamMembershipRepository finalizedTeamMembershipRepository,
            CurrentUserService currentUserService) {
        this.projectRepository = projectRepository;
        this.projectRosterRepository = projectRosterRepository;
        this.skillRepository = skillRepository;
        this.projectConstraintRepository = projectConstraintRepository;
        this.versionRepository = versionRepository;
        this.formationJobRepository = formationJobRepository;
        this.candidateTeamRepository = candidateTeamRepository;
        this.finalizedTeamRepository = finalizedTeamRepository;
        this.finalizedTeamMembershipRepository = finalizedTeamMembershipRepository;
        this.currentUserService = currentUserService;
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ConstraintConfigurationResponse replaceConstraints(
            Authentication authentication, Long projectId, ConstraintConfigurationRequest request) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);

        // The project row lock serialises two concurrent saves for this project.
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (!project.getMentor().getId().equals(mentor.getId())) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }

        rejectIfLocked(project);

        List<ProhibitedPair> pairs = validateProhibitedPairs(projectId, request.prohibitedPairs());
        List<MandatorySkill> mandatory = validateMandatorySkills(request.mandatorySkills());
        validateSkillIdsExist(mandatory);

        List<ProjectConstraintVersion> existingVersions = versionRepository.findVersionsForUpdate(projectId);
        int nextVersion = existingVersions.isEmpty() ? 1 : existingVersions.getFirst().getVersion() + 1;

        ProjectConstraintVersion versionRow =
                versionRepository.save(new ProjectConstraintVersion(project, nextVersion, mentor));

        List<ProjectConstraint> rows = new ArrayList<>();
        for (ProhibitedPair pair : pairs) {
            rows.add(new ProjectConstraint(
                    project,
                    ConstraintType.HARD,
                    ConstraintRuleType.PROHIBITED_PAIR,
                    pair.studentId1() + "," + pair.studentId2(),
                    1.0,
                    nextVersion));
        }
        for (MandatorySkill skill : mandatory) {
            rows.add(new ProjectConstraint(
                    project,
                    ConstraintType.HARD,
                    ConstraintRuleType.MANDATORY_SKILL,
                    skill.skillId() + ":" + skill.minimumProficiency(),
                    1.0,
                    nextVersion));
        }
        projectConstraintRepository.saveAll(rows);

        return toResponse(versionRow, pairs, mandatory);
    }

    @Transactional(readOnly = true)
    public ConstraintConfigurationResponse getCurrentConstraints(
            Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        requireOwnedProject(projectId, mentor);

        return versionRepository.findFirstByProjectIdOrderByVersionDesc(projectId)
                .map(this::toResponse)
                .orElseGet(ConstraintConfigurationService::emptyResponse);
    }

    /**
     * Returns the current constraint version of a project without loading the
     * rules. Version 0 means no configuration has ever been saved.
     *
     * <p>This is used by T2 to prove that the constraint configuration that was
     * captured in the T1 snapshot is still the current one. Joining the caller's
     * transaction is intentional: the check runs while the project row is
     * already locked by T2, so a concurrent save cannot commit between the
     * check and the engine run.</p>
     */
    @Transactional(readOnly = true)
    public int currentVersion(Long projectId) {
        return versionRepository.findFirstByProjectIdOrderByVersionDesc(projectId)
                .map(ProjectConstraintVersion::getVersion)
                .orElse(0);
    }

    /**
     * Loads the current configuration for another service after that service
     * has performed its own authorisation. Version 0 means no configuration has
     * ever been saved.
     */
    @Transactional(readOnly = true)
    public ConstraintConfiguration loadCurrentConfiguration(Long projectId) {
        return versionRepository.findFirstByProjectIdOrderByVersionDesc(projectId)
                .map(version -> loadConfiguration(projectId, version.getVersion()))
                .orElseGet(() -> ConstraintConfiguration.empty(0));
    }

    private void rejectIfLocked(Project project) {
        // Locking preferences is an input to formation, not the start of it.
        // The mentor may still tune constraints until a run becomes active or
        // teams exist; only FORMED and later states are terminal.
        if (project.getStatus() == ProjectStatus.FORMED
                || project.getStatus() == ProjectStatus.APPROVED
                || project.getStatus() == ProjectStatus.COMPLETED) {
            log.info("Rejecting constraint change for project {}: project status {} is FORMED or later",
                    project.getId(), project.getStatus());
            throw new BusinessException(
                    "Constraints cannot change after the project is FORMED or later",
                    HttpStatus.CONFLICT);
        }
        if (formationJobRepository.existsByProjectIdAndJobStatusIn(
                project.getId(), ACTIVE_JOB_STATUSES)) {
            log.info("Rejecting constraint change for project {}: a formation job is READY or RUNNING",
                    project.getId());
            throw new BusinessException(
                    "Constraints cannot change while a formation job is READY or RUNNING",
                    HttpStatus.CONFLICT);
        }

        // Post-completion policy: a COMPLETED job plus candidate and/or
        // finalized history is authoritative. Changing the input constraints
        // afterwards would silently make the stored result unexplainable, so
        // the save is blocked. The mentor must create a new project/version (or
        // a future explicit stale-marking workflow must be introduced) to run
        // with different constraints. No history is deleted.
        boolean completedRun = formationJobRepository.existsByProjectIdAndJobStatus(
                project.getId(), JobStatus.COMPLETED);
        boolean authoritativeData = candidateTeamRepository.existsByProjectId(project.getId())
                || finalizedTeamRepository.existsByProjectId(project.getId())
                || finalizedTeamMembershipRepository.existsByProjectId(project.getId());
        if (completedRun && authoritativeData) {
            log.info("Rejecting constraint change for project {}: a completed formation run exists",
                    project.getId());
            throw new BusinessException(
                    "Constraints cannot change after a formation run has completed. "
                            + "Create a new project or a new version to run with different constraints",
                    HttpStatus.CONFLICT);
        }
    }

    private List<ProhibitedPair> validateProhibitedPairs(
            Long projectId, List<ProhibitedPairDto> requestedPairs) {
        if (requestedPairs == null) {
            return List.of();
        }
        Set<Long> rosterIds = projectRosterRepository.findByProjectId(projectId).stream()
                .map(entry -> entry.getStudent().getId())
                .collect(Collectors.toSet());
        Set<String> seenPairs = new LinkedHashSet<>();
        List<ProhibitedPair> result = new ArrayList<>();

        for (ProhibitedPairDto pair : requestedPairs) {
            if (pair == null || pair.studentId1() == null || pair.studentId2() == null) {
                throw new BusinessException("A prohibited pair needs two student ids");
            }
            long first = pair.studentId1();
            long second = pair.studentId2();
            if (first == second) {
                throw new BusinessException("A prohibited pair must contain two different students");
            }
            if (!rosterIds.contains(first) || !rosterIds.contains(second)) {
                throw new BusinessException(
                        "Every prohibited pair student must be on this project's roster");
            }
            String canonical = Math.min(first, second) + ":" + Math.max(first, second);
            if (!seenPairs.add(canonical)) {
                throw new BusinessException(
                        "A prohibited pair can appear only once, in either order");
            }
            result.add(new ProhibitedPair(first, second));
        }
        return List.copyOf(result);
    }

    private List<MandatorySkill> validateMandatorySkills(List<MandatorySkillDto> requested) {
        if (requested == null) {
            return List.of();
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<MandatorySkill> result = new ArrayList<>();
        for (MandatorySkillDto skill : requested) {
            if (skill == null || skill.skillId() == null) {
                throw new BusinessException("A mandatory skill needs a skill id");
            }
            int minimum = skill.minimumProficiency() == null ? 3 : skill.minimumProficiency();
            if (minimum < 1 || minimum > 5) {
                throw new BusinessException("Minimum proficiency must be between 1 and 5");
            }
            if (!seen.add(skill.skillId())) {
                throw new BusinessException(
                        "A mandatory skill can appear only once: " + skill.skillId());
            }
            result.add(new MandatorySkill(skill.skillId(), minimum));
        }
        return List.copyOf(result);
    }

    private void validateSkillIdsExist(List<MandatorySkill> mandatory) {
        Set<Long> requestedIds = mandatory.stream()
                .map(MandatorySkill::skillId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (requestedIds.isEmpty()) {
            return;
        }
        Set<Long> knownIds = skillRepository.findAllById(requestedIds).stream()
                .map(Skill::getId)
                .collect(Collectors.toSet());
        List<Long> unknown = requestedIds.stream()
                .filter(id -> !knownIds.contains(id))
                .toList();
        if (!unknown.isEmpty()) {
            throw new BusinessException("Unknown skill ids: " + unknown);
        }
    }

    private ConstraintConfiguration loadConfiguration(Long projectId, int version) {
        List<ProhibitedPair> pairs = new ArrayList<>();
        List<MandatorySkill> mandatory = new ArrayList<>();

        for (ProjectConstraint row : projectConstraintRepository
                .findByProjectIdAndVersionOrderByIdAsc(projectId, version)) {
            switch (row.getRuleType()) {
                case PROHIBITED_PAIR -> {
                    String[] parts = row.getRuleConfiguration().split(",", -1);
                    pairs.add(new ProhibitedPair(
                            Long.parseLong(parts[0].trim()), Long.parseLong(parts[1].trim())));
                }
                case MANDATORY_SKILL -> {
                    String[] parts = row.getRuleConfiguration().split(":", -1);
                    mandatory.add(new MandatorySkill(
                            Long.parseLong(parts[0].trim()), Integer.parseInt(parts[1].trim())));
                }
                default -> {
                    // Older rule types are not part of the Task 2.6 configuration.
                }
            }
        }
        return new ConstraintConfiguration(version, pairs, mandatory);
    }

    private ConstraintConfigurationResponse toResponse(ProjectConstraintVersion versionRow) {
        ConstraintConfiguration configuration =
                loadConfiguration(versionRow.getProject().getId(), versionRow.getVersion());
        return toResponse(
                versionRow,
                configuration.prohibitedPairs(),
                configuration.mandatorySkills());
    }

    private ConstraintConfigurationResponse toResponse(
            ProjectConstraintVersion versionRow,
            List<ProhibitedPair> pairs,
            List<MandatorySkill> mandatory) {
        List<ProhibitedPairDto> pairDtos = pairs.stream()
                .map(pair -> new ProhibitedPairDto(pair.studentId1(), pair.studentId2()))
                .toList();
        List<MandatorySkillDto> mandatoryDtos = mandatory.stream()
                .map(skill -> new MandatorySkillDto(skill.skillId(), skill.minimumProficiency()))
                .toList();
        return new ConstraintConfigurationResponse(
                versionRow.getVersion(),
                pairDtos,
                mandatoryDtos,
                versionRow.getChangedBy().getId(),
                versionRow.getChangedBy().getFullName(),
                versionRow.getChangedAt());
    }

    private static ConstraintConfigurationResponse emptyResponse() {
        return new ConstraintConfigurationResponse(
                0,
                List.of(),
                List.of(),
                null,
                null,
                null);
    }

    private Project requireOwnedProject(Long projectId, User mentor) {
        return projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }
}
