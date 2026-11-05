package com.teamformation.backend;

import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectConstraint;
import com.teamformation.backend.entity.ProjectConstraintVersion;
import com.teamformation.backend.entity.ProjectRoster;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.StudentSkill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ConstraintRuleType;
import com.teamformation.backend.entity.enums.ConstraintType;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.ProjectConstraintRepository;
import com.teamformation.backend.repository.ProjectConstraintVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engine 2 feasibility endpoint: it reports the current version and the
 * team-size/mandatory-skill/prohibited-pair findings without writing anything.
 */
class Engine2FeasibilityIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private ProjectConstraintVersionRepository versionRepository;

    @Autowired
    private ProjectConstraintRepository constraintRepository;

    @Test
    @DisplayName("Feasibility without a saved configuration uses version 0 and reports the plan")
    void checkFeasibility_withoutConfiguration() throws Exception {
        User mentor = saveUser("feasibility.fresh@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 3, 5, 0);
        addRoster(project, students(10, "feasibility.fresh", 700).toArray(User[]::new));

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(project.getId()))
                .andExpect(jsonPath("$.data.eligibleStudents").value(10))
                .andExpect(jsonPath("$.data.constraintVersion").value(0))
                .andExpect(jsonPath("$.data.minTeamSize").value(3))
                .andExpect(jsonPath("$.data.maxTeamSize").value(5))
                .andExpect(jsonPath("$.data.slack").value(0))
                .andExpect(jsonPath("$.data.status").value("FEASIBLE"))
                .andExpect(jsonPath("$.data.planner.feasible").value(true))
                .andExpect(jsonPath("$.data.planner.teamCount").value(2))
                .andExpect(jsonPath("$.data.planner.teamSizes[0]").value(5))
                .andExpect(jsonPath("$.data.planner.teamSizes[1]").value(5))
                .andExpect(jsonPath("$.data.planner.slackUsed").value(false))
                .andExpect(jsonPath("$.data.planner.reasonCode").doesNotExist())
                .andExpect(jsonPath("$.data.mandatorySkills.length()").value(0))
                .andExpect(jsonPath("$.data.findings.length()").value(0))
                .andExpect(jsonPath("$.data.disclaimer",
                        containsString("does not guarantee")));

        assertThat(versionRepository.count()).isZero();
        assertThat(constraintRows(project)).isZero();
    }

    @Test
    @DisplayName("A mandatory-skill shortage makes the report INFEASIBLE")
    void checkFeasibility_mandatorySkillShortage() throws Exception {
        User mentor = saveUser("feasibility.shortage@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 2, 0);
        List<User> students = students(4, "feasibility.shortage", 720);
        addRoster(project, students.toArray(User[]::new));
        Skill java = saveSkill("Java");
        giveSkill(students.get(0), java, 4);
        giveSkill(students.get(1), java, 3);

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatorySkills":[{"skillId":%d,"minimumProficiency":4}]}
                                """.formatted(java.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.constraintVersion").value(1))
                .andExpect(jsonPath("$.data.eligibleStudents").value(4))
                .andExpect(jsonPath("$.data.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.data.planner.feasible").value(true))
                .andExpect(jsonPath("$.data.planner.teamSizes[0]").value(2))
                .andExpect(jsonPath("$.data.planner.teamSizes[1]").value(2))
                .andExpect(jsonPath("$.data.mandatorySkills[0].skillId").value(java.getId()))
                .andExpect(jsonPath("$.data.mandatorySkills[0].skillName").value("Java"))
                .andExpect(jsonPath("$.data.mandatorySkills[0].minimumProficiency").value(4))
                .andExpect(jsonPath("$.data.mandatorySkills[0].qualifiedStudents").value(1))
                .andExpect(jsonPath("$.data.mandatorySkills[0].teamsNeeded").value(2))
                .andExpect(jsonPath("$.data.mandatorySkills[0].shortage").value(true))
                .andExpect(jsonPath("$.data.findings[0].code").value("MANDATORY_SKILL_SHORTAGE"))
                .andExpect(jsonPath("$.data.findings[0].severity").value("CRITICAL"))
                .andExpect(jsonPath("$.data.findings[*].code",
                        hasItem("MANDATORY_SKILL_SHORTAGE")));
    }

    @Test
    @DisplayName("Enough qualifying students keep the mandatory-skill report FEASIBLE")
    void checkFeasibility_mandatorySkillSatisfied() throws Exception {
        User mentor = saveUser("feasibility.satisfied@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 2, 0);
        List<User> students = students(4, "feasibility.satisfied", 740);
        addRoster(project, students.toArray(User[]::new));
        Skill java = saveSkill("Java");
        giveSkill(students.get(0), java, 5);
        giveSkill(students.get(1), java, 4);
        giveSkill(students.get(2), java, 2);

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatorySkills":[{"skillId":%d,"minimumProficiency":4}]}
                                """.formatted(java.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FEASIBLE"))
                .andExpect(jsonPath("$.data.mandatorySkills[0].qualifiedStudents").value(2))
                .andExpect(jsonPath("$.data.mandatorySkills[0].teamsNeeded").value(2))
                .andExpect(jsonPath("$.data.mandatorySkills[0].shortage").value(false))
                .andExpect(jsonPath("$.data.findings.length()").value(0));
    }

    @Test
    @DisplayName("Excluded roster entries are not counted as eligible students")
    void checkFeasibility_excludesIneligibleRosterEntries() throws Exception {
        User mentor = saveUser("feasibility.excluded@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 2, 0);
        List<User> students = students(4, "feasibility.excluded", 760);
        addRoster(project, students.get(0), students.get(1));
        projectRosterRepository.saveAndFlush(new ProjectRoster(
                project, students.get(2), RosterEligibilityStatus.EXCLUDED));
        projectRosterRepository.saveAndFlush(new ProjectRoster(
                project, students.get(3), RosterEligibilityStatus.EXCLUDED));

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.eligibleStudents").value(2))
                .andExpect(jsonPath("$.data.status").value("FEASIBLE"))
                .andExpect(jsonPath("$.data.planner.teamSizes[0]").value(2));
    }

    @Test
    @DisplayName("A population with an unplaceable remainder is INFEASIBLE with suggestions")
    void checkFeasibility_teamSizeInfeasible() throws Exception {
        User mentor = saveUser("feasibility.size@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 3, 5, 0);
        addRoster(project, students(21, "feasibility.size", 780).toArray(User[]::new));

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.data.planner.feasible").value(false))
                .andExpect(jsonPath("$.data.planner.reasonCode").value("REMAINDER_NOT_PLACEABLE"))
                .andExpect(jsonPath("$.data.planner.suggestedOptions.length()").isNotEmpty())
                .andExpect(jsonPath("$.data.findings[0].code").value("TEAM_SIZE_INFEASIBLE"))
                .andExpect(jsonPath("$.data.findings[0].severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("A population below the minimum team size is INFEASIBLE with BELOW_MINIMUM")
    void checkFeasibility_belowMinimum() throws Exception {
        User mentor = saveUser("feasibility.below@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 3, 5, 0);
        addRoster(project, students(2, "feasibility.below", 790).toArray(User[]::new));

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.data.eligibleStudents").value(2))
                .andExpect(jsonPath("$.data.planner.feasible").value(false))
                .andExpect(jsonPath("$.data.planner.reasonCode").value("BELOW_MINIMUM"))
                .andExpect(jsonPath("$.data.planner.teamSizes.length()").value(0))
                .andExpect(jsonPath("$.data.findings[0].code").value("TEAM_SIZE_INFEASIBLE"))
                .andExpect(jsonPath("$.data.findings[0].severity").value("CRITICAL"));
    }

    @Test
    @DisplayName("Default sizes with zero slack report suggestions but do not apply them")
    void checkFeasibility_slackZeroReportsSuggestions() throws Exception {
        User mentor = saveUser("feasibility.slack0@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 0);
        addRoster(project, students(21, "feasibility.slack0", 810).toArray(User[]::new));

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.slack").value(0))
                .andExpect(jsonPath("$.data.status").value("INFEASIBLE"))
                .andExpect(jsonPath("$.data.planner.feasible").value(false))
                .andExpect(jsonPath("$.data.planner.teamSizes.length()").value(0))
                .andExpect(jsonPath("$.data.planner.reasonCode").value("REMAINDER_NOT_PLACEABLE"))
                .andExpect(jsonPath("$.data.planner.suggestedOptions.length()").isNotEmpty())
                .andExpect(jsonPath("$.data.findings[0].code").value("TEAM_SIZE_INFEASIBLE"));
    }

    @Test
    @DisplayName("An off-roster prohibited pair is reported as a warning by feasibility")
    void checkFeasibility_offRosterProhibitedPair() throws Exception {
        User mentor = saveUser("feasibility.offroster@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 2, 0);
        List<User> students = students(4, "feasibility.offroster", 840);
        addRoster(project, students.toArray(User[]::new));

        versionRepository.saveAndFlush(new ProjectConstraintVersion(project, 1, mentor));
        constraintRepository.saveAndFlush(new ProjectConstraint(
                project,
                ConstraintType.HARD,
                ConstraintRuleType.PROHIBITED_PAIR,
                students.get(0).getId() + ",999999",
                1.0,
                1));

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.constraintVersion").value(1))
                .andExpect(jsonPath("$.data.status").value("FEASIBLE"))
                .andExpect(jsonPath("$.data.findings[0].code")
                        .value("PROHIBITED_PAIR_STUDENT_NOT_ON_ROSTER"))
                .andExpect(jsonPath("$.data.findings[0].severity").value("WARNING"));
    }

    @Test
    @DisplayName("Checking feasibility writes nothing and leaves the current version intact")
    void checkFeasibility_isReadOnly() throws Exception {
        User mentor = saveUser("feasibility.readonly@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 3, 5, 0);
        addRoster(project, students(10, "feasibility.readonly", 800).toArray(User[]::new));
        Skill java = saveSkill("Java");

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatorySkills":[{"skillId":%d,"minimumProficiency":3}]}
                                """.formatted(java.getId())))
                .andExpect(status().isOk());

        long versionsBefore = versionRepository.count();
        int constraintRowsBefore = constraintRows(project);

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.constraintVersion").value(1));

        assertThat(versionRepository.count()).isEqualTo(versionsBefore);
        assertThat(constraintRows(project)).isEqualTo(constraintRowsBefore);
        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("Only the owning mentor can check feasibility")
    void checkFeasibility_enforcesRoleAndOwnership() throws Exception {
        User owner = saveUser("feasibility.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("feasibility.other@college.edu", UserRole.MENTOR);
        User student = saveStudent("feasibility.caller@college.edu", "02FE24BCS821");
        User admin = saveUser("feasibility.admin@college.edu", UserRole.ADMIN);
        Project project = saveSizedProject(owner, ProjectStatus.PUBLISHED, 2, 4, 1);

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(path(project, "/feasibility"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(path(project, "/feasibility")))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(PROJECTS + "/999999/feasibility")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
    }

    private List<User> students(int count, String emailPrefix, int firstSrnSuffix) {
        List<User> students = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            students.add(saveStudent(
                    emailPrefix + "." + index + "@college.edu",
                    "02FE24BCS" + (firstSrnSuffix + index)));
        }
        return students;
    }

    private Project saveSizedProject(
            User mentor, ProjectStatus status, int min, int max, int slack) {
        Project project = new Project(
                mentor, "Project", "Problem statement", "CS301", min, max, slack, null);
        project.setStatus(status);
        return projectRepository.saveAndFlush(project);
    }

    private Skill saveSkill(String name) {
        return skillRepository.saveAndFlush(new Skill(name, "Engineering", null));
    }

    private void giveSkill(User student, Skill skill, int proficiency) {
        StudentProfile profile = studentProfileRepository.findByUserId(student.getId()).orElseThrow();
        studentSkillRepository.saveAndFlush(new StudentSkill(profile, skill, proficiency));
    }

    private int constraintRows(Project project) {
        Integer rows = jdbcTemplate.queryForObject(
                "select count(*) from project_constraints where project_id = ?",
                Integer.class, project.getId());
        return rows == null ? 0 : rows;
    }

    private static String path(Project project, String suffix) {
        return PROJECTS + "/" + project.getId() + suffix;
    }
}
