package com.teamformation.backend.repository;

import com.teamformation.backend.TestcontainersConfiguration;
import com.teamformation.backend.entity.AppMetadata;
import com.teamformation.backend.entity.CandidateTeam;
import com.teamformation.backend.entity.CandidateTeamMember;
import com.teamformation.backend.entity.EmailVerificationToken;
import com.teamformation.backend.entity.FinalizedTeam;
import com.teamformation.backend.entity.FinalizedTeamMembership;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.MentorApproval;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.ProjectConstraint;
import com.teamformation.backend.entity.ProjectRoster;
import com.teamformation.backend.entity.Skill;
import com.teamformation.backend.entity.StudentProfile;
import com.teamformation.backend.entity.StudentSkill;
import com.teamformation.backend.entity.TeamValidationReport;
import com.teamformation.backend.entity.TeammatePreference;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.ValidationFinding;
import com.teamformation.backend.entity.enums.ApprovalStatus;
import com.teamformation.backend.entity.enums.ConstraintRuleType;
import com.teamformation.backend.entity.enums.ConstraintType;
import com.teamformation.backend.entity.enums.FindingSeverity;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.MembershipSource;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.entity.enums.ValidationStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifies every repository against a real MySQL 8.0 instance started by
 * Testcontainers. No H2 and no skipped tests. Flyway migrations run because
 * the test profile enables them; the DataJpaTest slice does not include
 * Flyway by default in Spring Boot 4.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class RepositoryIntegrationTest {

    @Autowired
    private AppMetadataRepository appMetadataRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailVerificationTokenRepository emailVerificationTokenRepository;

    @Autowired
    private StudentProfileRepository studentProfileRepository;

    @Autowired
    private SkillRepository skillRepository;

    @Autowired
    private StudentSkillRepository studentSkillRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ProjectRosterRepository projectRosterRepository;

    @Autowired
    private TeammatePreferenceRepository teammatePreferenceRepository;

    @Autowired
    private ProjectConstraintRepository projectConstraintRepository;

    @Autowired
    private FormationJobRepository formationJobRepository;

    @Autowired
    private CandidateTeamRepository candidateTeamRepository;

    @Autowired
    private CandidateTeamMemberRepository candidateTeamMemberRepository;

    @Autowired
    private FinalizedTeamRepository finalizedTeamRepository;

    @Autowired
    private FinalizedTeamMembershipRepository finalizedTeamMembershipRepository;

    @Autowired
    private TeamValidationReportRepository teamValidationReportRepository;

    @Autowired
    private ValidationFindingRepository validationFindingRepository;

    @Autowired
    private MentorApprovalRepository mentorApprovalRepository;

    // ------------------------------------------------------------------
    // AppMetadata
    // ------------------------------------------------------------------

    @Test
    @DisplayName("AppMetadata: load seeded row, update it, and return empty for an unknown key")
    void appMetadataRepository_loadsAndUpdatesSeed() {
        AppMetadata seeded = appMetadataRepository.findById("schema_version").orElseThrow();
        assertThat(seeded.getMetaValue()).isNotBlank();

        seeded.setMetaValue("3.0.0-test");
        appMetadataRepository.saveAndFlush(seeded);

        assertThat(appMetadataRepository.findById("schema_version"))
                .get()
                .extracting(AppMetadata::getMetaValue)
                .isEqualTo("3.0.0-test");
        assertThat(appMetadataRepository.findById("does_not_exist")).isEmpty();
    }

    @Test
    @DisplayName("AppMetadata: insert relies on database timestamp defaults")
    void appMetadataRepository_insertUsesDatabaseTimestampDefaults() {
        appMetadataRepository.saveAndFlush(new AppMetadata("task_2_4_test", "created"));

        AppMetadata loaded = appMetadataRepository.findById("task_2_4_test").orElseThrow();
        assertThat(loaded.getCreatedAt()).isNotNull();
        assertThat(loaded.getUpdatedAt()).isNotNull();
    }

    // ------------------------------------------------------------------
    // User
    // ------------------------------------------------------------------

    @Test
    @DisplayName("User: find by email ignoring case and return empty when absent")
    void userRepository_findByEmailIgnoreCase() {
        saveUser("Mentor@Example.COM", UserRole.MENTOR);

        assertThat(userRepository.findByEmailIgnoreCase("mentor@example.com"))
                .get()
                .extracting(User::getEmail)
                .isEqualTo("Mentor@Example.COM");
        assertThat(userRepository.findByEmailIgnoreCase("missing@example.com")).isEmpty();
    }

    @Test
    @DisplayName("User: existence by email ignoring case")
    void userRepository_existsByEmailIgnoreCase() {
        saveUser("student@example.com", UserRole.STUDENT);

        assertThat(userRepository.existsByEmailIgnoreCase("STUDENT@EXAMPLE.COM")).isTrue();
        assertThat(userRepository.existsByEmailIgnoreCase("missing@example.com")).isFalse();
    }

    @Test
    @DisplayName("User: existence by role supports the admin seed check")
    void userRepository_existsByRole() {
        saveUser("mentor@example.com", UserRole.MENTOR);
        User admin = saveUser("admin@example.com", UserRole.ADMIN);

        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isTrue();
        userRepository.delete(admin);
        userRepository.flush();
        assertThat(userRepository.existsByRole(UserRole.ADMIN)).isFalse();
    }

    // ------------------------------------------------------------------
    // EmailVerificationToken
    // ------------------------------------------------------------------

    @Test
    @DisplayName("EmailVerificationToken: find by token hash and return empty when absent")
    void emailVerificationTokenRepository_findByTokenHash() {
        User user = saveUser("verify@example.com", UserRole.STUDENT);
        String hash = "d".repeat(64);
        emailVerificationTokenRepository.saveAndFlush(
                new EmailVerificationToken(user, hash, java.time.Instant.now().plusSeconds(3600)));

        assertThat(emailVerificationTokenRepository.findByTokenHash(hash))
                .get()
                .extracting(EmailVerificationToken::getTokenHash)
                .isEqualTo(hash);
        assertThat(emailVerificationTokenRepository.findByTokenHash("e".repeat(64))).isEmpty();
    }

    @Test
    @DisplayName("User: duplicate email differing only in case is rejected by the database")
    void userRepository_rejectsDuplicateEmail() {
        saveUser("duplicate@example.com", UserRole.STUDENT);

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new User("DUPLICATE@EXAMPLE.COM", "Other", "hash", UserRole.STUDENT)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // StudentProfile
    // ------------------------------------------------------------------

    @Test
    @DisplayName("StudentProfile: find by user and by SRN, with empty results")
    void studentProfileRepository_findByUserAndSrn() {
        User owner = saveUser("owner@example.com", UserRole.STUDENT);
        StudentProfile profile = saveProfile(owner, "02FE24BCS141");

        assertThat(studentProfileRepository.findByUserId(owner.getId()))
                .get()
                .extracting(StudentProfile::getId)
                .isEqualTo(profile.getId());
        assertThat(studentProfileRepository.findByUserId(999L)).isEmpty();

        assertThat(studentProfileRepository.findBySrn("02FE24BCS141"))
                .get()
                .extracting(StudentProfile::getUser)
                .extracting(User::getEmail)
                .isEqualTo("owner@example.com");
        assertThat(studentProfileRepository.findBySrn("02FE24BCS999")).isEmpty();
    }

    @Test
    @DisplayName("StudentProfile: existence by SRN")
    void studentProfileRepository_existsBySrn() {
        saveProfile(saveUser("s1@example.com", UserRole.STUDENT), "02FE24BCS142");

        assertThat(studentProfileRepository.existsBySrn("02FE24BCS142")).isTrue();
        assertThat(studentProfileRepository.existsBySrn("02FE24BCS999")).isFalse();
    }

    @Test
    @DisplayName("StudentProfile: duplicate SRN is rejected by the database")
    void studentProfileRepository_rejectsDuplicateSrn() {
        saveProfile(saveUser("first@example.com", UserRole.STUDENT), "02FE24BCS143");
        User second = saveUser("second@example.com", UserRole.STUDENT);

        assertThatThrownBy(() -> studentProfileRepository.saveAndFlush(
                new StudentProfile(second, "02FE24BCS143", "CSE", 5, null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // Skill and StudentSkill
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Skill: find by name ignoring case and return empty when absent")
    void skillRepository_findByNameIgnoreCase() {
        saveSkill("Java");

        assertThat(skillRepository.findByNameIgnoreCase("java"))
                .get()
                .extracting(Skill::getName)
                .isEqualTo("Java");
        assertThat(skillRepository.findByNameIgnoreCase("Rust")).isEmpty();
    }

    @Test
    @DisplayName("Skill: duplicate name differing only in case is rejected by the database")
    void skillRepository_rejectsDuplicateNameInDifferentCase() {
        saveSkill("Python");

        assertThatThrownBy(() -> skillRepository.saveAndFlush(new Skill("python", "LANG", null)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("StudentSkill: find by student profile and return empty for another profile")
    void studentSkillRepository_findByStudentProfileId() {
        StudentProfile profile = saveProfile(saveUser("skilled@example.com", UserRole.STUDENT), "02FE24BCS144");
        studentSkillRepository.save(new StudentSkill(profile, saveSkill("Java"), 5));
        studentSkillRepository.save(new StudentSkill(profile, saveSkill("SQL"), 3));

        List<StudentSkill> skills = studentSkillRepository.findByStudentProfileId(profile.getId());
        assertThat(skills).hasSize(2);
        assertThat(skills).extracting(ss -> ss.getSkill().getName())
                .containsExactlyInAnyOrder("Java", "SQL");
        assertThat(studentSkillRepository.findByStudentProfileId(999L)).isEmpty();
    }

    // ------------------------------------------------------------------
    // Project
    // ------------------------------------------------------------------

    @Test
    @DisplayName("Project: page projects by mentor, including an empty result")
    void projectRepository_findByMentorId() {
        User mentor = saveUser("mentor1@example.com", UserRole.MENTOR);
        User otherMentor = saveUser("mentor2@example.com", UserRole.MENTOR);
        saveProject(mentor);
        saveProject(mentor);
        saveProject(mentor);
        saveProject(otherMentor);

        Page<Project> firstPage = projectRepository.findByMentorId(mentor.getId(), PageRequest.of(0, 2));
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);

        Page<Project> secondPage = projectRepository.findByMentorId(mentor.getId(), PageRequest.of(1, 2));
        assertThat(secondPage.getContent()).hasSize(1);

        assertThat(projectRepository.findByMentorId(999L, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("Project: page projects by mentor and status, including an empty result")
    void projectRepository_findByMentorIdAndStatus() {
        User mentor = saveUser("mentor3@example.com", UserRole.MENTOR);
        saveProject(mentor, ProjectStatus.DRAFT);
        saveProject(mentor, ProjectStatus.DRAFT);
        saveProject(mentor, ProjectStatus.PUBLISHED);

        Page<Project> drafts = projectRepository.findByMentorIdAndStatus(
                mentor.getId(), ProjectStatus.DRAFT, PageRequest.of(0, 1));
        assertThat(drafts.getTotalElements()).isEqualTo(2);
        assertThat(drafts.getContent()).hasSize(1);

        assertThat(projectRepository.findByMentorIdAndStatus(
                mentor.getId(), ProjectStatus.COMPLETED, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("Project: mentor-scoped lookup returns only owned projects")
    void projectRepository_findByIdAndMentorId() {
        User mentor = saveUser("mentor4@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);

        assertThat(projectRepository.findByIdAndMentorId(project.getId(), mentor.getId())).isPresent();
        assertThat(projectRepository.findByIdAndMentorId(project.getId(), 999L)).isEmpty();
        assertThat(projectRepository.findByIdAndMentorId(999L, mentor.getId())).isEmpty();
    }

    @Test
    @DisplayName("Project: pessimistic write lock lookup returns the project and empty when absent")
    void projectRepository_findByIdForUpdate() {
        User mentor = saveUser("mentor5@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);

        assertThat(projectRepository.findByIdForUpdate(project.getId())).isPresent();
        assertThat(projectRepository.findByIdForUpdate(999L)).isEmpty();
    }

    // ------------------------------------------------------------------
    // ProjectRoster
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ProjectRoster: page roster entries by project, including an empty result")
    void projectRosterRepository_findByProjectId() {
        User mentor = saveUser("mentor6@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        addRoster(project, saveUser("r1@example.com", UserRole.STUDENT));
        addRoster(project, saveUser("r2@example.com", UserRole.STUDENT));
        addRoster(project, saveUser("r3@example.com", UserRole.STUDENT));

        Page<ProjectRoster> page = projectRosterRepository.findByProjectId(project.getId(), PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);
        assertThat(page.getContent().get(0).getStudent().getEmail()).isNotBlank();

        assertThat(projectRosterRepository.findByProjectId(999L, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("ProjectRoster: existence by project and student")
    void projectRosterRepository_existsByProjectIdAndStudentId() {
        User mentor = saveUser("mentor7@example.com", UserRole.MENTOR);
        User student = saveUser("r4@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        addRoster(project, student);

        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(project.getId(), student.getId())).isTrue();
        assertThat(projectRosterRepository.existsByProjectIdAndStudentId(project.getId(), 999L)).isFalse();
    }

    @Test
    @DisplayName("ProjectRoster: duplicate roster entry is rejected by the database")
    void projectRosterRepository_rejectsDuplicateRosterEntry() {
        User mentor = saveUser("mentor8@example.com", UserRole.MENTOR);
        User student = saveUser("r5@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        addRoster(project, student);

        assertThatThrownBy(() -> projectRosterRepository.saveAndFlush(
                new ProjectRoster(project, student, RosterEligibilityStatus.ELIGIBLE)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // TeammatePreference
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TeammatePreference: find by project/student ordered by rank, with empty result")
    void teammatePreferenceRepository_findByProjectIdAndStudentId() {
        User mentor = saveUser("mentor9@example.com", UserRole.MENTOR);
        User student = saveUser("p1@example.com", UserRole.STUDENT);
        User first = saveUser("p2@example.com", UserRole.STUDENT);
        User second = saveUser("p3@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        addRoster(project, student);
        addRoster(project, first);
        addRoster(project, second);
        teammatePreferenceRepository.save(new TeammatePreference(project, student, second, 2));
        teammatePreferenceRepository.save(new TeammatePreference(project, student, first, 1));

        List<TeammatePreference> preferences =
                teammatePreferenceRepository.findByProjectIdAndStudentIdOrderByRankAsc(project.getId(), student.getId());
        assertThat(preferences).extracting(TeammatePreference::getRank).containsExactly(1, 2);
        assertThat(preferences.get(0).getPreferredStudent().getEmail()).isEqualTo("p2@example.com");

        assertThat(teammatePreferenceRepository.findByProjectIdAndStudentIdOrderByRankAsc(
                project.getId(), first.getId())).isEmpty();
    }

    @Test
    @DisplayName("TeammatePreference: page preferences by project, including an empty result")
    void teammatePreferenceRepository_findByProjectId() {
        User mentor = saveUser("mentor10@example.com", UserRole.MENTOR);
        User student = saveUser("p4@example.com", UserRole.STUDENT);
        User first = saveUser("p5@example.com", UserRole.STUDENT);
        User second = saveUser("p6@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        addRoster(project, student);
        addRoster(project, first);
        addRoster(project, second);
        teammatePreferenceRepository.save(new TeammatePreference(project, student, first, 1));
        teammatePreferenceRepository.save(new TeammatePreference(project, student, second, 2));
        teammatePreferenceRepository.save(new TeammatePreference(project, first, second, 1));

        Page<TeammatePreference> page = teammatePreferenceRepository.findByProjectIdOrderByStudentIdAscRankAsc(
                project.getId(), PageRequest.of(0, 2));
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(2);

        assertThat(teammatePreferenceRepository.findByProjectIdOrderByStudentIdAscRankAsc(
                999L, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("TeammatePreference: duplicate rank for one student and project is rejected")
    void teammatePreferenceRepository_rejectsDuplicateRank() {
        User mentor = saveUser("mentor11@example.com", UserRole.MENTOR);
        User student = saveUser("p7@example.com", UserRole.STUDENT);
        User first = saveUser("p8@example.com", UserRole.STUDENT);
        User second = saveUser("p9@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        addRoster(project, student);
        addRoster(project, first);
        addRoster(project, second);
        teammatePreferenceRepository.save(new TeammatePreference(project, student, first, 1));

        assertThatThrownBy(() -> teammatePreferenceRepository.saveAndFlush(
                new TeammatePreference(project, student, second, 1)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // ProjectConstraint
    // ------------------------------------------------------------------

    @Test
    @DisplayName("ProjectConstraint: only active constraints of a project are returned")
    void projectConstraintRepository_findByProjectIdAndActiveTrue() {
        User mentor = saveUser("mentor12@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        projectConstraintRepository.save(new ProjectConstraint(
                project, ConstraintType.HARD, ConstraintRuleType.TEAM_SIZE, "{}", 1.0, 1));
        projectConstraintRepository.save(new ProjectConstraint(
                project, ConstraintType.SOFT, ConstraintRuleType.DIVERSE_SKILLS, "{}", 0.5, 1));
        ProjectConstraint inactive = projectConstraintRepository.save(new ProjectConstraint(
                project, ConstraintType.HARD, ConstraintRuleType.MEMBER_EXCLUSION, "{}", 1.0, 1));
        inactive.setActive(false);
        projectConstraintRepository.saveAndFlush(inactive);

        List<ProjectConstraint> active = projectConstraintRepository.findByProjectIdAndActiveTrue(project.getId());
        assertThat(active).hasSize(2);
        assertThat(active).allMatch(ProjectConstraint::isActive);
        assertThat(projectConstraintRepository.findByProjectIdAndActiveTrue(999L)).isEmpty();
    }

    // ------------------------------------------------------------------
    // FormationJob
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FormationJob: page jobs by project and status, including an empty result")
    void formationJobRepository_findByProjectIdAndJobStatus() {
        User mentor = saveUser("mentor13@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        saveJob(project, mentor, JobStatus.READY);
        saveJob(project, mentor, JobStatus.READY);
        saveJob(project, mentor, JobStatus.READY);
        saveJob(project, mentor, JobStatus.RUNNING);

        Page<FormationJob> ready = formationJobRepository.findByProjectIdAndJobStatus(
                project.getId(), JobStatus.READY, PageRequest.of(0, 2));
        assertThat(ready.getTotalElements()).isEqualTo(3);
        assertThat(ready.getContent()).hasSize(2);
        assertThat(ready.getContent().get(0).getTriggeredBy().getEmail()).isEqualTo("mentor13@example.com");

        assertThat(formationJobRepository.findByProjectIdAndJobStatus(
                project.getId(), JobStatus.FAILED, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("FormationJob: existence of a job in active statuses")
    void formationJobRepository_existsByProjectIdAndJobStatusIn() {
        User mentor = saveUser("mentor14@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        saveJob(project, mentor, JobStatus.RUNNING);

        assertThat(formationJobRepository.existsByProjectIdAndJobStatusIn(
                project.getId(), List.of(JobStatus.READY, JobStatus.RUNNING))).isTrue();
        assertThat(formationJobRepository.existsByProjectIdAndJobStatusIn(
                project.getId(), List.of(JobStatus.COMPLETED, JobStatus.FAILED))).isFalse();
        assertThat(formationJobRepository.existsByProjectIdAndJobStatusIn(
                999L, List.of(JobStatus.READY))).isFalse();
    }

    @Test
    @DisplayName("FormationJob: latest job by creation instant then id, with empty result")
    void formationJobRepository_findFirstByProjectIdOrderByCreatedAtDescIdDesc() {
        User mentor = saveUser("mentor15@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        saveJob(project, mentor, JobStatus.COMPLETED);
        saveJob(project, mentor, JobStatus.COMPLETED);
        FormationJob latest = saveJob(project, mentor, JobStatus.READY);

        assertThat(formationJobRepository.findFirstByProjectIdOrderByCreatedAtDescIdDesc(project.getId()))
                .get()
                .extracting(FormationJob::getId)
                .isEqualTo(latest.getId());
        assertThat(formationJobRepository.findFirstByProjectIdOrderByCreatedAtDescIdDesc(999L)).isEmpty();
    }

    // ------------------------------------------------------------------
    // CandidateTeam and CandidateTeamMember
    // ------------------------------------------------------------------

    @Test
    @DisplayName("CandidateTeam: page teams by formation job ordered by name, with empty result")
    void candidateTeamRepository_findByFormationJobId() {
        User mentor = saveUser("mentor16@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        FormationJob job = saveJob(project, mentor, JobStatus.COMPLETED);
        saveCandidateTeam(project, job, "Team B");
        saveCandidateTeam(project, job, "Team A");
        saveCandidateTeam(project, job, "Team C");

        Page<CandidateTeam> firstPage = candidateTeamRepository.findByFormationJobIdOrderByTeamNameAsc(
                job.getId(), PageRequest.of(0, 2));
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent().get(0).getTeamName()).isEqualTo("Team A");

        assertThat(candidateTeamRepository.findByFormationJobIdOrderByTeamNameAsc(
                999L, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("CandidateTeamMember: find members by team ordered by student, with empty result")
    void candidateTeamMemberRepository_findByCandidateTeamId() {
        User mentor = saveUser("mentor17@example.com", UserRole.MENTOR);
        User s1 = saveUser("c1@example.com", UserRole.STUDENT);
        User s2 = saveUser("c2@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        FormationJob job = saveJob(project, mentor, JobStatus.COMPLETED);
        CandidateTeam teamA = saveCandidateTeam(project, job, "Team A");
        CandidateTeam teamB = saveCandidateTeam(project, job, "Team B");
        candidateTeamMemberRepository.save(new CandidateTeamMember(teamA, s1, job.getId(), MembershipSource.ALGORITHM));
        candidateTeamMemberRepository.save(new CandidateTeamMember(teamA, s2, job.getId(), MembershipSource.ALGORITHM));

        List<CandidateTeamMember> members =
                candidateTeamMemberRepository.findByCandidateTeamIdOrderByStudentIdAsc(teamA.getId());
        assertThat(members).hasSize(2);
        assertThat(members.get(0).getStudent().getEmail()).isEqualTo("c1@example.com");

        assertThat(candidateTeamMemberRepository.findByCandidateTeamIdOrderByStudentIdAsc(teamB.getId())).isEmpty();
    }

    @Test
    @DisplayName("CandidateTeamMember: same student in two candidate teams of one run is rejected")
    void candidateTeamMemberRepository_rejectsSameStudentInTwoTeamsOfOneRun() {
        User mentor = saveUser("mentor18@example.com", UserRole.MENTOR);
        User student = saveUser("c3@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        FormationJob job = saveJob(project, mentor, JobStatus.COMPLETED);
        CandidateTeam teamA = saveCandidateTeam(project, job, "Team A");
        CandidateTeam teamB = saveCandidateTeam(project, job, "Team B");
        candidateTeamMemberRepository.save(new CandidateTeamMember(
                teamA, student, job.getId(), MembershipSource.ALGORITHM));

        assertThatThrownBy(() -> candidateTeamMemberRepository.saveAndFlush(
                new CandidateTeamMember(teamB, student, job.getId(), MembershipSource.ALGORITHM)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // FinalizedTeam and FinalizedTeamMembership
    // ------------------------------------------------------------------

    @Test
    @DisplayName("FinalizedTeam: page teams by project ordered by name, with empty result")
    void finalizedTeamRepository_findByProjectId() {
        User mentor = saveUser("mentor19@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        saveFinalizedTeam(project, mentor, "Final B");
        saveFinalizedTeam(project, mentor, "Final A");
        saveFinalizedTeam(project, mentor, "Final C");

        Page<FinalizedTeam> firstPage = finalizedTeamRepository.findByProjectIdOrderByTeamNameAsc(
                project.getId(), PageRequest.of(0, 2));
        assertThat(firstPage.getTotalElements()).isEqualTo(3);
        assertThat(firstPage.getContent()).hasSize(2);
        assertThat(firstPage.getContent().get(0).getTeamName()).isEqualTo("Final A");
        assertThat(firstPage.getContent().get(0).getFinalizedBy().getEmail()).isEqualTo("mentor19@example.com");

        assertThat(finalizedTeamRepository.findByProjectIdOrderByTeamNameAsc(
                999L, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("FinalizedTeamMembership: page memberships by project and find by project/student")
    void finalizedTeamMembershipRepository_findByProjectIdAndStudentId() {
        User mentor = saveUser("mentor20@example.com", UserRole.MENTOR);
        User s1 = saveUser("f1@example.com", UserRole.STUDENT);
        User s2 = saveUser("f2@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        addRoster(project, s1);
        addRoster(project, s2);
        FinalizedTeam team = saveFinalizedTeam(project, mentor, "Final A");
        finalizedTeamMembershipRepository.save(new FinalizedTeamMembership(
                team, project, s1, MembershipSource.ALGORITHM));
        finalizedTeamMembershipRepository.save(new FinalizedTeamMembership(
                team, project, s2, MembershipSource.ALGORITHM));

        Page<FinalizedTeamMembership> page = finalizedTeamMembershipRepository.findByProjectIdOrderByStudentIdAsc(
                project.getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getStudent().getEmail()).isEqualTo("f1@example.com");

        assertThat(finalizedTeamMembershipRepository.findByProjectIdAndStudentId(project.getId(), s1.getId()))
                .get()
                .extracting(m -> m.getFinalizedTeam().getTeamName())
                .isEqualTo("Final A");
        assertThat(finalizedTeamMembershipRepository.findByProjectIdAndStudentId(project.getId(), 999L)).isEmpty();
        assertThat(finalizedTeamMembershipRepository.findByProjectIdOrderByStudentIdAsc(
                999L, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("FinalizedTeamMembership: same student in two finalized teams of one project is rejected")
    void finalizedTeamMembershipRepository_rejectsSameStudentInTwoFinalizedTeamsOfOneProject() {
        User mentor = saveUser("mentor21@example.com", UserRole.MENTOR);
        User student = saveUser("f3@example.com", UserRole.STUDENT);
        Project project = saveProject(mentor);
        addRoster(project, student);
        FinalizedTeam teamA = saveFinalizedTeam(project, mentor, "Final A");
        FinalizedTeam teamB = saveFinalizedTeam(project, mentor, "Final B");
        finalizedTeamMembershipRepository.save(new FinalizedTeamMembership(
                teamA, project, student, MembershipSource.ALGORITHM));

        assertThatThrownBy(() -> finalizedTeamMembershipRepository.saveAndFlush(
                new FinalizedTeamMembership(teamB, project, student, MembershipSource.ALGORITHM)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    // ------------------------------------------------------------------
    // TeamValidationReport, ValidationFinding, MentorApproval
    // ------------------------------------------------------------------

    @Test
    @DisplayName("TeamValidationReport: page reports by formation job, with empty result")
    void teamValidationReportRepository_findByFormationJobId() {
        User mentor = saveUser("mentor22@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        FormationJob job = saveJob(project, mentor, JobStatus.COMPLETED);
        saveReport(project, job, ValidationStatus.PASSED);
        TeamValidationReport latest = saveReport(project, job, ValidationStatus.FAILED);

        Page<TeamValidationReport> page =
                teamValidationReportRepository.findByFormationJobIdOrderByEvaluatedAtDescIdDesc(
                        job.getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(latest.getId());

        assertThat(teamValidationReportRepository.findByFormationJobIdOrderByEvaluatedAtDescIdDesc(
                999L, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("ValidationFinding: page findings by report and severity, with empty result")
    void validationFindingRepository_findByReportIdAndSeverity() {
        User mentor = saveUser("mentor23@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        FormationJob job = saveJob(project, mentor, JobStatus.COMPLETED);
        CandidateTeam team = saveCandidateTeam(project, job, "Team A");
        TeamValidationReport report = saveReport(project, job, ValidationStatus.FAILED);
        validationFindingRepository.save(new ValidationFinding(
                report, team, FindingSeverity.WARNING, "WARN_1", "first"));
        validationFindingRepository.save(new ValidationFinding(
                report, team, FindingSeverity.WARNING, "WARN_2", "second"));
        validationFindingRepository.save(new ValidationFinding(
                report, team, FindingSeverity.CRITICAL, "CRIT_1", "critical"));

        Page<ValidationFinding> warnings =
                validationFindingRepository.findByReportIdAndSeverityOrderByCreatedAtAscIdAsc(
                        report.getId(), FindingSeverity.WARNING, PageRequest.of(0, 1));
        assertThat(warnings.getTotalElements()).isEqualTo(2);
        assertThat(warnings.getContent()).hasSize(1);
        assertThat(warnings.getContent().get(0).getCandidateTeam().getTeamName()).isEqualTo("Team A");

        assertThat(validationFindingRepository.findByReportIdAndSeverityOrderByCreatedAtAscIdAsc(
                report.getId(), FindingSeverity.INFO, PageRequest.of(0, 10))).isEmpty();
        assertThat(validationFindingRepository.findByReportIdAndSeverityOrderByCreatedAtAscIdAsc(
                999L, FindingSeverity.WARNING, PageRequest.of(0, 10))).isEmpty();
    }

    @Test
    @DisplayName("MentorApproval: page approvals by project, with empty result")
    void mentorApprovalRepository_findByProjectId() {
        User mentor = saveUser("mentor24@example.com", UserRole.MENTOR);
        Project project = saveProject(mentor);
        FormationJob job = saveJob(project, mentor, JobStatus.COMPLETED);
        TeamValidationReport report = saveReport(project, job, ValidationStatus.PASSED);
        mentorApprovalRepository.save(new MentorApproval(
                project, mentor, report, ApprovalStatus.APPROVED, "first"));
        MentorApproval latest = mentorApprovalRepository.save(new MentorApproval(
                project, mentor, report, ApprovalStatus.REJECTED, "second"));

        Page<MentorApproval> page = mentorApprovalRepository.findByProjectIdOrderByDecisionTimestampDescIdDesc(
                project.getId(), PageRequest.of(0, 1));
        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(latest.getId());
        assertThat(page.getContent().get(0).getValidationReport().getId()).isEqualTo(report.getId());

        assertThat(mentorApprovalRepository.findByProjectIdOrderByDecisionTimestampDescIdDesc(
                999L, PageRequest.of(0, 10))).isEmpty();
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private User saveUser(String email, UserRole role) {
        return userRepository.save(new User(email, "Test User", "hash", role));
    }

    private StudentProfile saveProfile(User user, String srn) {
        return studentProfileRepository.save(new StudentProfile(user, srn, "CSE", 5, null));
    }

    private Skill saveSkill(String name) {
        return skillRepository.save(new Skill(name, "GENERAL", null));
    }

    private Project saveProject(User mentor) {
        return saveProject(mentor, ProjectStatus.DRAFT);
    }

    private Project saveProject(User mentor, ProjectStatus status) {
        Project project = new Project(mentor, "Project", "Problem statement", "CS301", 2, 4, 1, null);
        project.setStatus(status);
        return projectRepository.save(project);
    }

    private void addRoster(Project project, User student) {
        projectRosterRepository.save(new ProjectRoster(project, student, RosterEligibilityStatus.ELIGIBLE));
    }

    private FormationJob saveJob(Project project, User mentor, JobStatus status) {
        FormationJob job = new FormationJob(project, 1, mentor);
        job.setJobStatus(status);
        return formationJobRepository.save(job);
    }

    private CandidateTeam saveCandidateTeam(Project project, FormationJob job, String name) {
        return candidateTeamRepository.save(new CandidateTeam(project, job, name));
    }

    private FinalizedTeam saveFinalizedTeam(Project project, User mentor, String name) {
        return finalizedTeamRepository.save(new FinalizedTeam(project, name, mentor));
    }

    private TeamValidationReport saveReport(Project project, FormationJob job, ValidationStatus status) {
        return teamValidationReportRepository.save(new TeamValidationReport(project, job, status, "summary"));
    }
}
