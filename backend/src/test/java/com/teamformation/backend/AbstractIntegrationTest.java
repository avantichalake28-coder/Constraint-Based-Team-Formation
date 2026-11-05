package com.teamformation.backend;

import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectRoster;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.repository.CandidateTeamMemberRepository;
import com.teamformation.backend.repository.CandidateTeamRepository;
import com.teamformation.backend.repository.FinalizedTeamMembershipRepository;
import com.teamformation.backend.repository.FormationJobRepository;
import com.teamformation.backend.repository.ProjectRepository;
import com.teamformation.backend.repository.ProjectRosterRepository;
import com.teamformation.backend.repository.SkillRepository;
import com.teamformation.backend.repository.StudentProfileRepository;
import com.teamformation.backend.repository.StudentSkillRepository;
import com.teamformation.backend.repository.TeammatePreferenceRepository;
import com.teamformation.backend.repository.UserRepository;
import com.teamformation.backend.security.authentication.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

/**
 * Common non-transactional integration test setup. Each test commits its own
 * rows (so post-commit mail and pessimistic locks behave like production) and
 * the mutable tables are truncated before the next test.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    protected static final String PASSWORD = "SuperSecret123";

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected JdbcTemplate jdbcTemplate;

    @Autowired
    protected UserRepository userRepository;

    @Autowired
    protected StudentProfileRepository studentProfileRepository;

    @Autowired
    protected SkillRepository skillRepository;

    @Autowired
    protected StudentSkillRepository studentSkillRepository;

    @Autowired
    protected ProjectRepository projectRepository;

    @Autowired
    protected ProjectRosterRepository projectRosterRepository;

    @Autowired
    protected TeammatePreferenceRepository teammatePreferenceRepository;

    @Autowired
    protected FormationJobRepository formationJobRepository;

    @Autowired
    protected CandidateTeamRepository candidateTeamRepository;

    @Autowired
    protected CandidateTeamMemberRepository candidateTeamMemberRepository;

    @Autowired
    protected FinalizedTeamMembershipRepository finalizedTeamMembershipRepository;

    @Autowired
    protected PasswordEncoder passwordEncoder;

    @Autowired
    protected JwtService jwtService;

    @BeforeEach
    void cleanMutableTables() {
        TestDatabaseCleaner.clean(jdbcTemplate);
    }

    protected User saveUser(String email, UserRole role) {
        return saveUser(email, role, true);
    }

    protected User saveUser(String email, UserRole role, boolean active) {
        User user = new User(email, "Test " + role, passwordEncoder.encode(PASSWORD), role);
        user.setActive(active);
        return userRepository.saveAndFlush(user);
    }

    protected User saveStudent(String email, String srn) {
        User user = saveUser(email, UserRole.STUDENT);
        studentProfileRepository.saveAndFlush(
                new StudentProfile(user, srn, "CSE", 5, null));
        return user;
    }

    protected String token(User user) {
        return jwtService.generateAccessToken(user);
    }

    protected String bearer(User user) {
        return "Bearer " + token(user);
    }

    protected Project saveProject(User mentor, ProjectStatus status, Instant deadline) {
        Project project = new Project(
                mentor, "Project", "Problem statement", "CS301", 2, 4, 1, deadline);
        project.setStatus(status);
        return projectRepository.saveAndFlush(project);
    }

    protected void addRoster(Project project, User... students) {
        for (User student : students) {
            projectRosterRepository.saveAndFlush(
                    new ProjectRoster(project, student, RosterEligibilityStatus.ELIGIBLE));
        }
    }

    protected static String projectJson(String title, Integer min, Integer max, Integer slack, Instant deadline) {
        String deadlineValue = deadline == null ? "null" : "\"" + deadline + "\"";
        return """
                {
                  "title": "%s",
                  "problemStatement": "Problem statement",
                  "courseCode": "CS301",
                  "minTeamSize": %d,
                  "maxTeamSize": %d,
                  "teamSizeSlack": %d,
                  "preferenceDeadline": %s
                }
                """.formatted(title, min, max, slack, deadlineValue);
    }
}
