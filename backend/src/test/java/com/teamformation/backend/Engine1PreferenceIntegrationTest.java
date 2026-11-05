package com.teamformation.backend;

import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.TeammatePreference;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engine 1 preference submission and confidential review, including rank
 * replacement, validation, locking and visibility.
 */
class Engine1PreferenceIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Test
    @DisplayName("A rostered student replaces their ranked preferences in request order")
    void replacePreferences_storesRankedChoices() throws Exception {
        User mentor = saveUser("pref.mentor@college.edu", UserRole.MENTOR);
        User student = saveStudent("pref.student@college.edu", "02FE24BCS601");
        User firstChoice = saveStudent("pref.first@college.edu", "02FE24BCS602");
        User secondChoice = saveStudent("pref.second@college.edu", "02FE24BCS603");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        addRoster(project, student, firstChoice, secondChoice);

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(firstChoice.getId(), secondChoice.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[0].preferredStudentId").value(firstChoice.getId()))
                .andExpect(jsonPath("$.data[0].preferredStudentSrn").value("02FE24BCS602"))
                .andExpect(jsonPath("$.data[1].rank").value(2))
                .andExpect(jsonPath("$.data[1].preferredStudentId").value(secondChoice.getId()));

        assertThat(teammatePreferenceRepository
                .findByProjectIdAndStudentIdOrderByRankAsc(project.getId(), student.getId()))
                .extracting(TeammatePreference::getRank)
                .containsExactly(1, 2);
    }

    @Test
    @DisplayName("Replacing preferences removes the old ranks and can swap the order")
    void replacePreferences_replacesWholeSet() throws Exception {
        User mentor = saveUser("replace.mentor@college.edu", UserRole.MENTOR);
        User student = saveStudent("replace.student@college.edu", "02FE24BCS611");
        User first = saveStudent("replace.first@college.edu", "02FE24BCS612");
        User second = saveStudent("replace.second@college.edu", "02FE24BCS613");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        addRoster(project, student, first, second);

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(first.getId(), second.getId())))
                .andExpect(status().isOk());

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(second.getId(), first.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].preferredStudentId").value(second.getId()))
                .andExpect(jsonPath("$.data[1].preferredStudentId").value(first.getId()));

        assertThat(teammatePreferenceRepository
                .findByProjectIdAndStudentIdOrderByRankAsc(project.getId(), student.getId()))
                .hasSize(2)
                .allSatisfy(preference -> assertThat(preference.getStudent().getId()).isEqualTo(student.getId()));
    }

    @Test
    @DisplayName("An empty preference list clears all choices")
    void replacePreferences_emptyListClears() throws Exception {
        User mentor = saveUser("clear.mentor@college.edu", UserRole.MENTOR);
        User student = saveStudent("clear.student@college.edu", "02FE24BCS621");
        User preferred = saveStudent("clear.preferred@college.edu", "02FE24BCS622");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        addRoster(project, student, preferred);
        teammatePreferenceRepository.saveAndFlush(
                new TeammatePreference(project, student, preferred, 1));

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredStudentIds\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        assertThat(teammatePreferenceRepository
                .findByProjectIdAndStudentIdOrderByRankAsc(project.getId(), student.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Preference payloads reject more than two, duplicates, self and non-roster choices")
    void replacePreferences_validatesChoices() throws Exception {
        User mentor = saveUser("pref.validation@college.edu", UserRole.MENTOR);
        User student = saveStudent("pref.validation.student@college.edu", "02FE24BCS631");
        User first = saveStudent("pref.validation.first@college.edu", "02FE24BCS632");
        User second = saveStudent("pref.validation.second@college.edu", "02FE24BCS633");
        User third = saveStudent("pref.validation.third@college.edu", "02FE24BCS634");
        User outside = saveStudent("pref.validation.outside@college.edu", "02FE24BCS635");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        addRoster(project, student, first, second, third);

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(first.getId(), second.getId(), third.getId())))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(first.getId(), first.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("only once")));

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(student.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("themselves")));

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(outside.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not on this project's roster")));

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredStudentIds\":[null]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"preferredStudentIds\":[],\"studentId\":999}"))
                .andExpect(status().isBadRequest());

        assertThat(teammatePreferenceRepository
                .findByProjectIdAndStudentIdOrderByRankAsc(project.getId(), student.getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Preference submission requires a rostered student on a published, unlocked project")
    void replacePreferences_enforcesRoleProjectAndRoster() throws Exception {
        User mentor = saveUser("pref.rules@college.edu", UserRole.MENTOR);
        User rostered = saveStudent("pref.rules.rostered@college.edu", "02FE24BCS641");
        User outside = saveStudent("pref.rules.outside@college.edu", "02FE24BCS642");
        User preferred = saveStudent("pref.rules.preferred@college.edu", "02FE24BCS643");
        Project draft = saveProject(mentor, ProjectStatus.DRAFT, Instant.now().plusSeconds(3600));
        Project published = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        addRoster(draft, rostered, preferred);
        addRoster(published, preferred);

        mockMvc.perform(put(PROJECTS + "/" + published.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(preferred.getId())))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(PROJECTS + "/999999/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rostered))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(preferred.getId())))
                .andExpect(status().isNotFound());

        mockMvc.perform(put(PROJECTS + "/" + draft.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(rostered))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(preferred.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not published")));

        mockMvc.perform(put(PROJECTS + "/" + published.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outside))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(preferencesJson(preferred.getId())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("not on this project's roster")));
    }

    @Test
    @DisplayName("A manual or deadline lock rejects preference changes with 409")
    void replacePreferences_rejectsLockedProject() throws Exception {
        User mentor = saveUser("pref.locked@college.edu", UserRole.MENTOR);
        User student = saveStudent("pref.locked.student@college.edu", "02FE24BCS651");
        User preferred = saveStudent("pref.locked.preferred@college.edu", "02FE24BCS652");
        Project manual = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        manual.setPreferencesLockedAt(Instant.now());
        projectRepository.saveAndFlush(manual);
        Project deadline = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().minusSeconds(60));
        addRoster(manual, student, preferred);
        addRoster(deadline, student, preferred);

        for (Project project : new Project[] {manual, deadline}) {
            mockMvc.perform(put(PROJECTS + "/" + project.getId() + "/preferences")
                            .header(HttpHeaders.AUTHORIZATION, bearer(student))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(preferencesJson(preferred.getId())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("locked")));
        }
    }

    @Test
    @DisplayName("A student reads only their own preferences while the owning mentor reads all")
    void preferences_areConfidentialButReviewableByOwner() throws Exception {
        User mentor = saveUser("confidential.mentor@college.edu", UserRole.MENTOR);
        User otherMentor = saveUser("confidential.other@college.edu", UserRole.MENTOR);
        User first = saveStudent("confidential.first@college.edu", "02FE24BCS661");
        User second = saveStudent("confidential.second@college.edu", "02FE24BCS662");
        User third = saveStudent("confidential.third@college.edu", "02FE24BCS663");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().plusSeconds(3600));
        addRoster(project, first, second, third);
        teammatePreferenceRepository.saveAndFlush(new TeammatePreference(project, first, second, 1));
        teammatePreferenceRepository.saveAndFlush(new TeammatePreference(project, first, third, 2));
        teammatePreferenceRepository.saveAndFlush(new TeammatePreference(project, second, first, 1));

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/preferences/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].studentId").value(first.getId()))
                .andExpect(jsonPath("$.data[0].rank").value(1))
                .andExpect(jsonPath("$.data[1].rank").value(2));

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/preferences/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(second)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].studentId").value(second.getId()));

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .param("page", "0")
                        .param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2));

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/preferences")
                        .header(HttpHeaders.AUTHORIZATION, bearer(otherMentor)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Students cannot read preferences for a draft project or a project they are not on")
    void getMyPreferences_enforcesVisibility() throws Exception {
        User mentor = saveUser("pref.visibility@college.edu", UserRole.MENTOR);
        User student = saveStudent("pref.visibility.student@college.edu", "02FE24BCS671");
        User outsider = saveStudent("pref.visibility.outside@college.edu", "02FE24BCS672");
        Project draft = saveProject(mentor, ProjectStatus.DRAFT, null);
        Project published = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(draft, student);
        addRoster(published, student);

        mockMvc.perform(get(PROJECTS + "/" + draft.getId() + "/preferences/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PROJECTS + "/" + published.getId() + "/preferences/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(outsider)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PROJECTS + "/999999/preferences/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Preferences remain readable by the student after the project is locked")
    void getMyPreferences_visibleAfterLock() throws Exception {
        User mentor = saveUser("pref.afterlock@college.edu", UserRole.MENTOR);
        User student = saveStudent("pref.afterlock.student@college.edu", "02FE24BCS681");
        User preferred = saveStudent("pref.afterlock.preferred@college.edu", "02FE24BCS682");
        Project project = saveProject(mentor, ProjectStatus.PREFERENCES_LOCKED, null);
        project.setPreferencesLockedAt(Instant.now());
        projectRepository.saveAndFlush(project);
        addRoster(project, student, preferred);
        teammatePreferenceRepository.saveAndFlush(new TeammatePreference(project, student, preferred, 1));

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/preferences/me")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));
    }

    private static String preferencesJson(Long... preferredStudentIds) {
        StringBuilder json = new StringBuilder("{\"preferredStudentIds\":[");
        for (int index = 0; index < preferredStudentIds.length; index++) {
            if (index > 0) {
                json.append(',');
            }
            json.append(preferredStudentIds[index]);
        }
        return json.append("]}").toString();
    }
}
