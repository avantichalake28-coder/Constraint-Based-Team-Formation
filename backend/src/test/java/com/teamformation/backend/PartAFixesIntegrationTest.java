package com.teamformation.backend;

import com.teamformation.backend.entity.CandidateTeam;
import com.teamformation.backend.entity.CandidateTeamMember;
import com.teamformation.backend.entity.EmailVerificationToken;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.MembershipSource;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.EmailVerificationTokenRepository;
import com.teamformation.backend.security.service.TokenHasher;
import com.teamformation.backend.security.service.VerificationResendRateLimiter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Part A regression coverage: resend-verification behaviour, the POST
 * verification form, commit-after-mail semantics and the formation-job delete
 * cascade.
 */
class PartAFixesIntegrationTest extends AbstractIntegrationTest {

    private static final String RESEND_URL = "/api/v1/auth/resend-verification";
    private static final String VERIFY_URL = "/api/v1/auth/verify-email";

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private VerificationResendRateLimiter resendRateLimiter;

    @MockitoBean
    private JavaMailSender mailSender;

    private final List<SimpleMailMessage> sentMessages = new ArrayList<>();

    @BeforeEach
    void setUpMail() {
        sentMessages.clear();
        resendRateLimiter.clearAll();
        doAnswer(invocation -> {
            sentMessages.add(invocation.getArgument(0));
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
    }

    @Test
    @DisplayName("Resend replaces the token, invalidates the old link and sends one message")
    void resend_replacesTokenAndInvalidatesOldLink() throws Exception {
        User student = saveInactiveStudent("resend.student@college.edu", "02FE24BCS301");
        String oldToken = "old-verification-token";
        tokenRepository.saveAndFlush(new EmailVerificationToken(
                student, TokenHasher.sha256Hex(oldToken), Instant.now().plusSeconds(3600)));

        mockMvc.perform(post(RESEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendJson("resend.student@college.edu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value(containsString("If the address belongs")));

        assertThat(sentMessages).hasSize(1);
        String newToken = tokenFrom(sentMessages.getFirst());

        EmailVerificationToken oldStored = tokenRepository.findAll().stream()
                .filter(token -> token.getTokenHash().equals(TokenHasher.sha256Hex(oldToken)))
                .findFirst()
                .orElseThrow();
        assertThat(oldStored.getUsedAt()).isNotNull();

        mockMvc.perform(get(VERIFY_URL).param("token", oldToken))
                .andExpect(status().isBadRequest());

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + newToken + "\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(student.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    @DisplayName("Resend for an unknown address returns the same generic response and sends nothing")
    void resend_unknownAddress_isGenericAndSendsNothing() throws Exception {
        mockMvc.perform(post(RESEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendJson("nobody@college.edu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("If the address belongs")));

        assertThat(sentMessages).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("Resend for an already verified account returns the same generic response and sends nothing")
    void resend_verifiedAccount_isGenericAndSendsNothing() throws Exception {
        saveUser("verified.student@college.edu", UserRole.STUDENT, true);

        mockMvc.perform(post(RESEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendJson("verified.student@college.edu")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("If the address belongs")));

        assertThat(sentMessages).isEmpty();
        assertThat(tokenRepository.count()).isZero();
    }

    @Test
    @DisplayName("Resend is throttled after three requests in one hour even for an unknown address")
    void resend_fourthRequestIsThrottled() throws Exception {
        for (int request = 1; request <= 3; request++) {
            mockMvc.perform(post(RESEND_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(resendJson("throttle@college.edu")))
                    .andExpect(status().isOk());
        }

        mockMvc.perform(post(RESEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendJson("THROTTLE@COLLEGE.EDU")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value(containsString("Too many verification email requests")));
    }

    @Test
    @DisplayName("The POST verification form activates an account and rejects a missing token")
    void verifyEmailPost_activatesAndValidatesBody() throws Exception {
        User student = saveInactiveStudent("post.verify@college.edu", "02FE24BCS302");
        String rawToken = "post-verification-token";
        tokenRepository.saveAndFlush(new EmailVerificationToken(
                student, TokenHasher.sha256Hex(rawToken), Instant.now().plusSeconds(3600)));

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"" + rawToken + "\"}"))
                .andExpect(status().isOk());

        assertThat(userRepository.findById(student.getId()).orElseThrow().isActive()).isTrue();

        mockMvc.perform(post(VERIFY_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.token").exists());
    }

    @Test
    @DisplayName("A mail failure does not roll back registration or hide the account")
    void registration_mailFailureKeepsAccount() throws Exception {
        doThrow(new MailSendException("smtp unavailable"))
                .when(mailSender).send(any(SimpleMailMessage.class));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Mail Failure",
                                  "email": "mail.failure@college.edu",
                                  "password": "%s",
                                  "srn": "02FE24BCS303"
                                }
                                """.formatted(PASSWORD)))
                .andExpect(status().isCreated());

        User saved = userRepository.findByEmailIgnoreCase("mail.failure@college.edu").orElseThrow();
        assertThat(saved.isActive()).isFalse();
        assertThat(studentProfileRepository.findByUserId(saved.getId())).isPresent();
        assertThat(tokenRepository.count()).isEqualTo(1);
    }

    @Test
    @DisplayName("Deleting a formation job cascades to its candidate teams and members")
    void deleteFormationJob_cascadesCandidateTeamsAndMembers() {
        User mentor = saveUser("cascade.mentor@college.edu", UserRole.MENTOR);
        User student = saveStudent("cascade.student@college.edu", "02FE24BCS304");
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        FormationJob job = formationJobRepository.saveAndFlush(
                new FormationJob(project, 1, mentor));
        CandidateTeam team = candidateTeamRepository.saveAndFlush(
                new CandidateTeam(project, job, "Team A"));
        candidateTeamMemberRepository.saveAndFlush(
                new CandidateTeamMember(team, student, job.getId(), MembershipSource.ALGORITHM));

        jdbcTemplate.update("DELETE FROM formation_jobs WHERE id = ?", job.getId());

        assertThat(formationJobRepository.count()).isZero();
        assertThat(candidateTeamRepository.count()).isZero();
        assertThat(candidateTeamMemberRepository.count()).isZero();
    }

    @Test
    @DisplayName("Deleting a project cascades to its formation jobs")
    void deleteProject_cascadesFormationJobs() {
        User mentor = saveUser("project.cascade@college.edu", UserRole.MENTOR);
        Project project = saveProject(mentor, ProjectStatus.PUBLISHED, null);
        formationJobRepository.saveAndFlush(new FormationJob(project, 1, mentor));

        jdbcTemplate.update("DELETE FROM projects WHERE id = ?", project.getId());

        assertThat(formationJobRepository.count()).isZero();
    }

    @Test
    @DisplayName("Resend rejects malformed email bodies without creating state")
    void resend_rejectsInvalidEmail() throws Exception {
        mockMvc.perform(post(RESEND_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(resendJson("not-an-email")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.email").exists());

        assertThat(sentMessages).isEmpty();
    }

    private User saveInactiveStudent(String email, String srn) {
        User user = new User(email, "Inactive Student", passwordEncoder.encode(PASSWORD), UserRole.STUDENT);
        user.setActive(false);
        user = userRepository.saveAndFlush(user);
        studentProfileRepository.saveAndFlush(new StudentProfile(user, srn, "CSE", 5, null));
        return user;
    }

    private static String resendJson(String email) {
        return "{\"email\":\"" + email + "\"}";
    }

    private static String tokenFrom(SimpleMailMessage message) {
        String body = message.getText();
        int start = body.indexOf("token=");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int valueStart = start + "token=".length();
        int end = body.indexOf('\n', valueStart);
        return body.substring(valueStart, end < 0 ? body.length() : end).trim();
    }
}
