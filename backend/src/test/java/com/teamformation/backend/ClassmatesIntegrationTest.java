package com.teamformation.backend;

import com.jayway.jsonpath.JsonPath;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * The student-facing classmates projection: only other rostered students on a
 * non-draft project, with id, name and SRN but no contact or skill data.
 */
class ClassmatesIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Test
    @DisplayName("A rostered student pages through classmates without seeing themself")
    void getClassmates_excludesCallerAndPages() throws Exception {
        User mentor = saveUser("classmates.mentor@college.edu", UserRole.MENTOR);
        User caller = saveStudent("classmates.caller@college.edu", "02FE24BCS851");
        User first = saveStudent("classmates.first@college.edu", "02FE24BCS852");
        User second = saveStudent("classmates.second@college.edu", "02FE24BCS853");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, caller, first, second);

        mockMvc.perform(get(classmatesPath(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content.length()").value(1))
                .andExpect(jsonPath("$.data.content[0].studentId").value(not(caller.getId().intValue())))
                .andExpect(jsonPath("$.data.content[0].fullName").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].srn").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].email").doesNotExist())
                .andExpect(jsonPath("$.data.content[0].skills").doesNotExist());

        mockMvc.perform(get(classmatesPath(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(caller))
                        .param("page", "0")
                        .param("size", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].studentId", containsInAnyOrder(
                        first.getId().intValue(), second.getId().intValue())))
                .andExpect(jsonPath("$.data.content[*].srn", containsInAnyOrder(
                        "02FE24BCS852", "02FE24BCS853")));
    }

    @Test
    @DisplayName("A classmate entry exposes exactly student id, full name and SRN")
    void getClassmates_exposesExactlyThreeFields() throws Exception {
        User mentor = saveUser("classmates.fields.mentor@college.edu", UserRole.MENTOR);
        User caller = saveStudent("classmates.fields.caller@college.edu", "02FE24BCS854");
        User other = saveStudent("classmates.fields.other@college.edu", "02FE24BCS855");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, caller, other);

        MvcResult result = mockMvc.perform(get(classmatesPath(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isOk())
                .andReturn();

        Map<String, Object> entry = JsonPath.read(
                result.getResponse().getContentAsString(), "$.data.content[0]");
        assertThat(entry).containsOnlyKeys("studentId", "fullName", "srn");
    }

    @Test
    @DisplayName("A student alone on the roster gets an empty classmate page")
    void getClassmates_onlyCallerOnRoster() throws Exception {
        User mentor = saveUser("classmates.alone.mentor@college.edu", UserRole.MENTOR);
        User caller = saveStudent("classmates.alone@college.edu", "02FE24BCS861");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, caller);

        mockMvc.perform(get(classmatesPath(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(0))
                .andExpect(jsonPath("$.data.content.length()").value(0));
    }

    @Test
    @DisplayName("A draft project, a missing project and a non-rostered student are all 404")
    void getClassmates_isInvisibleOutsideTheVisibleRoster() throws Exception {
        User mentor = saveUser("classmates.visibility@college.edu", UserRole.MENTOR);
        User rostered = saveStudent("classmates.visibility.rostered@college.edu", "02FE24BCS871");
        User outsider = saveStudent("classmates.visibility.outsider@college.edu", "02FE24BCS872");
        Project draft = saveProject(mentor, ProjectStatus.DRAFT, null);
        Project published = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(draft, rostered);
        addRoster(published, rostered);

        mockMvc.perform(get(classmatesPath(draft))
                        .header(HttpHeaders.AUTHORIZATION, bearer(rostered)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(classmatesPath(published))
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PROJECTS + "/999999/classmates")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rostered)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Classmates remain visible after preferences are locked")
    void getClassmates_visibleAfterPreferencesLocked() throws Exception {
        User mentor = saveUser("classmates.locked@college.edu", UserRole.MENTOR);
        User caller = saveStudent("classmates.locked.caller@college.edu", "02FE24BCS881");
        User other = saveStudent("classmates.locked.other@college.edu", "02FE24BCS882");
        Project project = saveProject(mentor, ProjectStatus.PREFERENCES_LOCKED, null);
        addRoster(project, caller, other);

        mockMvc.perform(get(classmatesPath(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(caller)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[*].studentId",
                        hasItem(other.getId().intValue())));
    }

    @Test
    @DisplayName("Mentors are forbidden and unauthenticated callers are rejected")
    void getClassmates_enforcesRoleAndAuthentication() throws Exception {
        User mentor = saveUser("classmates.role.mentor@college.edu", UserRole.MENTOR);
        User admin = saveUser("classmates.role.admin@college.edu", UserRole.ADMIN);
        User caller = saveStudent("classmates.role.caller@college.edu", "02FE24BCS891");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, caller);

        mockMvc.perform(get(classmatesPath(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(classmatesPath(project))
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(classmatesPath(project)))
                .andExpect(status().isUnauthorized());
    }

    private static String classmatesPath(Project project) {
        return PROJECTS + "/" + project.getId() + "/classmates";
    }
}
