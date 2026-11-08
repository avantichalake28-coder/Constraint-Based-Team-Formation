package com.teamformation.backend;

import com.jayway.jsonpath.JsonPath;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectConstraint;
import com.teamformation.backend.entity.ProjectConstraintVersion;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ConstraintRuleType;
import com.teamformation.backend.entity.enums.ConstraintType;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.MembershipSource;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.entity.ProjectRoster;
import com.teamformation.backend.repository.ProjectConstraintRepository;
import com.teamformation.backend.repository.ProjectConstraintVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engine 3 lifecycle, persistence, API and retrieval integration tests against
 * the real MySQL Testcontainer.
 */
class Engine3FormationIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private ProjectConstraintVersionRepository versionRepository;

    @Autowired
    private ProjectConstraintRepository constraintRepository;

    @Test
    @DisplayName("A successful run persists algorithm teams and leaves the project status unchanged")
    void startFormation_completesAndPersistsTeams() throws Exception {
        User mentor = saveUser("formation.success@college.edu", UserRole.MENTOR);
        Fixture fixture = fixture(mentor, 4, 2, 2, 0, "formation.success", 100);
        addPreference(fixture.project, fixture.students.get(0), fixture.students.get(1), 1);
        addPreference(fixture.project, fixture.students.get(1), fixture.students.get(0), 2);
        addPreference(fixture.project, fixture.students.get(2), fixture.students.get(3), 1);

        MvcResult result = mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.jobStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.constraintVersion").value(0))
                .andExpect(jsonPath("$.data.algorithmVersion").value("3.0.0"))
                .andExpect(jsonPath("$.data.seed").isNumber())
                .andExpect(jsonPath("$.data.teamCount").value(2))
                .andExpect(jsonPath("$.data.assignedStudentCount").value(4))
                .andExpect(jsonPath("$.data.totalPreferenceScore").value(2.5))
                .andExpect(jsonPath("$.data.unsatisfiedPreferenceStudents").value(0))
                .andExpect(jsonPath("$.data.studentsWithoutPreferences").value(1))
                .andExpect(jsonPath("$.data.teams.length()").value(2))
                .andExpect(jsonPath("$.data.teams[0].teamName").value("Team 01"))
                .andExpect(jsonPath("$.data.teams[1].teamName").value("Team 02"))
                .andExpect(jsonPath("$.data.teams[0].members.length()").value(2))
                .andExpect(jsonPath("$.data.teams[1].members.length()").value(2))
                .andExpect(jsonPath("$.data.teams[0].members[0].source").value("ALGORITHM"))
                .andReturn();

        Long jobId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();
        FormationJob job = formationJobRepository.findById(jobId).orElseThrow();
        assertThat(job.getJobStatus()).isEqualTo(JobStatus.COMPLETED);
        assertThat(job.getConstraintVersion()).isZero();
        assertThat(job.getSeed()).isNotNull();
        assertThat(job.getAlgorithmVersion()).isEqualTo("3.0.0");
        assertThat(job.getStartedAt()).isNotNull();
        assertThat(job.getCompletedAt()).isNotNull();
        assertThat(job.getErrorMessage()).isNull();

        assertThat(candidateTeamRepository.countByFormationJobId(jobId)).isEqualTo(2);
        assertThat(candidateTeamMemberRepository.countByFormationJobId(jobId)).isEqualTo(4);
        assertThat(candidateTeamMemberRepository
                .findByFormationJobIdOrderByCandidateTeamIdAscStudentIdAsc(jobId))
                .allMatch(member -> member.getSource() == MembershipSource.ALGORITHM);

        Project reloaded = projectRepository.findById(fixture.project.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProjectStatus.PREFERENCES_LOCKED);

        mockMvc.perform(get(jobsPath(fixture.project) + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.jobStatus").value("COMPLETED"))
                .andExpect(jsonPath("$.data.teams.length()").value(2))
                .andExpect(jsonPath("$.data.teams[0].teamName").value("Team 01"));

        mockMvc.perform(get(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(jobId));

        mockMvc.perform(get(jobsPath(fixture.project))
                        .param("status", "COMPLETED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1));

        mockMvc.perform(get(jobsPath(fixture.project))
                        .param("status", "FAILED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    @Test
    @DisplayName("A rerun after a COMPLETED job is allowed and keeps both runs")
    void startFormation_allowsRerunAfterCompletion() throws Exception {
        User mentor = saveUser("formation.rerun@college.edu", UserRole.MENTOR);
        Fixture fixture = fixture(mentor, 4, 2, 2, 0, "formation.rerun", 110);

        mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.jobStatus").value("COMPLETED"));
        mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.jobStatus").value("COMPLETED"));

        assertThat(formationJobRepository.count()).isEqualTo(2);
        assertThat(candidateTeamRepository.count()).isEqualTo(4);
        assertThat(candidateTeamMemberRepository.count()).isEqualTo(8);

        mockMvc.perform(get(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("Pre-job lifecycle failures create no formation job")
    void startFormation_preJobFailuresCreateNoJob() throws Exception {
        User mentor = saveUser("formation.prejob@college.edu", UserRole.MENTOR);

        Project draft = saveFormationProject(mentor, ProjectStatus.DRAFT, 2, 2, 0);
        mockMvc.perform(post(jobsPath(draft))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("PUBLISHED or PREFERENCES_LOCKED")));

        Project unlocked = saveFormationProject(mentor, ProjectStatus.PUBLISHED, 2, 2, 0);
        mockMvc.perform(post(jobsPath(unlocked))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("preferences must be locked")));

        Fixture tooMany = fixture(mentor, 21, 3, 5, 0, "formation.prejob.size", 120);
        mockMvc.perform(post(jobsPath(tooMany.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Team formation cannot start")));

        Fixture active = fixture(mentor, 4, 2, 2, 0, "formation.prejob.active", 400);
        formationJobRepository.saveAndFlush(new FormationJob(active.project, 0, mentor));
        mockMvc.perform(post(jobsPath(active.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("READY or RUNNING")));

        assertThat(formationJobRepository.count()).isEqualTo(1);
        assertThat(candidateTeamRepository.count()).isZero();
        assertThat(candidateTeamMemberRepository.count()).isZero();
    }

    @Test
    @DisplayName("An unsupported soft or rule-type constraint is rejected instead of ignored")
    void startFormation_rejectsUnsupportedConstraintRows() throws Exception {
        User mentor = saveUser("formation.unsupported@college.edu", UserRole.MENTOR);
        Fixture softFixture = fixture(mentor, 4, 2, 2, 0, "formation.unsupported.soft", 140);
        versionRepository.saveAndFlush(
                new ProjectConstraintVersion(softFixture.project, 1, mentor));
        constraintRepository.saveAndFlush(new ProjectConstraint(
                softFixture.project,
                ConstraintType.SOFT,
                ConstraintRuleType.MANDATORY_SKILL,
                "1:3",
                1.0,
                1));

        mockMvc.perform(post(jobsPath(softFixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("only HARD constraints are supported")));

        Fixture ruleFixture = fixture(mentor, 4, 2, 2, 0, "formation.unsupported.rule", 150);
        versionRepository.saveAndFlush(
                new ProjectConstraintVersion(ruleFixture.project, 1, mentor));
        constraintRepository.saveAndFlush(new ProjectConstraint(
                ruleFixture.project,
                ConstraintType.HARD,
                ConstraintRuleType.TEAM_SIZE,
                "2,2",
                1.0,
                1));

        mockMvc.perform(post(jobsPath(ruleFixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("unsupported rule type TEAM_SIZE")));

        assertThat(formationJobRepository.count()).isZero();
    }

    @Test
    @DisplayName("Mandatory skills are enforced per team and the version is recorded")
    void startFormation_enforcesMandatorySkillPerTeam() throws Exception {
        User mentor = saveUser("formation.mandatory@college.edu", UserRole.MENTOR);
        Fixture fixture = fixture(mentor, 4, 2, 2, 0, "formation.mandatory", 160);
        Skill java = createSkill("Java");
        assignSkill(fixture.students.get(0), java, 4);
        assignSkill(fixture.students.get(2), java, 5);

        mockMvc.perform(put(PROJECTS + "/" + fixture.project.getId() + "/constraints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatorySkills":[{"skillId":%d,"minimumProficiency":3}]}
                                """.formatted(java.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        MvcResult result = mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.constraintVersion").value(1))
                .andReturn();

        List<Map<String, Object>> teams = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.teams");
        assertThat(teams).hasSize(2);
        for (Map<String, Object> team : teams) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members = (List<Map<String, Object>>) team.get("members");
            boolean covered = members.stream()
                    .map(member -> ((Number) member.get("studentId")).longValue())
                    .anyMatch(id -> id == fixture.students.get(0).getId()
                            || id == fixture.students.get(2).getId());
            assertThat(covered)
                    .as("team %s must contain a qualified Java student", team.get("teamName"))
                    .isTrue();
        }
    }

    @Test
    @DisplayName("A prohibited pair is separated and retrieval exposes the persisted teams")
    void startFormation_separatesProhibitedPair() throws Exception {
        User mentor = saveUser("formation.prohibited@college.edu", UserRole.MENTOR);
        Fixture fixture = fixture(mentor, 4, 2, 2, 0, "formation.prohibited", 170);
        long first = fixture.students.get(0).getId();
        long second = fixture.students.get(1).getId();

        mockMvc.perform(put(PROJECTS + "/" + fixture.project.getId() + "/constraints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"prohibitedPairs":[{"studentId1":%d,"studentId2":%d}]}
                                """.formatted(first, second)))
                .andExpect(status().isOk());

        MvcResult result = mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.constraintVersion").value(1))
                .andReturn();

        String json = result.getResponse().getContentAsString();
        List<Map<String, Object>> teams = JsonPath.read(json, "$.data.teams");
        assertThat(teamIndexOf(teams, first)).isNotEqualTo(teamIndexOf(teams, second));

        Long jobId = ((Number) JsonPath.read(json, "$.data.id")).longValue();
        mockMvc.perform(get(jobsPath(fixture.project) + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.teams.length()").value(2))
                .andExpect(jsonPath("$.data.totalPreferenceScore").value(0.0));
    }

    @Test
    @DisplayName("Only the owning mentor can start or read formation jobs")
    void startFormation_enforcesRoleAndOwnership() throws Exception {
        User owner = saveUser("formation.security.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("formation.security.other@college.edu", UserRole.MENTOR);
        User student = saveStudent("formation.security.student@college.edu", "02FE24BCS180");
        User admin = saveUser("formation.security.admin@college.edu", UserRole.ADMIN);
        Fixture fixture = fixture(owner, 4, 2, 2, 0, "formation.security", 182);

        mockMvc.perform(post(jobsPath(fixture.project)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(PROJECTS + "/999999/formation-jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());

        MvcResult result = mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isCreated())
                .andReturn();
        Long jobId = ((Number) JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.id")).longValue();

        mockMvc.perform(get(jobsPath(fixture.project) + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());
        mockMvc.perform(get(jobsPath(fixture.project) + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(jobsPath(fixture.project) + "/" + jobId)
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Only eligible roster entries take part in a formation run")
    void startFormation_usesOnlyEligibleRosterEntries() throws Exception {
        User mentor = saveUser("formation.eligibility@college.edu", UserRole.MENTOR);
        Fixture fixture = fixture(mentor, 2, 2, 2, 0, "formation.eligibility", 190);
        User excluded = saveStudent("formation.eligibility.excluded@college.edu", "02FE24BCS192");
        projectRosterRepository.saveAndFlush(new ProjectRoster(
                fixture.project, excluded, RosterEligibilityStatus.EXCLUDED));

        mockMvc.perform(post(jobsPath(fixture.project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.assignedStudentCount").value(2))
                .andExpect(jsonPath("$.data.teamCount").value(1));
    }

    private Fixture fixture(
            User mentor,
            int students,
            int min,
            int max,
            int slack,
            String emailPrefix,
            int firstSrnSuffix) {
        Project project = saveFormationProject(
                mentor, ProjectStatus.PREFERENCES_LOCKED, min, max, slack);
        project.setPreferencesLockedAt(Instant.now());
        project.setUpdatedAt(Instant.now());
        project = projectRepository.saveAndFlush(project);
        List<User> roster = saveStudents(students, emailPrefix, firstSrnSuffix);
        addRoster(project, roster.toArray(User[]::new));
        return new Fixture(project, roster);
    }

    private static String jobsPath(Project project) {
        return PROJECTS + "/" + project.getId() + "/formation-jobs";
    }

    private static int teamIndexOf(List<Map<String, Object>> teams, long studentId) {
        for (int index = 0; index < teams.size(); index++) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> members =
                    (List<Map<String, Object>>) teams.get(index).get("members");
            boolean present = members.stream()
                    .anyMatch(member -> ((Number) member.get("studentId")).longValue() == studentId);
            if (present) {
                return index;
            }
        }
        throw new AssertionError("Student " + studentId + " is not assigned");
    }

    private record Fixture(Project project, List<User> students) {
    }
}
