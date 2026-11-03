package com.teamformation.backend.entity;

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
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class EntityMappingTest {

    @Test
    @DisplayName("Verify User entity annotations and field mappings")
    void testUserEntityMapping() {
        assertThat(User.class).hasAnnotation(Entity.class);
        Table table = User.class.getAnnotation(Table.class);
        assertThat(table).isNotNull();
        assertThat(table.name()).isEqualTo("users");

        User user = new User("srusti@example.com", "Srusti C", "hashed_pwd", UserRole.STUDENT);
        assertThat(user.getEmail()).isEqualTo("srusti@example.com");
        assertThat(user.getFullName()).isEqualTo("Srusti C");
        assertThat(user.getRole()).isEqualTo(UserRole.STUDENT);
        assertThat(user.isActive()).isTrue();
        assertThat(user.getCreatedAt()).isNotNull();
        assertThat(user.getUpdatedAt()).isNotNull();

        User user2 = new User("srusti@example.com", "Srusti C", "hashed_pwd", UserRole.STUDENT);
        assertThat(user).isEqualTo(user2);
        assertThat(user.hashCode()).isEqualTo(user2.hashCode());
    }

    @Test
    @DisplayName("Verify StudentProfile entity and one-to-one relationship with User")
    void testStudentProfileEntityMapping() {
        assertThat(StudentProfile.class).hasAnnotation(Entity.class);
        Table table = StudentProfile.class.getAnnotation(Table.class);
        assertThat(table.name()).isEqualTo("student_profiles");

        User student = new User("avanti@example.com", "Avanti C", "pwd", UserRole.STUDENT);
        StudentProfile profile = new StudentProfile(student, "02fe24bcs052", "CSE", 5, "Algorithms, Systems");

        assertThat(profile.getUser()).isEqualTo(student);
        assertThat(profile.getSrn()).isEqualTo("02FE24BCS052");
        assertThat(profile.getDepartment()).isEqualTo("CSE");
        assertThat(profile.getSemester()).isEqualTo(5);
        assertThat(profile.getInterests()).contains("Algorithms");
    }

    @Test
    @DisplayName("Verify Skill and StudentSkill many-to-many relationship mapping")
    void testSkillAndStudentSkillMapping() {
        Skill skill = new Skill("SQL", "DATABASE", "Structured Query Language");
        assertThat(skill.getName()).isEqualTo("SQL");
        assertThat(skill.getCategory()).isEqualTo("DATABASE");

        User student = new User("darshan@example.com", "Darshan C", "pwd", UserRole.STUDENT);
        StudentProfile profile = new StudentProfile(student, "02FE24BCS141", "CSE", 5, "Distributed Systems");

        StudentSkill studentSkill = new StudentSkill(profile, skill, 5);
        assertThat(studentSkill.getStudentProfile()).isEqualTo(profile);
        assertThat(studentSkill.getSkill()).isEqualTo(skill);
        assertThat(studentSkill.getProficiencyLevel()).isEqualTo(5);
    }

    @Test
    @DisplayName("Verify Project, ProjectRoster, and TeammatePreference relationships")
    void testProjectRosterAndPreferenceMapping() {
        User mentor = new User("mentor@university.edu", "Prof. Veena Badiger", "pwd", UserRole.MENTOR);
        User student1 = new User("darshan@example.com", "Darshan C", "pwd", UserRole.STUDENT);
        User student2 = new User("kartik@example.com", "Kartik P", "pwd", UserRole.STUDENT);

        Project project = new Project(mentor, "Skill Match System", "Automate team formation", "CS301", 2, 4, 1, Instant.now().plusSeconds(86400));
        assertThat(project.getMentor()).isEqualTo(mentor);
        assertThat(project.getTitle()).isEqualTo("Skill Match System");
        assertThat(project.getMinTeamSize()).isEqualTo(2);
        assertThat(project.getMaxTeamSize()).isEqualTo(4);
        assertThat(project.getTeamSizeSlack()).isEqualTo(1);
        assertThat(project.getStatus()).isEqualTo(ProjectStatus.DRAFT);

        ProjectRoster roster = new ProjectRoster(project, student1, RosterEligibilityStatus.ELIGIBLE);
        assertThat(roster.getProject()).isEqualTo(project);
        assertThat(roster.getStudent()).isEqualTo(student1);
        assertThat(roster.getEligibilityStatus()).isEqualTo(RosterEligibilityStatus.ELIGIBLE);

        TeammatePreference preference = new TeammatePreference(project, student1, student2, 1);
        assertThat(preference.getProject()).isEqualTo(project);
        assertThat(preference.getStudent()).isEqualTo(student1);
        assertThat(preference.getPreferredStudent()).isEqualTo(student2);
        assertThat(preference.getRank()).isEqualTo(1);
    }

    @Test
    @DisplayName("Verify ProjectConstraint entity, rule types and versioning")
    void testProjectConstraintMapping() {
        User mentor = new User("mentor@university.edu", "Prof. Veena", "pwd", UserRole.MENTOR);
        Project project = new Project(mentor, "Team System", "Description", "CS301", 2, 4, 1, null);

        ProjectConstraint hardRule = new ProjectConstraint(
                project,
                ConstraintType.HARD,
                ConstraintRuleType.MANDATORY_VERIFIED_SKILL,
                "{\"skillId\":1,\"minCount\":1}",
                1.0,
                1
        );

        assertThat(hardRule.getProject()).isEqualTo(project);
        assertThat(hardRule.getConstraintType()).isEqualTo(ConstraintType.HARD);
        assertThat(hardRule.getRuleType()).isEqualTo(ConstraintRuleType.MANDATORY_VERIFIED_SKILL);
        assertThat(hardRule.getVersion()).isEqualTo(1);
        assertThat(hardRule.isActive()).isTrue();
    }

    @Test
    @DisplayName("Verify FormationJob, CandidateTeam, and CandidateTeamMember entity relationships")
    void testFormationJobAndCandidateTeamMapping() {
        User mentor = new User("mentor@university.edu", "Prof. Veena", "pwd", UserRole.MENTOR);
        User student = new User("srusti@example.com", "Srusti C", "pwd", UserRole.STUDENT);
        Project project = new Project(mentor, "Team System", "Description", "CS301", 2, 4, 1, null);

        FormationJob job = new FormationJob(project, 1, mentor);
        job.setSeed(42L);
        job.setAlgorithmVersion("1.0.0");
        assertThat(job.getProject()).isEqualTo(project);
        assertThat(job.getJobStatus()).isEqualTo(JobStatus.READY);
        assertThat(job.getTriggeredBy()).isEqualTo(mentor);
        assertThat(job.getSeed()).isEqualTo(42L);
        assertThat(job.getAlgorithmVersion()).isEqualTo("1.0.0");

        CandidateTeam team = new CandidateTeam(project, job, "Team Alpha");
        assertThat(team.getProject()).isEqualTo(project);
        assertThat(team.getFormationJob()).isEqualTo(job);
        assertThat(team.getTeamName()).isEqualTo("Team Alpha");

        CandidateTeamMember member = new CandidateTeamMember(team, student, job.getId(), MembershipSource.MANUAL_OVERRIDE);
        assertThat(member.getCandidateTeam()).isEqualTo(team);
        assertThat(member.getStudent()).isEqualTo(student);
        assertThat(member.getSource()).isEqualTo(MembershipSource.MANUAL_OVERRIDE);
    }

    @Test
    @DisplayName("Verify FinalizedTeam and FinalizedTeamMembership entity relationships")
    void testFinalizedTeamMapping() {
        User mentor = new User("mentor2@university.edu", "Prof. Veena", "pwd", UserRole.MENTOR);
        User student = new User("srusti2@example.com", "Srusti C", "pwd", UserRole.STUDENT);
        Project project = new Project(mentor, "Team System", "Description", "CS301", 2, 4, 1, null);

        FinalizedTeam team = new FinalizedTeam(project, "Final Team Alpha", mentor);
        assertThat(team.getProject()).isEqualTo(project);
        assertThat(team.getTeamName()).isEqualTo("Final Team Alpha");
        assertThat(team.getFinalizedBy()).isEqualTo(mentor);

        FinalizedTeamMembership membership =
                new FinalizedTeamMembership(team, project, student, MembershipSource.MANUAL_OVERRIDE);
        assertThat(membership.getFinalizedTeam()).isEqualTo(team);
        assertThat(membership.getProject()).isEqualTo(project);
        assertThat(membership.getStudent()).isEqualTo(student);
        assertThat(membership.getSource()).isEqualTo(MembershipSource.MANUAL_OVERRIDE);
    }

    @Test
    @DisplayName("Verify StudentProfile database and Bean Validation SRN rules")
    void testSrnConstraintAndNormalization() {
        User student = new User("srn-check@example.com", "SRN Check", "pwd", UserRole.STUDENT);
        StudentProfile profile = new StudentProfile(student, " 02fe24bcs141 ", "CSE", 5, null);
        assertThat(profile.getSrn()).isEqualTo("02FE24BCS141");

        jakarta.validation.Validator validator =
                jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();
        assertThat(validator.validate(profile)).noneMatch(v -> v.getPropertyPath().toString().equals("srn"));

        StudentProfile invalid = new StudentProfile(student, "02FE24BCS14", "CSE", 5, null);
        assertThat(validator.validate(invalid))
                .withFailMessage("The Bean Validation pattern must reject an invalid SRN")
                .anyMatch(v -> v.getPropertyPath().toString().equals("srn"));
    }

    @Test
    @DisplayName("Verify TeamValidationReport, ValidationFinding, and MentorApproval auditing")
    void testValidationAndApprovalMapping() {
        User mentor = new User("mentor@university.edu", "Prof. Veena", "pwd", UserRole.MENTOR);
        Project project = new Project(mentor, "Team System", "Description", "CS301", 2, 4, 1, null);
        FormationJob job = new FormationJob(project, 1, mentor);

        TeamValidationReport report = new TeamValidationReport(project, job, ValidationStatus.PASSED, "All hard constraints satisfied");
        assertThat(report.getProject()).isEqualTo(project);
        assertThat(report.getValidationStatus()).isEqualTo(ValidationStatus.PASSED);

        ValidationFinding finding = new ValidationFinding(report, null, FindingSeverity.INFO, "ROSTER_DIVISIBLE", "Roster count 24 is divisible by team size 4");
        assertThat(finding.getReport()).isEqualTo(report);
        assertThat(finding.getSeverity()).isEqualTo(FindingSeverity.INFO);

        MentorApproval approval = new MentorApproval(project, mentor, report, ApprovalStatus.APPROVED, "Teams approved for publication");
        assertThat(approval.getProject()).isEqualTo(project);
        assertThat(approval.getMentor()).isEqualTo(mentor);
        assertThat(approval.getValidationReport()).isEqualTo(report);
        assertThat(approval.getApprovalStatus()).isEqualTo(ApprovalStatus.APPROVED);
        assertThat(approval.getDecisionTimestamp()).isNotNull();
    }

    @Test
    @DisplayName("Formation job statuses READY and RUNNING are the only active statuses")
    void testActiveFormationJobStatuses() {
        assertThat(JobStatus.READY.isActive()).isTrue();
        assertThat(JobStatus.RUNNING.isActive()).isTrue();
        assertThat(JobStatus.COMPLETED.isActive()).isFalse();
        assertThat(JobStatus.FAILED.isActive()).isFalse();
    }

    @Test
    @DisplayName("Verify all 18 entities have explicit primary key @Id annotations")
    void testAllEntitiesHaveIdAnnotation() {
        Class<?>[] entityClasses = {
                AppMetadata.class,
                User.class,
                StudentProfile.class,
                Skill.class,
                StudentSkill.class,
                Project.class,
                ProjectRoster.class,
                TeammatePreference.class,
                ProjectConstraint.class,
                FormationJob.class,
                CandidateTeam.class,
                CandidateTeamMember.class,
                FinalizedTeam.class,
                FinalizedTeamMembership.class,
                TeamValidationReport.class,
                ValidationFinding.class,
                MentorApproval.class,
                EmailVerificationToken.class
        };

        for (Class<?> entityClass : entityClasses) {
            boolean hasId = false;
            for (Field field : entityClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Id.class)) {
                    hasId = true;
                    break;
                }
            }
            assertThat(hasId)
                    .withFailMessage("Entity " + entityClass.getSimpleName() + " must have an @Id field")
                    .isTrue();
        }
    }
}

