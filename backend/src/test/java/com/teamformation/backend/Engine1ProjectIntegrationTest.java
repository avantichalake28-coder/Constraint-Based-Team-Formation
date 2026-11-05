package com.teamformation.backend;

import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engine 1 project lifecycle: ownership, validation, publishing, listing,
 * student visibility and the single-source preference lock.
 */
class Engine1ProjectIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Test
    @DisplayName("A mentor creates a draft project scoped to themselves")
    void createProject_mentorCreatesOwnDraft() throws Exception {
        User mentor = saveUser("project.mentor@college.edu", UserRole.MENTOR);

        mockMvc.perform(post(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Capstone", 2, 4, 1, Instant.now().plusSeconds(3600))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Project created successfully"))
                .andExpect(jsonPath("$.data.mentorId").value(mentor.getId()))
                .andExpect(jsonPath("$.data.mentorName").value(mentor.getFullName()))
                .andExpect(jsonPath("$.data.title").value("Capstone"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.preferencesLocked").value(false));

        Project saved = projectRepository.findAll().getFirst();
        assertThat(saved.getMentor().getId()).isEqualTo(mentor.getId());
        assertThat(saved.getStatus()).isEqualTo(ProjectStatus.DRAFT);
    }

    @Test
    @DisplayName("Project creation is rejected for a student, an admin and an unauthenticated caller")
    void createProject_rejectsWrongRolesAndAnonymous() throws Exception {
        User student = saveUser("project.student@college.edu", UserRole.STUDENT);
        User admin = saveUser("project.admin@college.edu", UserRole.ADMIN);
        String body = projectJson("Blocked", 2, 4, 1, Instant.now().plusSeconds(3600));

        mockMvc.perform(post(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(PROJECTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnauthorized());

        assertThat(projectRepository.count()).isZero();
    }

    @Test
    @DisplayName("Project creation validates team-size rules, deadlines and unknown JSON fields")
    void createProject_validatesSettingsAndUnknownFields() throws Exception {
        User mentor = saveUser("validation.mentor@college.edu", UserRole.MENTOR);

        mockMvc.perform(post(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Too Small", 1, 4, 1, Instant.now().plusSeconds(3600))))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Inverted", 5, 4, 1, Instant.now().plusSeconds(3600))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Minimum team size")));

        mockMvc.perform(post(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Past", 2, 4, 1, Instant.now().minusSeconds(3600))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("future")));

        mockMvc.perform(post(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Phone Field",
                                  "problemStatement": "Problem statement",
                                  "courseCode": "CS301",
                                  "minTeamSize": 2,
                                  "maxTeamSize": 4,
                                  "teamSizeSlack": 1,
                                  "preferenceDeadline": null,
                                  "phone": "9876543210"
                                }
                                """))
                .andExpect(status().isBadRequest());

        assertThat(projectRepository.count()).isZero();
    }

    @Test
    @DisplayName("A mentor can update only their own project and cannot change locked settings")
    void updateProject_enforcesOwnershipAndLockRules() throws Exception {
        User owner = saveUser("update.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("update.other@college.edu", UserRole.MENTOR);
        Instant deadline = Instant.now().plusSeconds(7200).plusNanos(900_000_000);
        Project project = saveProject(owner, ProjectStatus.PUBLISHED, deadline);

        mockMvc.perform(put(PROJECTS + "/" + project.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Stolen", 2, 4, 1, deadline)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(PROJECTS + "/" + project.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Renamed", 2, 4, 1, deadline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Renamed"))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        project = projectRepository.findById(project.getId()).orElseThrow();
        project.setPreferencesLockedAt(Instant.now());
        projectRepository.saveAndFlush(project);

        mockMvc.perform(put(PROJECTS + "/" + project.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Changed Size", 3, 4, 1, deadline)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("locked")));

        mockMvc.perform(put(PROJECTS + "/" + project.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Renamed Again", 2, 4, 1, deadline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Renamed Again"));
    }

    @Test
    @DisplayName("After an automatic deadline lock a mentor can still edit non-locked fields")
    void updateProject_allowsNonLockedFieldsAfterDeadlineLock() throws Exception {
        User mentor = saveUser("deadline.mentor@college.edu", UserRole.MENTOR);
        Instant pastDeadline = Instant.now().minusSeconds(3600).plusNanos(900_000_000);
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, pastDeadline);

        mockMvc.perform(put(PROJECTS + "/" + project.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Still Editable", 2, 4, 1, pastDeadline)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("Still Editable"))
                .andExpect(jsonPath("$.data.preferencesLocked").value(true));
    }

    @Test
    @DisplayName("Publishing is owner-only, draft-only and idempotent")
    void publishProject_enforcesOwnershipAndStatus() throws Exception {
        User owner = saveUser("publish.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("publish.other@college.edu", UserRole.MENTOR);
        Project draft = saveProject(owner, ProjectStatus.DRAFT, null);

        mockMvc.perform(post(PROJECTS + "/" + draft.getId() + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(post(PROJECTS + "/" + draft.getId() + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        mockMvc.perform(post(PROJECTS + "/" + draft.getId() + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"));

        Project locked = saveProject(owner, ProjectStatus.PREFERENCES_LOCKED, null);
        mockMvc.perform(post(PROJECTS + "/" + locked.getId() + "/publish")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Locking is owner-only, idempotent and maps DRAFT/PUBLISHED to PREFERENCES_LOCKED")
    void lockPreferences_isIdempotentAndMapsStatus() throws Exception {
        User owner = saveUser("lock.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("lock.other@college.edu", UserRole.MENTOR);
        Project project = saveProject(owner, ProjectStatus.PUBLISHED, null);

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/lock-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        MvcResult first = mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/lock-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.preferencesLocked").value(true))
                .andExpect(jsonPath("$.data.preferencesLockedAt").isNotEmpty())
                .andExpect(jsonPath("$.data.status").value("PREFERENCES_LOCKED"))
                .andReturn();

        String lockedAt = com.jayway.jsonpath.JsonPath.read(
                first.getResponse().getContentAsString(), "$.data.preferencesLockedAt");

        MvcResult second = mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/lock-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PREFERENCES_LOCKED"))
                .andReturn();

        String lockedAtAgain = com.jayway.jsonpath.JsonPath.read(
                second.getResponse().getContentAsString(), "$.data.preferencesLockedAt");
        Instant storedLock = projectRepository.findById(project.getId()).orElseThrow().getPreferencesLockedAt();
        assertThat(lockedAt).isNotBlank();
        assertThat(storedLock).isNotNull();
        assertThat(Instant.parse(lockedAtAgain)).isEqualTo(storedLock);
    }

    @Test
    @DisplayName("Locking a project in a later lifecycle state keeps that state")
    void lockPreferences_keepsLaterStatus() throws Exception {
        User mentor = saveUser("later.status@college.edu", UserRole.MENTOR);
        Project project = saveProject(mentor, ProjectStatus.FORMED, null);

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/lock-preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("FORMED"))
                .andExpect(jsonPath("$.data.preferencesLocked").value(true));
    }

    @Test
    @DisplayName("Two concurrent lock requests both succeed and leave one lock timestamp")
    void lockPreferences_concurrentRequestsAreIdempotent() throws Exception {
        User mentor = saveUser("concurrent.mentor@college.edu", UserRole.MENTOR);
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        String authorization = bearer(mentor);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            List<Future<Integer>> futures = new ArrayList<>();
            for (int request = 0; request < 2; request++) {
                futures.add(executor.submit(() -> {
                    start.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/lock-preferences")
                                    .header(HttpHeaders.AUTHORIZATION, authorization))
                            .andReturn()
                            .getResponse()
                            .getStatus();
                }));
            }
            start.countDown();
            for (Future<Integer> future : futures) {
                assertThat(future.get(30, TimeUnit.SECONDS)).isEqualTo(200);
            }
        } finally {
            executor.shutdownNow();
        }

        Project stored = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(stored.getPreferencesLockedAt()).isNotNull();
        assertThat(stored.getStatus()).isEqualTo(ProjectStatus.PREFERENCES_LOCKED);
    }

    @Test
    @DisplayName("A mentor lists only their own projects with an optional status filter and paging")
    void listProjects_mentorSeesOwnProjects() throws Exception {
        User mentor = saveUser("list.mentor@college.edu", UserRole.MENTOR);
        User other = saveUser("list.other@college.edu", UserRole.MENTOR);
        saveProject(mentor, ProjectStatus.DRAFT, null);
        saveProject(mentor, ProjectStatus.PUBLISHED, null);
        saveProject(mentor, ProjectStatus.FORMED, null);
        saveProject(other, ProjectStatus.PUBLISHED, null);

        mockMvc.perform(get(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.content.length()").value(3));

        mockMvc.perform(get(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"));

        mockMvc.perform(get(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .param("page", "1")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));
    }

    @Test
    @DisplayName("A student lists non-draft projects they are rostered for; an admin is forbidden")
    void listProjects_studentSeesVisibleRosteredProjects() throws Exception {
        User mentor = saveUser("visible.mentor@college.edu", UserRole.MENTOR);
        User student = saveUser("visible.student@college.edu", UserRole.STUDENT);
        User admin = saveUser("visible.admin@college.edu", UserRole.ADMIN);

        Project draft = saveProject(mentor, ProjectStatus.DRAFT, null);
        Project published = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        Project locked = saveProject(mentor, ProjectStatus.PREFERENCES_LOCKED, null);
        Project notRostered = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(draft, student);
        addRoster(published, student);
        addRoster(locked, student);

        mockMvc.perform(get(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(published.getId())).exists())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(locked.getId())).exists())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(draft.getId())).doesNotExist())
                .andExpect(jsonPath("$.data.content[?(@.id == %d)]".formatted(notRostered.getId())).doesNotExist());

        mockMvc.perform(get(PROJECTS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Single-project visibility is owner-only for mentors and roster-based for students")
    void getProject_enforcesVisibility() throws Exception {
        User mentor = saveUser("get.mentor@college.edu", UserRole.MENTOR);
        User otherMentor = saveUser("get.other@college.edu", UserRole.MENTOR);
        User student = saveUser("get.student@college.edu", UserRole.STUDENT);
        User admin = saveUser("get.admin@college.edu", UserRole.ADMIN);

        Project draft = saveProject(mentor, ProjectStatus.DRAFT, null);
        Project published = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        Project locked = saveProject(mentor, ProjectStatus.PREFERENCES_LOCKED, null);
        Project publishedNotRostered = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(draft, student);
        addRoster(published, student);
        addRoster(locked, student);

        mockMvc.perform(get(PROJECTS + "/" + draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROJECTS + "/" + draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherMentor)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PROJECTS + "/" + draft.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PROJECTS + "/" + published.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROJECTS + "/" + locked.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk());
        mockMvc.perform(get(PROJECTS + "/" + publishedNotRostered.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PROJECTS + "/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(PROJECTS + "/" + published.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());
    }
}
