package com.teamformation.backend;

import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Engine 1 student profile, skill catalogue and own-skill selection.
 */
class Engine1ProfileSkillIntegrationTest extends AbstractIntegrationTest {

    private static final String STUDENT_ME = "/api/v1/students/me";
    private static final String STUDENT_SKILLS = "/api/v1/students/me/skills";
    private static final String SKILLS = "/api/v1/skills";

    @Test
    @DisplayName("A student reads their own profile; a mentor is forbidden")
    void getProfile_returnsOwnIdentity() throws Exception {
        User student = saveStudent("profile.student@college.edu", "02FE24BCS701");
        User mentor = saveUser("profile.mentor@college.edu", UserRole.MENTOR);

        mockMvc.perform(get(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.srn").value("02FE24BCS701"))
                .andExpect(jsonPath("$.data.name").value(student.getFullName()))
                .andExpect(jsonPath("$.data.email").value("profile.student@college.edu"))
                .andExpect(jsonPath("$.data.department").value("CSE"));

        mockMvc.perform(get(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get(STUDENT_ME))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A student updates editable fields while SRN and email stay read-only")
    void updateProfile_changesOnlyEditableFields() throws Exception {
        User student = saveStudent("update.profile@college.edu", "02FE24BCS711");

        mockMvc.perform(put(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "  Updated Name  ",
                                  "department": "  ",
                                  "semester": 6,
                                  "interests": "  distributed systems  "
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"))
                .andExpect(jsonPath("$.data.srn").value("02FE24BCS711"))
                .andExpect(jsonPath("$.data.email").value("update.profile@college.edu"))
                .andExpect(jsonPath("$.data.department").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.data.semester").value(6))
                .andExpect(jsonPath("$.data.interests").value("distributed systems"));

        assertThat(userRepository.findById(student.getId()).orElseThrow().getFullName())
                .isEqualTo("Updated Name");
    }

    @Test
    @DisplayName("Profile updates reject SRN/email fields, blank names and overlong names")
    void updateProfile_rejectsImmutableAndInvalidFields() throws Exception {
        User student = saveStudent("immutable.profile@college.edu", "02FE24BCS721");

        mockMvc.perform(put(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Hacked Name",
                                  "srn": "02FE24BCS999"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Hacked Name",
                                  "email": "hacked@college.edu"
                                }
                                """))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("must not be blank")));

        mockMvc.perform(put(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fullName\":\"" + "a".repeat(151) + "\"}"))
                .andExpect(status().isBadRequest());

        User stored = userRepository.findById(student.getId()).orElseThrow();
        assertThat(stored.getFullName()).isEqualTo("Test STUDENT");
        assertThat(stored.getEmail()).isEqualTo("immutable.profile@college.edu");
        assertThat(studentProfileRepository.findByUserId(student.getId()).orElseThrow().getSrn())
                .isEqualTo("02FE24BCS721");
    }

    @Test
    @DisplayName("A student account without a profile receives 404 for profile and skills")
    void profileEndpoints_requireStudentProfile() throws Exception {
        User studentOnly = saveUser("no.profile@college.edu", UserRole.STUDENT);

        mockMvc.perform(get(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentOnly)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(STUDENT_ME)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentOnly))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"semester\":4}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(studentOnly)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("All authenticated roles can read the skill catalogue, but only mentors and admins create")
    void skillCatalogue_enforcesRolesAndValidation() throws Exception {
        User student = saveStudent("skills.student@college.edu", "02FE24BCS731");
        User mentor = saveUser("skills.mentor@college.edu", UserRole.MENTOR);
        User admin = saveUser("skills.admin@college.edu", UserRole.ADMIN);
        skillRepository.saveAndFlush(new Skill("Java", "LANGUAGE", "JVM language"));

        mockMvc.perform(get(SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.content[0].name").value("Java"));

        mockMvc.perform(get(SKILLS))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post(SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  SQL  \",\"category\":\" DATABASE \",\"description\":\"  Queries  \"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.name").value("SQL"))
                .andExpect(jsonPath("$.data.category").value("DATABASE"))
                .andExpect(jsonPath("$.data.description").value("Queries"));

        mockMvc.perform(post(SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Python\"}"))
                .andExpect(status().isCreated());

        mockMvc.perform(post(SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Blocked\"}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post(SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\" java \"}"))
                .andExpect(status().isConflict());

        mockMvc.perform(post(SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Rust\",\"phone\":\"9876543210\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("A student replaces their whole skill set and can clear it")
    void studentSkills_replaceAndClear() throws Exception {
        User student = saveStudent("student.skills@college.edu", "02FE24BCS741");
        Skill javaSkill = skillRepository.saveAndFlush(new Skill("Java", "LANGUAGE", null));
        Skill sqlSkill = skillRepository.saveAndFlush(new Skill("SQL", "DATABASE", null));
        Skill pythonSkill = skillRepository.saveAndFlush(new Skill("Python", "LANGUAGE", null));

        mockMvc.perform(get(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(javaSkill.getId(), 5, sqlSkill.getId(), 3)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].skillName").value("Java"))
                .andExpect(jsonPath("$.data[0].proficiency").value(5))
                .andExpect(jsonPath("$.data[1].skillName").value("SQL"))
                .andExpect(jsonPath("$.data[1].proficiency").value(3));

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(pythonSkill.getId(), 4)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].skillName").value("Python"));

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(0));

        assertThat(studentSkillRepository.findByStudentProfileId(
                studentProfileRepository.findByUserId(student.getId()).orElseThrow().getId()))
                .isEmpty();
    }

    @Test
    @DisplayName("Invalid skill updates leave the previous set untouched")
    void studentSkills_invalidUpdatesAreAtomic() throws Exception {
        User student = saveStudent("atomic.skills@college.edu", "02FE24BCS751");
        Skill javaSkill = skillRepository.saveAndFlush(new Skill("Java", "LANGUAGE", null));

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(javaSkill.getId(), 5)))
                .andExpect(status().isOk());

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(999999L, 4)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Unknown skill ids")));

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(javaSkill.getId(), 5, javaSkill.getId(), 3)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Duplicate skill id")));

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(javaSkill.getId(), 0)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(javaSkill.getId(), 6)))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(student))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[{\"skillId\":" + javaSkill.getId() + ",\"proficiency\":null}]}"))
                .andExpect(status().isBadRequest());

        assertThat(studentSkillRepository.findByStudentProfileId(
                studentProfileRepository.findByUserId(student.getId()).orElseThrow().getId()))
                .hasSize(1)
                .allSatisfy(skill -> {
                    assertThat(skill.getSkill().getName()).isEqualTo("Java");
                    assertThat(skill.getProficiencyLevel()).isEqualTo(5);
                });
    }

    @Test
    @DisplayName("Only students can manage the student skill set")
    void studentSkills_requireStudentRole() throws Exception {
        User mentor = saveUser("skills.caller.mentor@college.edu", UserRole.MENTOR);
        Skill skill = skillRepository.saveAndFlush(new Skill("Java", "LANGUAGE", null));

        mockMvc.perform(get(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor)))
                .andExpect(status().isForbidden());

        mockMvc.perform(put(STUDENT_SKILLS)
                        .header(HttpHeaders.AUTHORIZATION, bearer(mentor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(skillsJson(skill.getId(), 4)))
                .andExpect(status().isForbidden());
    }

    private static String skillsJson(Object... skillIdAndProficiencyPairs) {
        StringBuilder json = new StringBuilder("{\"skills\":[");
        for (int index = 0; index < skillIdAndProficiencyPairs.length; index += 2) {
            if (index > 0) {
                json.append(',');
            }
            json.append("{\"skillId\":")
                    .append(skillIdAndProficiencyPairs[index])
                    .append(",\"proficiency\":")
                    .append(skillIdAndProficiencyPairs[index + 1])
                    .append('}');
        }
        return json.append("]}").toString();
    }
}
