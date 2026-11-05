package com.teamformation.backend.security;

import com.teamformation.backend.TestDatabaseCleaner;
import com.teamformation.backend.TestcontainersConfiguration;
import com.teamformation.backend.entity.EmailVerificationToken;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.EmailVerificationTokenRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.authentication.JwtService;
import com.teamformation.backend.security.service.LoginAttemptService;
import com.teamformation.backend.security.service.TokenHasher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end security tests against a real MySQL 8.0 container. Mail is
 * replaced by a mock so the verification token can be read from the outgoing
 * message without contacting an SMTP server.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@ExtendWith(OutputCaptureExtension.class)
class AuthIntegrationTest {

    private static final String VALID_PASSWORD = "SuperSecret123";
    private static final String REGISTER_URL = "/api/v1/auth/register";
    private static final String LOGIN_URL = "/api/v1/auth/login";
    private static final String VERIFY_URL = "/api/v1/auth/verify-email";
    private static final String ME_URL = "/api/v1/auth/me";
    private static final String ADMIN_MENTORS_URL = "/api/v1/admin/mentors";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private EmailVerificationTokenRepository tokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private JavaMailSender mailSender;

    private final List<SimpleMailMessage> sentMessages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        TestDatabaseCleaner.clean(jdbcTemplate);
        loginAttemptService.clearAll();
        sentMessages.clear();
        doAnswer(invocation -> {
            sentMessages.add(invocation.getArgument(0));
            return null;
        }).when(mailSender).send(any(SimpleMailMessage.class));
    }

    // ------------------------------------------------------------------
    // Registration
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Registration creates an inactive student profile and stores only the token hash")
    void register_success_createsInactiveStudentAndHashedToken() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student One", "student.one@college.edu", VALID_PASSWORD, "02FE24BCS141")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.email").value("student.one@college.edu"));

        User saved = userRepository.findByEmailIgnoreCase("student.one@college.edu").orElseThrow();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(studentProfileRepository.findByUserId(saved.getId()))
                .get()
                .extracting(StudentProfile::getSrn)
                .isEqualTo("02FE24BCS141");

        String rawToken = latestVerificationToken();
        assertThat(rawToken).isNotBlank();

        EmailVerificationToken stored = tokenRepository.findAll().getFirst();
        assertThat(stored.getTokenHash())
                .hasSize(64)
                .isNotEqualTo(rawToken)
                .isEqualTo(TokenHasher.sha256Hex(rawToken));
        assertThat(stored.getUsedAt()).isNull();
        assertThat(stored.getExpiresAt()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("Registration rejects an email outside the configured domain")
    void register_rejectsEmailOutsideAllowedDomain() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student Two", "student@gmail.com", VALID_PASSWORD, "02FE24BCS142")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("college.edu")));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Registration rejects an SRN that does not match the required pattern")
    void register_rejectsInvalidSrn() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student Three", "student.three@college.edu", VALID_PASSWORD, "INVALID-SRN")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("SRN")));

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Registration rejects a password shorter than ten characters")
    void register_rejectsShortPassword() throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student Four", "student.four@college.edu", "short", "02FE24BCS144")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.validationErrors.password").exists());

        assertThat(userRepository.count()).isZero();
    }

    @Test
    @DisplayName("Registration rejects a duplicate email differing only in letter case")
    void register_rejectsDuplicateEmailIgnoringCase() throws Exception {
        register("Student Five", "student.five@college.edu", VALID_PASSWORD, "02FE24BCS151");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student Five", "STUDENT.FIVE@COLLEGE.EDU", VALID_PASSWORD, "02FE24BCS152")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("An account with the provided details already exists"));
    }

    @Test
    @DisplayName("Registration rejects a duplicate SRN after normalizing it to uppercase")
    void register_rejectsDuplicateSrn() throws Exception {
        register("Student Six", "student.six@college.edu", VALID_PASSWORD, "02FE24BCS161");

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student Seven", "student.seven@college.edu", VALID_PASSWORD, "02fe24bcs161")))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("Registration rejects phone and role JSON fields and persists nothing")
    void register_rejectsUnknownPhoneAndRoleFieldsWithoutPersisting() throws Exception {
        String body = """
                {
                  "fullName": "Student Eight",
                  "email": "student.eight@college.edu",
                  "password": "%s",
                  "srn": "02FE24BCS181",
                  "phone": "9876543210",
                  "role": "ADMIN"
                }
                """.formatted(VALID_PASSWORD);

        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());

        assertThat(userRepository.count()).isZero();
        assertThat(studentProfileRepository.count()).isZero();
        assertThat(tokenRepository.count()).isZero();
    }

    // ------------------------------------------------------------------
    // Email verification
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Verification activates the account and marks the token as used")
    void verifyEmail_activatesAccountAndMarksTokenUsed() throws Exception {
        register("Student Nine", "student.nine@college.edu", VALID_PASSWORD, "02FE24BCS191");
        String token = latestVerificationToken();

        mockMvc.perform(get(VERIFY_URL).param("token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        User activated = userRepository.findByEmailIgnoreCase("student.nine@college.edu").orElseThrow();
        assertThat(activated.isActive()).isTrue();
        assertThat(tokenRepository.findAll().getFirst().getUsedAt()).isNotNull();
    }

    @Test
    @DisplayName("Verification rejects an expired token")
    void verifyEmail_rejectsExpiredToken() throws Exception {
        User user = new User("student.ten@college.edu", "Student Ten",
                passwordEncoder.encode(VALID_PASSWORD), UserRole.STUDENT);
        user.setActive(false);
        user = userRepository.saveAndFlush(user);

        String rawToken = "already-expired-token";
        tokenRepository.saveAndFlush(new EmailVerificationToken(
                user, TokenHasher.sha256Hex(rawToken), Instant.now().minusSeconds(3600)));

        mockMvc.perform(get(VERIFY_URL).param("token", rawToken))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invalid")));

        assertThat(userRepository.findByEmailIgnoreCase("student.ten@college.edu").orElseThrow().isActive())
                .isFalse();
    }

    @Test
    @DisplayName("Verification rejects a token that was already used")
    void verifyEmail_rejectsReusedToken() throws Exception {
        register("Student Eleven", "student.eleven@college.edu", VALID_PASSWORD, "02FE24BCS211");
        String token = latestVerificationToken();

        mockMvc.perform(get(VERIFY_URL).param("token", token)).andExpect(status().isOk());
        mockMvc.perform(get(VERIFY_URL).param("token", token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invalid")));
    }

    @Test
    @DisplayName("Verification rejects an unknown token")
    void verifyEmail_rejectsUnknownToken() throws Exception {
        mockMvc.perform(get(VERIFY_URL).param("token", "not-a-real-token"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("invalid")));
    }

    // ------------------------------------------------------------------
    // Login
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Login rejects an unverified account only after the password is correct")
    void login_rejectsUnverifiedAccountAfterCorrectPassword() throws Exception {
        register("Student Twelve", "student.twelve@college.edu", VALID_PASSWORD, "02FE24BCS221");

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("student.twelve@college.edu", VALID_PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message", containsString("verify your email")));
    }

    @Test
    @DisplayName("Login returns the generic credential error for a wrong password")
    void login_rejectsWrongPasswordWithGenericMessage() throws Exception {
        saveUser("active.student@college.edu", UserRole.STUDENT, true);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("active.student@college.edu", "WrongPassword123")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("Login returns a 60-minute JWT for an active account")
    void login_returnsJwtForActiveAccount() throws Exception {
        saveUser("jwt.student@college.edu", UserRole.STUDENT, true);

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("jwt.student@college.edu", VALID_PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresInSeconds").value(3600));
    }

    @Test
    @DisplayName("The sixth failed login attempt is throttled with 429")
    void login_blocksSixthFailedAttemptWithTooManyRequests() throws Exception {
        saveUser("throttled.student@college.edu", UserRole.STUDENT, true);

        for (int attempt = 1; attempt <= 5; attempt++) {
            mockMvc.perform(post(LOGIN_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(loginJson("throttled.student@college.edu", "WrongPassword123")))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("throttled.student@college.edu", "WrongPassword123")))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message", containsString("Too many failed login attempts")));
    }

    // ------------------------------------------------------------------
    // JWT-protected endpoints
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A protected endpoint without a token returns 401 in the standard JSON format")
    void protectedEndpoint_withoutTokenReturns401() throws Exception {
        mockMvc.perform(get(ME_URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.error").value("Unauthorized"))
                .andExpect(jsonPath("$.path").value(ME_URL));
    }

    @Test
    @DisplayName("An expired token is rejected with 401")
    void protectedEndpoint_withExpiredTokenReturns401() throws Exception {
        User user = saveUser("expired.student@college.edu", UserRole.STUDENT, true);
        String token = jwtService.generateAccessToken(
                user, Instant.now().minusSeconds(7200), Instant.now().minusSeconds(3600));

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A tampered token signature is rejected with 401")
    void protectedEndpoint_withTamperedTokenReturns401() throws Exception {
        User user = saveUser("tampered.student@college.edu", UserRole.STUDENT, true);
        String token = jwtService.generateAccessToken(user);
        char replacement = token.charAt(token.length() - 1) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, token.length() - 1) + replacement;

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(tampered)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("A deactivated user's token is rejected immediately with 401")
    void protectedEndpoint_rejectsTokenOfDeactivatedUser() throws Exception {
        User user = saveUser("deactivated.student@college.edu", UserRole.STUDENT, true);
        String token = jwtService.generateAccessToken(user);
        user.setActive(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("/me returns the student's identity and SRN")
    void me_returnsStudentSrn() throws Exception {
        User user = saveUser("me.student@college.edu", UserRole.STUDENT, true);
        studentProfileRepository.saveAndFlush(new StudentProfile(user, "02FE24BCS241", "CSE", 5, null));
        String token = jwtService.generateAccessToken(user);

        mockMvc.perform(get(ME_URL).header(HttpHeaders.AUTHORIZATION, bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.getId()))
                .andExpect(jsonPath("$.data.name").value(user.getFullName()))
                .andExpect(jsonPath("$.data.email").value("me.student@college.edu"))
                .andExpect(jsonPath("$.data.role").value("STUDENT"))
                .andExpect(jsonPath("$.data.srn").value("02FE24BCS241"));
    }

    // ------------------------------------------------------------------
    // Admin-only mentor creation
    // ------------------------------------------------------------------

    @Test
    @DisplayName("A student cannot create a mentor and receives 403")
    void adminEndpoint_rejectsStudentWith403() throws Exception {
        User student = saveUser("student.caller@college.edu", UserRole.STUDENT, true);
        String token = jwtService.generateAccessToken(student);

        mockMvc.perform(post(ADMIN_MENTORS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mentorJson("Mentor Blocked", "blocked.mentor@college.edu", VALID_PASSWORD)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("An admin creates an active mentor account without a password in the response")
    void adminEndpoint_createsMentor() throws Exception {
        User admin = saveUser("admin.caller@college.edu", UserRole.ADMIN, true);
        String token = jwtService.generateAccessToken(admin);

        MvcResult result = mockMvc.perform(post(ADMIN_MENTORS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mentorJson("Mentor One", "mentor.one@college.edu", VALID_PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("MENTOR"))
                .andExpect(jsonPath("$.data.email").value("mentor.one@college.edu"))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain(VALID_PASSWORD)
                .doesNotContain("password");

        User mentor = userRepository.findByEmailIgnoreCase("mentor.one@college.edu").orElseThrow();
        assertThat(mentor.getRole()).isEqualTo(UserRole.MENTOR);
        assertThat(mentor.isActive()).isTrue();
        assertThat(studentProfileRepository.findByUserId(mentor.getId())).isEmpty();
    }

    @Test
    @DisplayName("A mentor cannot create another mentor and receives 403")
    void adminEndpoint_rejectsMentorWith403() throws Exception {
        User mentor = saveUser("mentor.caller@college.edu", UserRole.MENTOR, true);
        String token = jwtService.generateAccessToken(mentor);

        mockMvc.perform(post(ADMIN_MENTORS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(mentorJson("Mentor Two", "mentor.two@college.edu", VALID_PASSWORD)))
                .andExpect(status().isForbidden());
    }

    // ------------------------------------------------------------------
    // Secret hygiene
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Passwords and raw verification tokens never appear in responses or logs")
    void responsesAndLogs_neverContainPasswordOrRawVerificationToken(CapturedOutput output) throws Exception {
        String password = "UniqueSecret987";
        MvcResult registration = mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson("Student Secret", "student.secret@college.edu", password, "02FE24BCS251")))
                .andExpect(status().isCreated())
                .andReturn();
        String rawToken = latestVerificationToken();

        MvcResult login = mockMvc.perform(post(LOGIN_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson("student.secret@college.edu", password)))
                .andExpect(status().isUnauthorized())
                .andReturn();

        assertThat(registration.getResponse().getContentAsString())
                .doesNotContain(password)
                .doesNotContain(rawToken);
        assertThat(login.getResponse().getContentAsString())
                .doesNotContain(password)
                .doesNotContain(rawToken);
        assertThat(output.getAll())
                .doesNotContain(password)
                .doesNotContain(rawToken);
    }

    @Test
    @DisplayName("An admin mentor endpoint rejects unexpected JSON fields")
    void adminEndpoint_rejectsUnknownJsonFields() throws Exception {
        User admin = saveUser("admin.fields@college.edu", UserRole.ADMIN, true);
        String token = jwtService.generateAccessToken(admin);

        mockMvc.perform(post(ADMIN_MENTORS_URL)
                        .header(HttpHeaders.AUTHORIZATION, bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "fullName": "Mentor Fields",
                                  "email": "mentor.fields@college.edu",
                                  "initialPassword": "%s",
                                  "phone": "9876543210"
                                }
                                """.formatted(VALID_PASSWORD)))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void register(String fullName, String email, String password, String srn) throws Exception {
        mockMvc.perform(post(REGISTER_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson(fullName, email, password, srn)))
                .andExpect(status().isCreated());
    }

    private String latestVerificationToken() {
        assertThat(sentMessages).isNotEmpty();
        String body = sentMessages.getLast().getText();
        int start = body.indexOf("token=");
        assertThat(start).isGreaterThanOrEqualTo(0);
        int valueStart = start + "token=".length();
        int end = body.indexOf('\n', valueStart);
        return body.substring(valueStart, end < 0 ? body.length() : end).trim();
    }

    private User saveUser(String email, UserRole role, boolean active) {
        User user = new User(email, "Test User", passwordEncoder.encode(VALID_PASSWORD), role);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    private static String registerJson(String fullName, String email, String password, String srn) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "password": "%s",
                  "srn": "%s"
                }
                """.formatted(fullName, email, password, srn);
    }

    private static String loginJson(String email, String password) {
        return """
                {
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(email, password);
    }

    private static String mentorJson(String fullName, String email, String password) {
        return """
                {
                  "fullName": "%s",
                  "email": "%s",
                  "initialPassword": "%s"
                }
                """.formatted(fullName, email, password);
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
