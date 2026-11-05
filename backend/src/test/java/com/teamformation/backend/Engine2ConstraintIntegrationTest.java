package com.teamformation.backend;

import com.jayway.jsonpath.JsonPath;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.ProjectConstraintVersionRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Versioned constraint API: validation, actor/version history, locking and
 * concurrent saves. Every assertion is made through the HTTP contract.
 */
class Engine2ConstraintIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private ProjectConstraintVersionRepository versionRepository;

    @Test
    @DisplayName("A project with no saved configuration reports version 0 and empty rules")
    void getCurrentConstraints_beforeAnySave() throws Exception {
        User mentor = saveUser("constraints.fresh@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);

        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.version").value(0))
                .andExpect(jsonPath("$.data.prohibitedPairs.length()").value(0))
                .andExpect(jsonPath("$.data.mandatorySkills.length()").value(0))
                .andExpect(jsonPath("$.data.changedById").value(nullValue()))
                .andExpect(jsonPath("$.data.changedByName").value(nullValue()))
                .andExpect(jsonPath("$.data.changedAt").value(nullValue()));

        assertThat(versionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Saving a full configuration stores version 1 with its actor and returns it")
    void replaceConstraints_storesVersionAndActor() throws Exception {
        User mentor = saveUser("constraints.actor@college.edu", UserRole.MENTOR);
        User first = saveStudent("constraints.actor.first@college.edu", "02FE24BCS601");
        User second = saveStudent("constraints.actor.second@college.edu", "02FE24BCS602");
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);
        addRoster(project, first, second);
        Skill java = saveSkill("Java");

        String body = fullBody(first, second, java, 4);

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.prohibitedPairs[0].studentId1").value(first.getId()))
                .andExpect(jsonPath("$.data.prohibitedPairs[0].studentId2").value(second.getId()))
                .andExpect(jsonPath("$.data.mandatorySkills[0].skillId").value(java.getId()))
                .andExpect(jsonPath("$.data.mandatorySkills[0].minimumProficiency").value(4))
                .andExpect(jsonPath("$.data.changedById").value(mentor.getId()))
                .andExpect(jsonPath("$.data.changedByName").value(mentor.getFullName()))
                .andExpect(jsonPath("$.data.changedAt").isNotEmpty());

        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1))
                .andExpect(jsonPath("$.data.mandatorySkills[0].skillId").value(java.getId()));

        assertThat(versionRepository.findFirstByProjectIdOrderByVersionDesc(project.getId()))
                .hasValueSatisfying(version -> {
                    assertThat(version.getVersion()).isEqualTo(1);
                    assertThat(version.getChangedBy().getId()).isEqualTo(mentor.getId());
                    assertThat(version.getChangedAt()).isNotNull();
                });

        List<String> storedRuleTypes = jdbcTemplate.queryForList(
                "select rule_type from project_constraints where project_id = ? order by id",
                String.class, project.getId());
        assertThat(storedRuleTypes).containsExactly("PROHIBITED_PAIR", "MANDATORY_SKILL");
    }

    @Test
    @DisplayName("An empty version is still recorded with its actor and does not delete older rows")
    void replaceConstraints_emptyVersionKeepsHistory() throws Exception {
        User mentor = saveUser("constraints.history@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);
        Skill java = saveSkill("Java");

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandatorySkills":[{"skillId":%d,"minimumProficiency":3}]}
                                """.formatted(java.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.mandatorySkills.length()").value(0))
                .andExpect(jsonPath("$.data.changedById").value(mentor.getId()));

        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2))
                .andExpect(jsonPath("$.data.mandatorySkills.length()").value(0));

        Integer versionOneRows = jdbcTemplate.queryForObject(
                "select count(*) from project_constraints where project_id = ? and version = 1",
                Integer.class, project.getId());
        Integer versionTwoRows = jdbcTemplate.queryForObject(
                "select count(*) from project_constraints where project_id = ? and version = 2",
                Integer.class, project.getId());
        assertThat(versionOneRows).isEqualTo(1);
        assertThat(versionTwoRows).isZero();
        assertThat(versionRepository.count()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForList(
                "select version from project_constraint_versions where project_id = ? order by version",
                Integer.class, project.getId())).containsExactly(1, 2);
    }

    @Test
    @DisplayName("Invalid constraint bodies are rejected without creating a version")
    void replaceConstraints_validationErrorsCreateNothing() throws Exception {
        User mentor = saveUser("constraints.invalid@college.edu", UserRole.MENTOR);
        User first = saveStudent("constraints.invalid.first@college.edu", "02FE24BCS611");
        User second = saveStudent("constraints.invalid.second@college.edu", "02FE24BCS612");
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);
        addRoster(project, first, second);
        Skill java = saveSkill("Java");

        List<String> invalidBodies = new ArrayList<>();
        invalidBodies.add("""
                {"prohibitedPairs":[{"studentId1":%d,"studentId2":%d}]}
                """.formatted(first.getId(), first.getId()));
        invalidBodies.add("""
                {"prohibitedPairs":[{"studentId1":%d,"studentId2":999999}]}
                """.formatted(first.getId()));
        invalidBodies.add("""
                {"prohibitedPairs":[
                  {"studentId1":%d,"studentId2":%d},
                  {"studentId1":%d,"studentId2":%d}
                ]}
                """.formatted(first.getId(), second.getId(), second.getId(), first.getId()));
        invalidBodies.add("""
                {"prohibitedPairs":[{"studentId1":%d}]}
                """.formatted(first.getId()));
        invalidBodies.add("""
                {"mandatorySkills":[
                  {"skillId":%d,"minimumProficiency":3},
                  {"skillId":%d,"minimumProficiency":4}
                ]}
                """.formatted(java.getId(), java.getId()));
        invalidBodies.add("""
                {"mandatorySkills":[{"skillId":%d,"minimumProficiency":0}]}
                """.formatted(java.getId()));
        invalidBodies.add("""
                {"mandatorySkills":[{"skillId":%d,"minimumProficiency":6}]}
                """.formatted(java.getId()));
        invalidBodies.add("""
                {"mandatorySkills":[{"skillId":999999,"minimumProficiency":3}]}
                """);
        invalidBodies.add("""
                {"unexpectedField":true}
                """);

        for (String body : invalidBodies) {
            mockMvc.perform(put(path(project, "/constraints"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isBadRequest());
        }

        assertThat(versionRepository.count()).isZero();
        Integer constraintRows = jdbcTemplate.queryForObject(
                "select count(*) from project_constraints where project_id = ?",
                Integer.class, project.getId());
        assertThat(constraintRows).isZero();
    }

    @Test
    @DisplayName("Only the owning mentor can read or replace constraints")
    void constraints_enforceRoleAndOwnership() throws Exception {
        User owner = saveUser("constraints.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("constraints.other@college.edu", UserRole.MENTOR);
        User student = saveStudent("constraints.caller@college.edu", "02FE24BCS621");
        Project project = saveSizedProject(owner, ProjectStatus.PUBLISHED, 2, 4, 1);

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk());

        mockMvc.perform(get(PROJECTS + "/999999/constraints")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(path(project, "/constraints")))
                .andExpect(status().isUnauthorized());

        assertThat(versionRepository.count()).isZero();
    }

    @Test
    @DisplayName("Formed, approved and completed projects reject constraint changes")
    void replaceConstraints_rejectsFormedOrLaterStatuses() throws Exception {
        User mentor = saveUser("constraints.locked@college.edu", UserRole.MENTOR);

        for (ProjectStatus locked : List.of(
                ProjectStatus.FORMED, ProjectStatus.APPROVED, ProjectStatus.COMPLETED)) {
            Project project = saveSizedProject(mentor, locked, 2, 4, 1);

            mockMvc.perform(put(path(project, "/constraints"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("FORMED or later")));
        }

        assertThat(versionRepository.count()).isZero();
    }

    @Test
    @DisplayName("A READY or RUNNING formation job blocks constraint changes")
    void replaceConstraints_rejectsActiveFormationJob() throws Exception {
        User mentor = saveUser("constraints.job@college.edu", UserRole.MENTOR);
        Project readyProject = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);
        Project runningProject = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);
        Project finishedProject = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);

        formationJobRepository.saveAndFlush(new FormationJob(readyProject, 1, mentor));
        FormationJob running = new FormationJob(runningProject, 1, mentor);
        running.setJobStatus(JobStatus.RUNNING);
        formationJobRepository.saveAndFlush(running);
        FormationJob completed = new FormationJob(finishedProject, 1, mentor);
        completed.setJobStatus(JobStatus.COMPLETED);
        formationJobRepository.saveAndFlush(completed);

        for (Project blocked : List.of(readyProject, runningProject)) {
            mockMvc.perform(put(path(blocked, "/constraints"))
                            .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{}"))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("READY or RUNNING")));
        }

        mockMvc.perform(put(path(finishedProject, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));
    }

    @Test
    @DisplayName("Locking preferences alone does not freeze the constraint configuration")
    void replaceConstraints_allowedAfterPreferencesLocked() throws Exception {
        User mentor = saveUser("constraints.prefs-locked@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);
        project.setStatus(ProjectStatus.PREFERENCES_LOCKED);
        project.setPreferencesLockedAt(Instant.now());
        projectRepository.saveAndFlush(project);

        mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(1));

        assertThat(versionRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Two concurrent saves receive distinct consecutive versions")
    void replaceConstraints_concurrentSavesAllocateDistinctVersions() throws Exception {
        User mentor = saveUser("constraints.concurrent@college.edu", UserRole.MENTOR);
        Project project = saveSizedProject(mentor, ProjectStatus.PUBLISHED, 2, 4, 1);

        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<MvcResult> first = executor.submit(() -> saveConstraintAfterSignal(
                    mentor, project, ready, start));
            Future<MvcResult> second = executor.submit(() -> saveConstraintAfterSignal(
                    mentor, project, ready, start));

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();

            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            MvcResult secondResult = second.get(30, TimeUnit.SECONDS);

            assertThat(firstResult.getResponse().getStatus()).isEqualTo(200);
            assertThat(secondResult.getResponse().getStatus()).isEqualTo(200);

            List<Integer> versions = new ArrayList<>();
            versions.add(JsonPath.read(
                    firstResult.getResponse().getContentAsString(), "$.data.version"));
            versions.add(JsonPath.read(
                    secondResult.getResponse().getContentAsString(), "$.data.version"));
            versions.sort(Integer::compareTo);
            assertThat(versions).containsExactly(1, 2);
        } finally {
            executor.shutdownNow();
        }

        assertThat(versionRepository.count()).isEqualTo(2);
        mockMvc.perform(get(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.version").value(2));
    }

    private MvcResult saveConstraintAfterSignal(
            User mentor, Project project, CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        if (!start.await(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("concurrent save did not start");
        }
        return mockMvc.perform(put(path(project, "/constraints"))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andReturn();
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

    private static String path(Project project, String suffix) {
        return PROJECTS + "/" + project.getId() + suffix;
    }

    private static String fullBody(
            User first, User second, Skill mandatory, int minimum) {
        return """
                {
                  "prohibitedPairs":[
                    {"studentId1":%d,"studentId2":%d}
                  ],
                  "mandatorySkills":[
                    {"skillId":%d,"minimumProficiency":%d}
                  ]
                }
                """.formatted(
                first.getId(), second.getId(),
                mandatory.getId(), minimum);
    }
}
