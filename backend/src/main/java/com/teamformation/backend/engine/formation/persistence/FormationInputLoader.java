package com.teamformation.backend.engine.formation.persistence;

import com.teamformation.backend.engine.constraint.ConstraintConfiguration;
import com.teamformation.backend.engine.constraint.TeamSizePlanner;
import com.teamformation.backend.engine.formation.FormationInput;
import com.teamformation.backend.engine.formation.FormationPreference;
import com.teamformation.backend.engine.formation.FormationStudent;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectConstraint;
import com.teamformation.backend.entity.ProjectRoster;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.StudentSkill;
import com.teamformation.backend.entity.TeammatePreference;
import com.teamformation.backend.entity.enums.ConstraintRuleType;
import com.teamformation.backend.entity.enums.ConstraintType;
import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
import com.teamformation.backend.exception.FormationException;
import com.teamformation.backend.repository.ProjectConstraintRepository;
import com.teamformation.backend.repository.ProjectRosterRepository;
import com.teamformation.backend.repository.SkillRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.StudentSkillRepository;
import com.teamformation.backend.repository.TeammatePreferenceRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Builds the pure {@link FormationInput} for one run using a small, fixed
 * number of bulk queries. It is deliberately kept outside the pure algorithm
 * package: the engine itself never touches a repository.
 */
@Component
public class FormationInputLoader {

    private final ProjectRosterRepository projectRosterRepository;
    private final StudentProfileRepository studentProfileRepository;
    private final StudentSkillRepository studentSkillRepository;
    private final TeammatePreferenceRepository teammatePreferenceRepository;
    private final ProjectConstraintRepository projectConstraintRepository;
    private final SkillRepository skillRepository;

    public FormationInputLoader(
            ProjectRosterRepository projectRosterRepository,
            StudentProfileRepository studentProfileRepository,
            StudentSkillRepository studentSkillRepository,
            TeammatePreferenceRepository teammatePreferenceRepository,
            ProjectConstraintRepository projectConstraintRepository,
            SkillRepository skillRepository) {
        this.projectRosterRepository = projectRosterRepository;
        this.studentProfileRepository = studentProfileRepository;
        this.studentSkillRepository = studentSkillRepository;
        this.teammatePreferenceRepository = teammatePreferenceRepository;
        this.projectConstraintRepository = projectConstraintRepository;
        this.skillRepository = skillRepository;
    }

    public FormationInput load(
            Project project,
            ConstraintConfiguration configuration,
            long seed,
            String algorithmVersion) {
        Long projectId = project.getId();
        validateSupportedConstraints(projectId, configuration.version());

        List<ProjectRoster> roster = new ArrayList<>(projectRosterRepository
                .findByProjectIdAndEligibilityStatus(projectId, RosterEligibilityStatus.ELIGIBLE));
        roster.sort(Comparator.comparing(entry -> entry.getStudent().getId()));

        List<Long> studentIds = roster.stream()
                .map(entry -> entry.getStudent().getId())
                .toList();
        Map<Long, Map<Long, Integer>> proficiencyByStudentId =
                loadProficiencies(studentIds);

        List<FormationStudent> students = new ArrayList<>(studentIds.size());
        for (Long studentId : studentIds) {
            students.add(new FormationStudent(
                    studentId,
                    proficiencyByStudentId.getOrDefault(studentId, Map.of())));
        }

        List<FormationPreference> preferences = loadPreferences(projectId);
        Map<Long, String> skillNames = loadSkillNames(configuration);

        return new FormationInput(
                projectId,
                TeamSizePlanner.plan(
                        students.size(),
                        project.getMinTeamSize(),
                        project.getMaxTeamSize(),
                        project.getTeamSizeSlack()),
                project.getMinTeamSize(),
                project.getMaxTeamSize(),
                project.getTeamSizeSlack(),
                configuration.version(),
                seed,
                algorithmVersion,
                students,
                preferences,
                configuration.prohibitedPairs(),
                configuration.mandatorySkills(),
                skillNames);
    }

    private void validateSupportedConstraints(Long projectId, int version) {
        if (version <= 0) {
            return;
        }
        for (ProjectConstraint row : projectConstraintRepository
                .findByProjectIdAndVersionOrderByIdAsc(projectId, version)) {
            if (row.getConstraintType() != ConstraintType.HARD) {
                throw FormationException.unsupportedConstraint(
                        "constraint " + row.getId() + " is " + row.getConstraintType()
                                + " but only HARD constraints are supported");
            }
            if (row.getRuleType() != ConstraintRuleType.PROHIBITED_PAIR
                    && row.getRuleType() != ConstraintRuleType.MANDATORY_SKILL) {
                throw FormationException.unsupportedConstraint(
                        "constraint " + row.getId() + " has unsupported rule type "
                                + row.getRuleType());
            }
        }
    }

    private Map<Long, Map<Long, Integer>> loadProficiencies(List<Long> studentIds) {
        if (studentIds.isEmpty()) {
            return Map.of();
        }
        List<StudentProfile> profiles =
                studentProfileRepository.findByUserIdIn(studentIds);
        Map<Long, Long> studentIdByProfileId = new HashMap<>();
        for (StudentProfile profile : profiles) {
            studentIdByProfileId.put(profile.getId(), profile.getUser().getId());
        }
        if (studentIdByProfileId.isEmpty()) {
            return Map.of();
        }

        List<StudentSkill> skills = studentSkillRepository
                .findByStudentProfileIdIn(studentIdByProfileId.keySet());
        Map<Long, Map<Long, Integer>> result = new HashMap<>();
        for (StudentSkill skill : skills) {
            Long studentId = studentIdByProfileId.get(skill.getStudentProfile().getId());
            if (studentId == null) {
                continue;
            }
            result.computeIfAbsent(studentId, ignored -> new HashMap<>())
                    .put(skill.getSkill().getId(), skill.getProficiencyLevel());
        }
        result.replaceAll((ignored, value) -> Map.copyOf(value));
        return Map.copyOf(result);
    }

    private List<FormationPreference> loadPreferences(Long projectId) {
        List<TeammatePreference> rows = teammatePreferenceRepository
                .findByProjectIdOrderByStudentIdAscRankAsc(projectId);
        List<FormationPreference> preferences = new ArrayList<>(rows.size());
        for (TeammatePreference row : rows) {
            preferences.add(new FormationPreference(
                    row.getStudent().getId(),
                    row.getPreferredStudent().getId(),
                    row.getRank()));
        }
        preferences.sort(Comparator
                .comparingLong(FormationPreference::studentId)
                .thenComparingInt(FormationPreference::rank)
                .thenComparingLong(FormationPreference::preferredStudentId));
        return List.copyOf(preferences);
    }

    private Map<Long, String> loadSkillNames(ConstraintConfiguration configuration) {
        Set<Long> skillIds = configuration.mandatorySkills().stream()
                .map(skill -> skill.skillId())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (skillIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, String> names = new HashMap<>();
        for (Skill skill : skillRepository.findAllById(skillIds)) {
            names.put(skill.getId(), skill.getName());
        }
        return Map.copyOf(names);
    }
}
