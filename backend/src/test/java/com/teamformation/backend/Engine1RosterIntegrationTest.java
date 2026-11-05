package com.teamformation.backend;

import com.teamformation.backend.entity.CandidateTeam;
import com.teamformation.backend.entity.CandidateTeamMember;
import com.teamformation.backend.entity.FinalizedTeam;
import com.teamformation.backend.entity.FinalizedTeamMembership;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.TeammatePreference;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.MembershipSource;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.FinalizedTeamRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.time.Instant;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engine 1 roster behaviour: all-or-nothing SRN resolution, idempotent adds,
 * ownership, locking and guarded removal.
 */
class Engine1RosterIntegrationTest extends AbstractIntegrationTest {

    private static final String PROJECTS = "/api/v1/projects";

    @Autowired
    private FinalizedTeamRepository finalizedTeamRepository;

    @Test
    @DisplayName("A mentor adds verified students by SRN, collapsing case-insensitive duplicates")
    void addToRoster_addsVerifiedStudentsAndCollapsesDuplicates() throws Exception {
        User mentor = saveUser("roster.mentor@college.edu", UserRole.MENTOR);
        User first = saveStudent("roster.first@college.edu", "02FE24BCS401");
        User second = saveStudent("roster.second@college.edu", "02FE24BCS402");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rosterJson("02fe24bcs401", " 02FE24BCS401 ", "02FE24BCS402")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.added.length()").value(2))
                .andExpect(jsonPath("$.data.added[0].srn").value("02FE24BCS401"))
                .andExpect(jsonPath("$.data.added[0].name").value(first.getFullName()))
                .andExpect(jsonPath("$.data.added[1].srn").value("02FE24BCS402"))
                .andExpect(jsonPath("$.data.alreadyOnRoster.length()").value(0));

        assertThat(projectRosterRepository.findByProjectId(project.getId()))
                .extracting(entry -> entry.getStudent().getId())
                .containsExactlyInAnyOrder(first.getId(), second.getId());
    }

