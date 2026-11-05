package com.teamformation.backend.engine.constraint;

import com.teamformation.backend.dto.FeasibilityFindingResponse;
import com.teamformation.backend.dto.FeasibilityReportResponse;
import com.teamformation.backend.dto.MandatorySkillCoverageResponse;
import com.teamformation.backend.dto.TeamSizePlanResponse;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectRoster;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.StudentSkill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.ProjectRepository;
import com.teamformation.backend.repository.ProjectRosterRepository;
import com.teamformation.backend.repository.SkillRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.StudentSkillRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Loads the roster, project team-size settings and current constraint version,
 * then delegates all reasoning to the pure {@link FeasibilityChecker}. Nothing
 * is written and no arrangement is searched for.
 */
@Service
public class FeasibilityService {

    private final ProjectRepository projectRepository;
    private final ProjectRosterRepository projectRosterRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final SkillRepository skillRepository;
    private final ConstraintConfigurationService constraintConfigurationService;
    private final CurrentUserService currentUserService;

    public FeasibilityService(
            ProjectRepository projectRepository,
            ProjectRosterRepository projectRosterRepository,
            StudentProfileRepository studentProfileRepository,
            StudentSkillRepository studentSkillRepository,
            SkillRepository skillRepository,
            ConstraintConfigurationService constraintConfigurationService,
            CurrentUserService currentUserService) {
        this.projectRepository = projectRepository;
        this.projectRosterRepository = projectRosterRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.studentSkillRepository = studentSkillRepository;
        this.skillRepository = skillRepository;
        this.constraintConfigurationService = constraintConfigurationService;
        this.currentUserService = currentUserService;
    }

    @Transactional(readOnly = true)
    public FeasibilityReportResponse checkFeasibility(Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(authentication, UserRole.MENTOR);
        Project project = projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        List<ProjectRoster> eligibleEntries = projectRosterRepository.findByProjectId(projectId).stream()
                .filter(entry -> entry.getEligibilityStatus() == RosterEligibilityStatus.ELIGIBLE)
                .toList();

        List<Long> studentIds = eligibleEntries.stream()
                .map(entry -> entry.getStudent().getId())
                .toList();
        Map<Long, StudentProfile> profilesByUserId = studentProfileRepository
                .findByUserIdIn(studentIds)
                .stream()
                .collect(Collectors.toMap(
                        profile -> profile.getUser().getId(),
                        Function.identity()));

        Map<Long, Long> userIdByProfileId = profilesByUserId.values().stream()
                .collect(Collectors.toMap(StudentProfile::getId,
                        profile -> profile.getUser().getId()));

        Map<Long, Map<Long, Integer>> skillsByUserId = new HashMap<>();
        if (!userIdByProfileId.isEmpty()) {
            List<StudentSkill> studentSkills = studentSkillRepository
                    .findByStudentProfileIdIn(userIdByProfileId.keySet());
            for (StudentSkill studentSkill : studentSkills) {
                Long userId = userIdByProfileId.get(studentSkill.getStudentProfile().getId());
                if (userId == null) {
                    continue;
                }
                skillsByUserId
                        .computeIfAbsent(userId, ignored -> new HashMap<>())
                        .put(studentSkill.getSkill().getId(), studentSkill.getProficiencyLevel());
            }
        }

        List<RosterStudentSkills> rosterSkills = new ArrayList<>();
        for (Long studentId : studentIds) {
            rosterSkills.add(new RosterStudentSkills(
                    studentId, skillsByUserId.getOrDefault(studentId, Map.of())));
        }

        ConstraintConfiguration configuration =
                constraintConfigurationService.loadCurrentConfiguration(projectId);

        Set<Long> mandatorySkillIds = configuration.mandatorySkills().stream()
                .map(MandatorySkill::skillId)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<Long, String> skillNames = mandatorySkillIds.isEmpty()
                ? Map.of()
                : skillRepository.findAllById(mandatorySkillIds).stream()
                        .collect(Collectors.toMap(Skill::getId, Skill::getName));

        FeasibilityReport report = FeasibilityChecker.check(
                eligibleEntries.size(),
                project.getMinTeamSize(),
                project.getMaxTeamSize(),
                project.getTeamSizeSlack(),
                configuration,
                rosterSkills,
                skillNames);

        return toResponse(project, configuration, report);
    }

    private FeasibilityReportResponse toResponse(
            Project project, ConstraintConfiguration configuration, FeasibilityReport report) {
        TeamSizePlan plan = report.planner();
        TeamSizePlanResponse plannerResponse = new TeamSizePlanResponse(
                plan.feasible(),
                plan.teamCount(),
                plan.teamSizes(),
                plan.slackUsed(),
                plan.reasonCode() == null ? null : plan.reasonCode().name(),
                plan.explanation(),
                plan.suggestedOptions());

        List<MandatorySkillCoverageResponse> coverage = report.mandatorySkills().stream()
                .map(skill -> new MandatorySkillCoverageResponse(
                        skill.skillId(),
                        skill.skillName(),
                        skill.minimumProficiency(),
                        skill.qualifiedStudents(),
                        skill.teamsNeeded(),
                        skill.shortage(),
                        skill.message()))
                .toList();

        List<FeasibilityFindingResponse> findings = report.findings().stream()
                .map(finding -> new FeasibilityFindingResponse(
                        finding.code(),
                        finding.severity().name(),
                        finding.message(),
                        finding.data()))
                .toList();

        return new FeasibilityReportResponse(
                project.getId(),
                report.eligibleStudents(),
                configuration.version(),
                project.getMinTeamSize(),
                project.getMaxTeamSize(),
                project.getTeamSizeSlack(),
                report.overallStatus().name(),
                plannerResponse,
                coverage,
                findings,
                report.disclaimer());
    }
}
