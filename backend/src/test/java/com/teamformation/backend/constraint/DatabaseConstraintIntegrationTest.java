package com.teamformation.backend.constraint;

import com.teamformation.backend.TestcontainersConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.UncategorizedSQLException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

/**
 * Verifies every Task 2.2b database constraint against a real MySQL 8.0
 * instance started by Testcontainers. No H2 and no skipped tests.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@Transactional
class DatabaseConstraintIntegrationTest {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long mentorId;
    private long student1Id;
    private long student2Id;
    private long student3Id;
    private long student4Id;
    private long projectId;
    private long jobId;

    @BeforeEach
    void setUp() {
        mentorId = insertUser("mentor@example.com", "MENTOR");
        student1Id = insertStudent("student1@example.com", "02FE24BCS141");
        student2Id = insertStudent("student2@example.com", "02FE24BCS142");
        student3Id = insertStudent("student3@example.com", "02FE24BCS143");
        student4Id = insertStudent("student4@example.com", "02FE24BCS144");
        projectId = insertProject(2, 4, 1);
        insertRoster(projectId, student1Id);
        insertRoster(projectId, student2Id);
        insertRoster(projectId, student3Id);
        jobId = insertFormationJob(projectId);
    }

    // ------------------------------------------------------------------
    // B. Student registration number (SRN)
    // ------------------------------------------------------------------

    @Test
    @DisplayName("SRN breaking the required pattern is rejected by the database")
    void insertStudentProfile_rejectsSrnBreakingPattern() {
        long userId = insertUser("bad-srn@example.com", "STUDENT");

        assertRejected(() -> jdbcTemplate.update(
                "INSERT INTO student_profiles (user_id, srn, department) VALUES (?, ?, ?)",
                userId, "INVALID-SRN", "CSE"));
    }

    @Test
    @DisplayName("Lowercase SRN is rejected by the case-sensitive database CHECK")
    void insertStudentProfile_rejectsLowercaseSrn() {
        long userId = insertUser("lower-srn@example.com", "STUDENT");

        assertRejected(() -> jdbcTemplate.update(
                "INSERT INTO student_profiles (user_id, srn, department) VALUES (?, ?, ?)",
                userId, "02fe24bcs199", "CSE"));
    }

    @Test
    @DisplayName("Duplicate SRN is rejected")
    void insertStudentProfile_rejectsDuplicateSrn() {
        long userId = insertUser("duplicate-srn@example.com", "STUDENT");

        assertRejected(() -> jdbcTemplate.update(
                "INSERT INTO student_profiles (user_id, srn, department) VALUES (?, ?, ?)",
                userId, "02FE24BCS141", "CSE"));
    }

    @Test
    @DisplayName("Duplicate email differing only in letter case is rejected")
    void insertUser_rejectsDuplicateEmailIgnoringCase() {
        assertRejected(() -> insertUser("STUDENT1@EXAMPLE.COM", "STUDENT"));
    }

    @Test
    @DisplayName("Student profile has a database-enforced unique foreign key to users")
    void studentProfileHasUniqueForeignKeyToUser() {
        Integer foreignKeyCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.KEY_COLUMN_USAGE
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'student_profiles'
                  AND COLUMN_NAME = 'user_id'
                  AND REFERENCED_TABLE_NAME = 'users'
                  AND REFERENCED_COLUMN_NAME = 'id'
                """, Integer.class);
        Integer uniqueIndexCount = jdbcTemplate.queryForObject("""
                SELECT COUNT(DISTINCT INDEX_NAME)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'student_profiles'
                  AND COLUMN_NAME = 'user_id'
                  AND NON_UNIQUE = 0
                """, Integer.class);

        assertThat(foreignKeyCount).isEqualTo(1);
        assertThat(uniqueIndexCount).isEqualTo(1);
    }

    @Test
    @DisplayName("A second student profile for one user is rejected")
    void insertStudentProfile_rejectsSecondProfileForOneUser() {
        assertRejected(() -> jdbcTemplate.update(
                "INSERT INTO student_profiles (user_id, srn, department) VALUES (?, ?, ?)",
                student1Id, "02FE24BCS199", "CSE"));
    }

    // ------------------------------------------------------------------
    // C. Eligibility roster
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Duplicate roster entry is rejected")
    void insertRosterEntry_rejectsDuplicateRosterEntry() {
        assertRejected(() -> insertRoster(projectId, student1Id));
    }

    // ------------------------------------------------------------------
    // D. Teammate preferences
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Preference rank 0 is rejected")
    void insertPreference_rejectsRankZero() {
        assertRejected(() -> insertPreference(projectId, student1Id, student2Id, 0));
    }

    @Test
    @DisplayName("Preference rank 3 is rejected")
    void insertPreference_rejectsRankThree() {
        assertRejected(() -> insertPreference(projectId, student1Id, student2Id, 3));
    }

    @Test
    @DisplayName("Self-preference is rejected")
    void insertPreference_rejectsSelfPreference() {
        assertRejected(() -> insertPreference(projectId, student1Id, student1Id, 1));
    }

    @Test
    @DisplayName("Preferred student who is not on the project roster is rejected")
    void insertPreference_rejectsPreferredStudentNotOnRoster() {
        assertRejected(() -> insertPreference(projectId, student1Id, student4Id, 1));
    }

    @Test
    @DisplayName("Duplicate rank for one student and project is rejected")
    void insertPreference_rejectsDuplicateRank() {
        insertPreference(projectId, student1Id, student2Id, 1);

        assertRejected(() -> insertPreference(projectId, student1Id, student3Id, 1));
    }

    @Test
    @DisplayName("Duplicate preferred student for one student and project is rejected")
    void insertPreference_rejectsDuplicatePreferredStudent() {
        insertPreference(projectId, student1Id, student2Id, 1);

        assertRejected(() -> insertPreference(projectId, student1Id, student2Id, 2));
    }

    // ------------------------------------------------------------------
    // G. Skills
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Proficiency 0 is rejected")
    void insertStudentSkill_rejectsProficiencyZero() {
        long skillId = insertSkill("Java");
        long profileId = profileIdOf(student1Id);

        assertRejected(() -> insertStudentSkill(profileId, skillId, 0));
    }

    @Test
    @DisplayName("Proficiency 6 is rejected")
    void insertStudentSkill_rejectsProficiencySix() {
        long skillId = insertSkill("Java");
        long profileId = profileIdOf(student1Id);

        assertRejected(() -> insertStudentSkill(profileId, skillId, 6));
    }

    @Test
    @DisplayName("Duplicate skill name in a different letter case is rejected")
    void insertSkill_rejectsDuplicateNameInDifferentCase() {
        insertSkill("Java");

        assertRejected(() -> insertSkill("java"));
    }

    @Test
    @DisplayName("Skill name collation is explicitly case-insensitive")
    void skillNameCollationIsCaseInsensitive() {
        String collation = jdbcTemplate.queryForObject("""
                SELECT COLLATION_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'skills'
                  AND COLUMN_NAME = 'name'
                """, String.class);

        assertThat(collation).isEqualTo("utf8mb4_unicode_ci");
        assertThat(collation).endsWith("_ci");
    }

    // ------------------------------------------------------------------
    // A. Team size per project
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Minimum team size below 2 is rejected")
    void insertProject_rejectsMinTeamSizeBelowTwo() {
        assertRejected(() -> insertProject(1, 4, 1));
    }

    @Test
    @DisplayName("Minimum team size greater than maximum is rejected")
    void insertProject_rejectsMinTeamSizeGreaterThanMax() {
        assertRejected(() -> insertProject(5, 4, 1));
    }

    @Test
    @DisplayName("Negative team size slack is rejected")
    void insertProject_rejectsNegativeSlack() {
        assertRejected(() -> insertProject(2, 4, -1));
    }

    // ------------------------------------------------------------------
    // H. Candidate and finalized teams
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Same student in two candidate teams of one formation run is rejected")
    void insertCandidateTeamMember_rejectsSameStudentInTwoTeamsOfOneRun() {
        long teamA = insertCandidateTeam(projectId, jobId, "Team A");
        long teamB = insertCandidateTeam(projectId, jobId, "Team B");
        insertCandidateTeamMember(teamA, jobId, student1Id, "ALGORITHM");

        assertRejected(() -> insertCandidateTeamMember(teamB, jobId, student1Id, "ALGORITHM"));
    }

    @Test
    @DisplayName("Candidate team member source outside ALGORITHM/MANUAL_OVERRIDE is rejected")
    void insertCandidateTeamMember_rejectsInvalidSource() {
        long team = insertCandidateTeam(projectId, jobId, "Team A");

        assertRejected(() -> insertCandidateTeamMember(team, jobId, student1Id, "SOMETHING_ELSE"));
    }

    @Test
    @DisplayName("Same student in two finalized teams of one project is rejected")
    void insertFinalizedTeamMembership_rejectsSameStudentInTwoFinalizedTeamsOfOneProject() {
        long teamA = insertFinalizedTeam(projectId, "Final Team A");
        long teamB = insertFinalizedTeam(projectId, "Final Team B");
        insertFinalizedTeamMembership(teamA, projectId, student1Id, "ALGORITHM");

        assertRejected(() -> insertFinalizedTeamMembership(teamB, projectId, student1Id, "ALGORITHM"));
    }

    @Test
    @DisplayName("Finalized membership for a student outside the project roster is rejected")
    void insertFinalizedTeamMembership_rejectsStudentOutsideRoster() {
        long team = insertFinalizedTeam(projectId, "Final Team A");

        assertRejected(() -> insertFinalizedTeamMembership(team, projectId, student4Id, "ALGORITHM"));
    }

    @Test
    @DisplayName("Candidate team without a formation job is rejected by the NOT NULL column")
    void insertCandidateTeam_rejectsNullFormationJob() {
        assertRejected(() -> jdbcTemplate.update(
                "INSERT INTO candidate_teams (project_id, formation_job_id, team_name, status) VALUES (?, NULL, ?, 'DRAFT')",
                projectId, "Team Without Job"));
    }

    @Test
    @DisplayName("candidate_teams.formation_job_id is NOT NULL in the live schema")
    void candidateTeamFormationJobColumnIsNotNull() {
        String nullable = jdbcTemplate.queryForObject("""
                SELECT IS_NULLABLE
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = 'candidate_teams'
                  AND COLUMN_NAME = 'formation_job_id'
                """, String.class);

        assertThat(nullable).isEqualTo("NO");
    }

    // ------------------------------------------------------------------
    // I. Email verification tokens
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Email verification token persists only the supplied hash and starts unused")
    void insertEmailVerificationToken_persistsHashAndUnusedState() {
        long userId = insertUser("verify@example.com", "STUDENT");
        String hash = "a".repeat(64);

        jdbcTemplate.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY))
                """, userId, hash);

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT token_hash, used_at, created_at FROM email_verification_tokens WHERE user_id = ?",
                userId);
        assertThat(row.get("token_hash")).isEqualTo(hash);
        assertThat(row.get("used_at")).isNull();
        assertThat(row.get("created_at")).isNotNull();
    }

    @Test
    @DisplayName("Duplicate email verification token hash is rejected")
    void insertEmailVerificationToken_rejectsDuplicateHash() {
        long firstUser = insertUser("verify-one@example.com", "STUDENT");
        long secondUser = insertUser("verify-two@example.com", "STUDENT");
        String hash = "b".repeat(64);
        jdbcTemplate.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY))
                """, firstUser, hash);

        assertRejected(() -> jdbcTemplate.update("""
                INSERT INTO email_verification_tokens (user_id, token_hash, expires_at)
                VALUES (?, ?, DATE_ADD(CURRENT_TIMESTAMP, INTERVAL 1 DAY))
                """, secondUser, hash));
    }

    @Test
    @DisplayName("Email verification token without an expiry is rejected")
    void insertEmailVerificationToken_rejectsNullExpiry() {
        long userId = insertUser("verify-no-expiry@example.com", "STUDENT");

        assertRejected(() -> jdbcTemplate.update(
                "INSERT INTO email_verification_tokens (user_id, token_hash, expires_at) VALUES (?, ?, NULL)",
                userId, "c".repeat(64)));
    }

    // ------------------------------------------------------------------
    // General foreign keys and phone-like columns
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Roster entry with a non-existent project is rejected by a foreign key")
    void insertRosterEntry_rejectsForeignKeyViolation() {
        assertRejected(() -> insertRoster(999_999L, student1Id));
    }

    @Test
    @DisplayName("Live schema contains no phone, mobile, contact, or msisdn column")
    void liveSchemaHasNoPhoneLikeColumns() {
        List<Map<String, Object>> columns = jdbcTemplate.queryForList("""
                SELECT TABLE_NAME, COLUMN_NAME
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND (LOWER(COLUMN_NAME) LIKE '%phone%'
                       OR LOWER(COLUMN_NAME) LIKE '%mobile%'
                       OR LOWER(COLUMN_NAME) LIKE '%contact%'
                       OR LOWER(COLUMN_NAME) LIKE '%msisdn%')
                """);

        assertThat(columns).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private void assertRejected(Runnable action) {
        Throwable thrown = catchThrowable(action::run);
        assertThat(thrown).isInstanceOf(DataAccessException.class);

        SQLException sqlException = extractSqlException((DataAccessException) thrown);
        assertThat((Object) sqlException).isNotNull();
        // 1062 duplicate key, 1048 null in NOT NULL column, 1451/1452 foreign key, 3819 CHECK constraint.
        assertThat(sqlException.getErrorCode()).isIn(1048, 1062, 1451, 1452, 3819);
    }

    private SQLException extractSqlException(DataAccessException exception) {
        if (exception instanceof UncategorizedSQLException uncategorized) {
            return uncategorized.getSQLException();
        }
        if (exception.getCause() instanceof SQLException sqlException) {
            return sqlException;
        }
        return null;
    }

    private long insertUser(String email, String role) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO users (email, full_name, password_hash, role, is_active) VALUES (?, ?, ?, ?, TRUE)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, email);
            statement.setString(2, "Test User");
            statement.setString(3, "hashed");
            statement.setString(4, role);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private long insertStudent(String email, String srn) {
        long userId = insertUser(email, "STUDENT");
        jdbcTemplate.update(
                "INSERT INTO student_profiles (user_id, srn, department, semester) VALUES (?, ?, ?, ?)",
                userId, srn, "CSE", 5);
        return userId;
    }

    private long profileIdOf(long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT id FROM student_profiles WHERE user_id = ?", Long.class, userId);
    }

    private long insertProject(int minTeamSize, int maxTeamSize, int slack) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO projects (mentor_id, title, problem_statement, course_code, min_team_size, max_team_size, team_size_slack, status) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, 'DRAFT')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, mentorId);
            statement.setString(2, "Project");
            statement.setString(3, "Problem statement");
            statement.setString(4, "CS301");
            statement.setInt(5, minTeamSize);
            statement.setInt(6, maxTeamSize);
            statement.setInt(7, slack);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertRoster(long project, long student) {
        jdbcTemplate.update(
                "INSERT INTO project_roster (project_id, student_id, eligibility_status) VALUES (?, ?, 'ELIGIBLE')",
                project, student);
    }

    private long insertFormationJob(long project) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO formation_jobs (project_id, job_status, constraint_version, triggered_by_id, seed, algorithm_version) "
                            + "VALUES (?, 'READY', 1, ?, 12345, '1.0.0')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, project);
            statement.setLong(2, mentorId);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertPreference(long project, long student, long preferredStudent, int rank) {
        jdbcTemplate.update(
                "INSERT INTO teammate_preferences (project_id, student_id, preferred_student_id, priority_rank) VALUES (?, ?, ?, ?)",
                project, student, preferredStudent, rank);
    }

    private long insertSkill(String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO skills (name, category) VALUES (?, 'GENERAL')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setString(1, name);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertStudentSkill(long profileId, long skillId, int proficiency) {
        jdbcTemplate.update(
                "INSERT INTO student_skills (profile_id, skill_id, proficiency_level) VALUES (?, ?, ?)",
                profileId, skillId, proficiency);
    }

    private long insertCandidateTeam(long project, long formationJobId, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO candidate_teams (project_id, formation_job_id, team_name, status) VALUES (?, ?, ?, 'DRAFT')",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, project);
            statement.setLong(2, formationJobId);
            statement.setString(3, name);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertCandidateTeamMember(long teamId, long formationJobId, long student, String source) {
        jdbcTemplate.update(
                "INSERT INTO candidate_team_members (candidate_team_id, formation_job_id, student_id, source) VALUES (?, ?, ?, ?)",
                teamId, formationJobId, student, source);
    }

    private long insertFinalizedTeam(long project, String name) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement(
                    "INSERT INTO finalized_teams (project_id, team_name, finalized_by) VALUES (?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, project);
            statement.setString(2, name);
            statement.setLong(3, mentorId);
            return statement;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    private void insertFinalizedTeamMembership(long teamId, long project, long student, String source) {
        jdbcTemplate.update(
                "INSERT INTO finalized_team_memberships (finalized_team_id, project_id, student_id, source) VALUES (?, ?, ?, ?)",
                teamId, project, student, source);
    }
}