    @Test
    @DisplayName("A roster request with any unknown or unverified SRN adds nobody and reports every offender")
    void addToRoster_isAllOrNothing() throws Exception {
        User mentor = saveUser("allornothing.mentor@college.edu", UserRole.MENTOR);
        User valid = saveStudent("allornothing.valid@college.edu", "02FE24BCS411");
        User unverified = saveInactiveStudent("allornothing.unverified@college.edu", "02FE24BCS412");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rosterJson("02FE24BCS411", "02fe24bcs499", "02FE24BCS412")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.invalidSrns", containsInAnyOrder("02FE24BCS499", "02FE24BCS412")))
                .andExpect(jsonPath("$.message").value(containsString("existing, verified student")));

        assertThat(projectRosterRepository.findByProjectId(project.getId())).isEmpty();
        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), valid.getId())).isFalse();
        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), unverified.getId())).isFalse();
    }

    @Test
    @DisplayName("Adding an already rostered student is reported without creating a duplicate")
    void addToRoster_reportsAlreadyRostered() throws Exception {
        User mentor = saveUser("already.mentor@college.edu", UserRole.MENTOR);
        User existing = saveStudent("already.existing@college.edu", "02FE24BCS421");
        User added = saveStudent("already.added@college.edu", "02FE24BCS422");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, existing);

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(rosterJson("02FE24BCS421", "02FE24BCS422")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.added.length()").value(1))
                .andExpect(jsonPath("$.data.added[0].srn").value("02FE24BCS422"))
                .andExpect(jsonPath("$.data.alreadyOnRoster[0]").value("02FE24BCS421"));

        assertThat(projectRosterRepository.findByProjectId(project.getId())).hasSize(2);
        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), added.getId())).isTrue();
    }

    @Test
    @DisplayName("Only the owning mentor can add to a roster")
    void addToRoster_enforcesRoleAndOwnership() throws Exception {
        User owner = saveUser("roster.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("roster.other@college.edu", UserRole.MENTOR);
        User student = saveStudent("roster.caller@college.edu", "02FE24BCS431");
        Project project = saveProject(owner, ProjectStatus.PUBLISHED, null);
        String body = rosterJson("02FE24BCS431");

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isNotFound());

        assertThat(projectRosterRepository.findByProjectId(project.getId())).isEmpty();
    }

    @Test
    @DisplayName("The roster is immutable after a manual or deadline lock")
    void addToRoster_rejectsLockedProject() throws Exception {
        User mentor = saveUser("locked.roster@college.edu", UserRole.MENTOR);
        User student = saveStudent("locked.roster.student@college.edu", "02FE24BCS441");
        Project manual = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        manual.setPreferencesLockedAt(Instant.now());
        projectRepository.saveAndFlush(manual);
        Project deadline = saveProject(mentor, ProjectStatus.PUBLISHED, Instant.now().minusSeconds(60));

        for (Project project : new Project[] {manual, deadline}) {
            mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                            .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(rosterJson("02FE24BCS441")))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.message").value(containsString("locked")));
        }

        assertThat(projectRosterRepository.findByProjectId(manual.getId())).isEmpty();
        assertThat(projectRosterRepository.findByProjectId(deadline.getId())).isEmpty();
    }

    @Test
    @DisplayName("An empty or blank SRN list is rejected before any lookup")
    void addToRoster_validatesRequest() throws Exception {
        User mentor = saveUser("roster.validation@college.edu", UserRole.MENTOR);
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"srns\":[]}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"srns\":[\"  \"]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("The owning mentor pages the roster with SRN, name and email")
    void getRoster_returnsPagedEntriesForOwnerOnly() throws Exception {
        User owner = saveUser("list.roster@college.edu", UserRole.MENTOR);
        User other = saveUser("list.roster.other@college.edu", UserRole.MENTOR);
        User first = saveStudent("list.roster.first@college.edu", "02FE24BCS451");
        User second = saveStudent("list.roster.second@college.edu", "02FE24BCS452");
        Project project = saveProject(owner, ProjectStatus.PUBLISHED, null);
        addRoster(project, first, second);

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(owner))
                        .param("page", "0")
                        .param("size", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(2))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.content[0].srn").isNotEmpty())
                .andExpect(jsonPath("$.data.content[0].email").isNotEmpty());

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get(PROJECTS + "/" + project.getId() + "/roster")
                        .header(HttpHeaders.AUTHORIZATION, bearer(first)))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Removing a rostered student succeeds before locking")
    void removeFromRoster_removesEntry() throws Exception {
        User mentor = saveUser("remove.mentor@college.edu", UserRole.MENTOR);
        User student = saveStudent("remove.student@college.edu", "02FE24BCS461");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, student);

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isOk());

        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), student.getId())).isFalse();
    }

    @Test
    @DisplayName("Removing an unknown student or removing after a lock is rejected")
    void removeFromRoster_validatesExistingEntryAndLock() throws Exception {
        User mentor = saveUser("remove.locked@college.edu", UserRole.MENTOR);
        User student = saveStudent("remove.locked.student@college.edu", "02FE24BCS471");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, student);

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/999999")
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isNotFound());

        project.setPreferencesLockedAt(Instant.now());
        projectRepository.saveAndFlush(project);

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict());

        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), student.getId())).isTrue();
    }

    @Test
    @DisplayName("Removal is blocked while the student has a submitted preference")
    void removeFromRoster_blocksWhenPreferenceExists() throws Exception {
        User mentor = saveUser("block.pref@college.edu", UserRole.MENTOR);
        User student = saveStudent("block.pref.student@college.edu", "02FE24BCS481");
        User preferred = saveStudent("block.pref.other@college.edu", "02FE24BCS482");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, student, preferred);
        teammatePreferenceRepository.saveAndFlush(
                new TeammatePreference(project, student, preferred, 1));

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(containsString("preferences or team memberships")));

        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), student.getId())).isTrue();
    }

    @Test
    @DisplayName("Removal is blocked while the student is in a candidate team")
    void removeFromRoster_blocksWhenCandidateMembershipExists() throws Exception {
        User mentor = saveUser("block.candidate@college.edu", UserRole.MENTOR);
        User student = saveStudent("block.candidate.student@college.edu", "02FE24BCS491");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, student);
        FormationJob job = formationJobRepository.saveAndFlush(new FormationJob(project, 1, mentor));
        CandidateTeam team = candidateTeamRepository.saveAndFlush(new CandidateTeam(project, job, "Team A"));
        candidateTeamMemberRepository.saveAndFlush(
                new CandidateTeamMember(team, student, job.getId(), MembershipSource.ALGORITHM));

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict());

        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), student.getId())).isTrue();
    }

    @Test
    @DisplayName("Removal is blocked while the student is in a finalized team")
    void removeFromRoster_blocksWhenFinalizedMembershipExists() throws Exception {
        User mentor = saveUser("block.finalized@college.edu", UserRole.MENTOR);
        User student = saveStudent("block.finalized.student@college.edu", "02FE24BCS501");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        addRoster(project, student);
        FinalizedTeam team = finalizedTeamRepository.saveAndFlush(
                new FinalizedTeam(project, "Final Team", mentor));
        finalizedTeamMembershipRepository.saveAndFlush(
                new FinalizedTeamMembership(team, project, student, MembershipSource.ALGORITHM));

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isConflict());

        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), student.getId())).isTrue();
    }

    @Test
    @DisplayName("Only the owning mentor can remove a student")
    void removeFromRoster_enforcesOwnershipAndRole() throws Exception {
        User owner = saveUser("remove.owner@college.edu", UserRole.MENTOR);
        User other = saveUser("remove.other@college.edu", UserRole.MENTOR);
        User student = saveStudent("remove.caller@college.edu", "02FE24BCS511");
        Project project = saveProject(owner, ProjectStatus.PUBLISHED, null);
        addRoster(project, student);

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete(PROJECTS + "/" + project.getId() + "/roster/" + student.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(other)))
                .andExpect(status().isNotFound());

        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(
                project.getId(), student.getId())).isTrue();
    }

    private User saveInactiveStudent(String email, String srn) {
        User user = new User(email, "Inactive Student", passwordEncoder.encode(PASSWORD), UserRole.STUDENT);
        user.setActive(false);
        user = userRepository.saveAndFlush(user);
        studentProfileRepository.saveAndFlush(new StudentProfile(user, srn, "CSE", 5, null));
        return user;
    }

    private static String rosterJson(String... srns) {
        return "{\"srns\":[\""
                + String.join("\",\"", Arrays.stream(srns).map(String::trim).toList())
                + "\"]}";
    }
}
