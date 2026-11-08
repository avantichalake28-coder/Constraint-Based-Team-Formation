package com.teamformation.backend;

import com.jayway.jsonpath.JsonPath;
import com.teamformation.backend.engine.formation.FormationEngine;
import com.teamformation.backend.engine.formation.FormationInput;
import com.teamformation.backend.engine.formation.FormationSearchLimits;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves that a second run is rejected while the first run is still executing
 * inside its T2 transaction. The real engine is wrapped in a spy so the test can
 * hold the first run inside the job execution window.
 */
class Engine3FormationConcurrencyIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @MockitoSpyBean
    private FormationEngine formationEngine;

    @Test
    @DisplayName("A second run receives 409 while the first run is still RUNNING")
    void startFormation_rejectsConcurrentRunWhileActive() throws Exception {
        User mentor = saveUser("formation.concurrent@college.edu", UserRole.MENTOR);
        Project project = saveFormationProject(
                mentor, ProjectStatus.PREFERENCES_LOCKED, 2, 2, 0);
        project.setPreferencesLockedAt(Instant.now());
        project = projectRepository.saveAndFlush(project);
        Long projectId = project.getId();
        List<User> students = saveStudents(4, "formation.concurrent.student", 200);
        addRoster(project, students.toArray(User[]::new));

        CountDownLatch enteredEngine = new CountDownLatch(1);
        CountDownLatch releaseEngine = new CountDownLatch(1);
        doAnswer(invocation -> {
            enteredEngine.countDown();
            if (!releaseEngine.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("formation engine was never released");
            }
            return invocation.callRealMethod();
        }).when(formationEngine).form(any(FormationInput.class), any(FormationSearchLimits.class));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<MvcResult> first = executor.submit(() -> mockMvc.perform(
                            post(PROJECTS + "/" + projectId + "/formation-jobs")
                                    .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                    .andReturn());

            assertThat(enteredEngine.await(20, TimeUnit.SECONDS))
                    .as("the first run must reach the engine while its job is RUNNING")
                    .isTrue();

            mockMvc.perform(post(PROJECTS + "/" + projectId + "/formation-jobs")
                            .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message", containsString("READY or RUNNING")));

            releaseEngine.countDown();
            MvcResult firstResult = first.get(30, TimeUnit.SECONDS);
            assertThat(firstResult.getResponse().getStatus()).isEqualTo(201);
            assertThat((String) JsonPath.read(
                    firstResult.getResponse().getContentAsString(), "$.data.jobStatus"))
                    .isEqualTo("COMPLETED");
        } finally {
            releaseEngine.countDown();
            executor.shutdownNow();
        }

        assertThat(formationJobRepository.count()).isEqualTo(1);
        assertThat(candidateTeamRepository.count()).isEqualTo(2);
        assertThat(candidateTeamMemberRepository.count()).isEqualTo(4);
    }
}
