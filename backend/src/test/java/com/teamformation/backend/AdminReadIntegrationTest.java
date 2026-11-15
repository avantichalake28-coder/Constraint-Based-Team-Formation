package com.teamformation.backend;

import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.BusinessException;
import com.teamformation.backend.service.AdminReadService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminReadIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private AdminReadService adminReadService;

    private static final String ADMIN_API = "/api/v1/admin";
    private static final List<String> ADMIN_READ_PATHS = List.of(
            ADMIN_API + "/students",
            ADMIN_API + "/mentors",
            ADMIN_API + "/projects",
            ADMIN_API + "/overview");

    @Test
    @DisplayName("Admin read endpoints reject students, mentors and unauthenticated callers")
    void adminReadEndpoints_enforceAccessForEveryEndpoint() throws Exception {
        User student = saveStudent("admin.read.student@college.edu", "02FE24BCS141");
        User mentor = saveUser("admin.read.mentor@college.edu", UserRole.MENTOR);

        for (String path : ADMIN_READ_PATHS) {
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(student)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                    .andExpect(status().isForbidden());
            mockMvc.perform(get(path))
                    .andExpect(status().isUnauthorized());
        }

        assertAdminServiceRejects(student);
        assertAdminServiceRejects(mentor);
    }

    @Test
    @DisplayName("Admin directory searches, pages and returns only explicit safe DTO fields")
    void adminDirectories_searchPageAndExcludeSensitiveFields() throws Exception {
        User admin = saveUser("admin.read.admin@college.edu", UserRole.ADMIN);
        User alice = saveStudent("alice.admin.read@college.edu", "02FE24BCS141");
        User bob = saveStudent("bob.admin.read@college.edu", "02FE24BCS142");
        User carol = saveStudent("carol.admin.read@college.edu", "02FE24BCS143");
        alice.setFullName("Alice AdminTest");
        bob.setFullName("Bob AdminTest");
        carol.setFullName("Carol AdminTest");
        userRepository.saveAllAndFlush(List.of(alice, bob, carol));
        StudentProfile aliceProfile = studentProfileRepository.findByUserId(alice.getId()).orElseThrow();
        aliceProfile.setInterests("private profile data");
        studentProfileRepository.saveAndFlush(aliceProfile);

        User mentorOne = saveUser("mentor.one.admin.read@college.edu", UserRole.MENTOR);
        User mentorTwo = saveUser("mentor.two.admin.read@college.edu", UserRole.MENTOR);
        mentorOne.setFullName("Mentor Alpha");
        mentorTwo.setFullName("Mentor Beta");
        userRepository.saveAllAndFlush(List.of(mentorOne, mentorTwo));

        Project published = saveProject(mentorOne, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        published.setTitle("Research Capstone");
        published.setPreferencesLockedAt(Instant.now());
        projectRepository.saveAndFlush(published);
        Project draft = saveProject(mentorTwo, ProjectStatus.DRAFT, null);
        draft.setTitle("Draft Study");
        projectRepository.saveAndFlush(draft);
        addRoster(published, alice);

        FormationJob olderJob = new FormationJob(published, 1, mentorOne);
        olderJob.setJobStatus(JobStatus.COMPLETED);
        formationJobRepository.saveAndFlush(olderJob);
        FormationJob latestJob = new FormationJob(published, 1, mentorOne);
        latestJob.setJobStatus(JobStatus.FAILED);
        formationJobRepository.saveAndFlush(latestJob);

        String studentResult = mockMvc.perform(get(ADMIN_API + "/students")
                        .param("search", "ALICE ADMIN")
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(alice.getId()))
                .andExpect(jsonPath("$.data.content[0].srn").value("02FE24BCS141"))
                .andExpect(jsonPath("$.data.content[0].department").value("CSE"))
                .andExpect(jsonPath("$.data.content[0].semester").value(5))
                .andExpect(jsonPath("$.data.content[0].active").value(true))
                .andExpect(jsonPath("$.data.content[0].projectCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(studentResult);

        String studentSrnSearch = mockMvc.perform(get(ADMIN_API + "/students")
                        .param("search", "02FE24BCS142")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(bob.getId()))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(studentSrnSearch);

        String studentEmailSearch = mockMvc.perform(get(ADMIN_API + "/students")
                        .param("search", "CAROL.ADMIN.READ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(carol.getId()))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(studentEmailSearch);

        String studentPage = mockMvc.perform(get(ADMIN_API + "/students")
                        .param("page", "1")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(3))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(studentPage);

        String mentorNameSearch = mockMvc.perform(get(ADMIN_API + "/mentors")
                        .param("search", "ALPHA")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(mentorOne.getId()))
                .andExpect(jsonPath("$.data.content[0].projectCount").value(1))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(mentorNameSearch);

        String mentorEmailSearch = mockMvc.perform(get(ADMIN_API + "/mentors")
                        .param("search", "MENTOR.TWO.ADMIN.READ")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(mentorTwo.getId()))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(mentorEmailSearch);

        String mentorPage = mockMvc.perform(get(ADMIN_API + "/mentors")
                        .param("page", "1")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(mentorPage);

        String projectResult = mockMvc.perform(get(ADMIN_API + "/projects")
                        .param("status", "PUBLISHED")
                        .param("page", "0")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(published.getId()))
                .andExpect(jsonPath("$.data.content[0].title").value("Research Capstone"))
                .andExpect(jsonPath("$.data.content[0].mentorName").value("Mentor Alpha"))
                .andExpect(jsonPath("$.data.content[0].status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.content[0].rosterSize").value(1))
                .andExpect(jsonPath("$.data.content[0].locked").value(true))
                .andExpect(jsonPath("$.data.content[0].latestFormationJobStatus").value("FAILED"))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(projectResult);

        String projectDraftResult = mockMvc.perform(get(ADMIN_API + "/projects")
                        .param("status", "DRAFT")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(draft.getId()))
                .andExpect(jsonPath("$.data.content[0].title").value("Draft Study"))
                .andExpect(jsonPath("$.data.content[0].status").value("DRAFT"))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(projectDraftResult);

        String projectCompletedResult = mockMvc.perform(get(ADMIN_API + "/projects")
                        .param("status", "COMPLETED")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(projectCompletedResult);

        String projectPage = mockMvc.perform(get(ADMIN_API + "/projects")
                        .param("page", "1")
                        .param("size", "1")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(projectPage);

        String overview = mockMvc.perform(get(ADMIN_API + "/overview")
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.studentCount").value(3))
                .andExpect(jsonPath("$.data.mentorCount").value(2))
                .andExpect(jsonPath("$.data.projectsByStatus.DRAFT").value(1))
                .andExpect(jsonPath("$.data.projectsByStatus.PUBLISHED").value(1))
                .andExpect(jsonPath("$.data.projectsByStatus.COMPLETED").value(0))
                .andExpect(jsonPath("$.data.formationJobsByStatus.COMPLETED").value(1))
                .andExpect(jsonPath("$.data.formationJobsByStatus.FAILED").value(1))
                .andReturn().getResponse().getContentAsString();
        assertResponseHasNoSensitiveFields(overview);

        mockMvc.perform(get("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentorOne)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].id").value(published.getId()));
    }

    @Test
    @DisplayName("Admin cannot write projects, rosters, preferences, or constraints")
    void existingProjectMutations_remainUnavailableToAdmins() throws Exception {
        User admin = saveUser("admin.write.admin@college.edu", UserRole.ADMIN);
        User mentor = saveUser("admin.write.mentor@college.edu", UserRole.MENTOR);
        User student = saveStudent("admin.write.student@college.edu", "02FE24BCS151");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        addRoster(project, student);
        String auth = bearer(admin);

        mockMvc.perform(post("/api/v1/projects")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Blocked", 2, 4, 1, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/projects/" + project.getId())
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(projectJson("Blocked", 2, 4, 1, null)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/v1/projects/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"srns\":[\"02FE24BCS151\"]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/v1/projects/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/projects/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredStudentIds\":[]}"))
                .andExpect(status().isForbidden());
        mockMvc.perform(put("/api/v1/projects/" + project.getId() + "/constraints")
                        .header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"prohibitedPairs\":[],\"mandatorySkills\":[]}"))
                .andExpect(status().isForbidden());
    }

    private void assertResponseHasNoSensitiveFields(String response) {
        assertThat(response)
                .doesNotContain("password")
                .doesNotContain("token")
                .doesNotContain("interests")
                .doesNotContain("preferences");
    }

    private void assertAdminServiceRejects(User user) {
        Jwt jwt = Jwt.withTokenValue("service-test")
                .header("alg", "none")
                .subject(user.getId().toString())
                .build();
        Authentication authentication = new JwtAuthenticationToken(jwt);

        assertForbidden(() -> adminReadService.getStudents(authentication, null, PageRequest.of(0, 20)));
        assertForbidden(() -> adminReadService.getMentors(authentication, null, PageRequest.of(0, 20)));
        assertForbidden(() -> adminReadService.getProjects(authentication, null, PageRequest.of(0, 20)));
        assertForbidden(() -> adminReadService.getOverview(authentication));
    }

    private void assertForbidden(Runnable action) {
        assertThatThrownBy(action::run)
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getHttpStatus())
                .isEqualTo(HttpStatus.FORBIDDEN);
    }
}
