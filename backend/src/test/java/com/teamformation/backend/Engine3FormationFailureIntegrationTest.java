package com.teamformation.backend;

import com.teamformation.backend.engine.formation.persistence.FormationPlanPersister;
import com.teamformation.backend.entity.CandidateTeam;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Proves the post-job failure contract: T2 is rolled back completely and T3
 * records a FAILED job with a safe reason and no candidate rows.
 */
class Engine3FormationFailureIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @MockitoSpyBean
    private FormationPlanPersister formationPlanPersister;

    @Test
    @DisplayName("A persistence failure rolls back T2 and leaves one FAILED job with no candidates")
    void startFormation_postJobFailureRollsBackAndMarksFailed() throws Exception {
        User mentor = saveUser("formation.failure@college.edu", UserRole.MENTOR);
        Project project = saveFormationProject(
                mentor, ProjectStatus.PREFERENCES_LOCKED, 2, 2, 0);
        project.setPreferencesLockedAt(Instant.now());
        project = projectRepository.saveAndFlush(project);
        List<User> students = saveStudents(4, "formation.failure.student", 210);
        addRoster(project, students.toArray(User[]::new));

        doAnswer(invocation -> {
            Project persistedProject = invocation.getArgument(0);
            FormationJob job = invocation.getArgument(1);
            candidateTeamRepository.saveAndFlush(
                    new CandidateTeam(persistedProject, job, "Team 01"));
            throw new IllegalStateException("simulated persistence failure");
        }).when(formationPlanPersister).persist(any(), any(), any());

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/formation-jobs")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message", containsString("internal error")));

        List<FormationJob> jobs = formationJobRepository.findAll();
        assertThat(jobs).hasSize(1);
        FormationJob job = jobs.get(0);
        assertThat(job.getJobStatus()).isEqualTo(JobStatus.FAILED);
        assertThat(job.getErrorMessage())
                .contains("internal error")
                .doesNotContain("simulated")
                .doesNotContain("IllegalStateException");
        assertThat(job.getSeed()).isNotNull();
        assertThat(job.getAlgorithmVersion()).isEqualTo("3.0.0");

        assertThat(candidateTeamRepository.count()).isZero();
        assertThat(candidateTeamMemberRepository.count()).isZero();

        Project reloaded = projectRepository.findById(project.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ProjectStatus.PREFERENCES_LOCKED);
    }
}
