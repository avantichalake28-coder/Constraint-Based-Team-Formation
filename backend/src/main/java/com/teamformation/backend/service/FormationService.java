package com.teamformation.backend.service;

import com.teamformation.backend.config.FormationJobProperties;
import com.teamformation.backend.config.FormationSearchProperties;
import com.teamformation.backend.dto.CandidateTeamMemberResponse;
import com.teamformation.backend.dto.CandidateTeamResponse;
import com.teamformation.backend.dto.FormationJobResponse;
import com.teamformation.backend.dto.FormationJobSummaryResponse;
import com.teamformation.backend.dto.PageResponse;
import com.teamformation.backend.engine.constraint.ConstraintConfiguration;
import com.teamformation.backend.engine.constraint.FeasibilityChecker;
import com.teamformation.backend.engine.constraint.FeasibilityReport;
import com.teamformation.backend.engine.constraint.FeasibilityStatus;
import com.teamformation.backend.engine.constraint.RosterStudentSkills;
import com.teamformation.backend.engine.formation.CompatibilityScorer;
import com.teamformation.backend.engine.formation.FormationEngine;
import com.teamformation.backend.engine.formation.FormationExecutionBudgetExceededException;
import com.teamformation.backend.engine.formation.FormationInput;
import com.teamformation.backend.engine.formation.FormationPlan;
import com.teamformation.backend.engine.formation.FormationPlanValidator;
import com.teamformation.backend.engine.formation.FormationSearchLimitReachedException;
import com.teamformation.backend.engine.formation.FormationSearchLimits;
import com.teamformation.backend.engine.formation.FormationStudent;
import com.teamformation.backend.engine.formation.FormationValidationException;
import com.teamformation.backend.engine.formation.NoValidFormationException;
import com.teamformation.backend.engine.formation.persistence.FormationInputLoader;
import com.teamformation.backend.engine.formation.persistence.FormationInputSnapshotStore;
import com.teamformation.backend.engine.formation.persistence.FormationPlanPersister;
import com.teamformation.backend.entity.CandidateTeam;
import com.teamformation.backend.entity.CandidateTeamMember;
import com.teamformation.backend.entity.FormationJob;
import com.teamformation.backend.entity.Project;
import com.teamformation.backend.entity.User;
import com.teamformation.backend.entity.enums.FailureCode;
import com.teamformation.backend.entity.enums.FailurePhase;
import com.teamformation.backend.entity.enums.JobStatus;
import com.teamformation.backend.entity.enums.ProjectStatus;
import com.teamformation.backend.entity.enums.UserRole;
import com.teamformation.backend.exception.FormationException;
import com.teamformation.backend.exception.ResourceNotFoundException;
import com.teamformation.backend.repository.CandidateTeamMemberRepository;
import com.teamformation.backend.repository.CandidateTeamRepository;
import com.teamformation.backend.repository.FormationJobRepository;
import com.teamformation.backend.repository.ProjectRepository;
import com.teamformation.backend.security.authentication.CurrentUserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Engine 3 formation job lifecycle.
 *
 * <p>The orchestration method is deliberately not transactional. It uses three
 * explicit transactions:</p>
 * <ol>
 *   <li><b>Pre-check</b> is a short read-only transaction that proves ownership,
 *       lifecycle readiness and the absence of an active job without waiting for
 *       the project row lock. This prevents a second synchronous request from
 *       blocking behind a long RUNNING transaction.</li>
 *   <li><b>T1</b> locks the project row, re-validates every pre-check against
 *       current committed state, loads the run input, proves feasibility, and
 *       commits one READY job plus its immutable input snapshot.</li>
 *   <li><b>T2</b> locks the project row and then the job row (always in that
 *       order), moves READY to RUNNING, reconstructs the input from the snapshot
 *       only, runs the pure engine, validates and persists the plan, verifies
 *       the persisted counts, records completion metrics, and moves RUNNING to
 *       COMPLETED.</li>
 *   <li><b>T3</b> is a new transaction that moves READY or RUNNING to FAILED
 *       with a stable code, a phase and a safe reason when T2 rolls back.</li>
 * </ol>
 *
 * <p>Live roster, preference and skill rows are read only in T1. T2 and every
 * historical read use the snapshot or persisted metrics, so a later edit can
 * never change what an old run meant.</p>
 */
@Service
public class FormationService {

    public static final String ALGORITHM_VERSION = "3.0.0";

    private static final Logger log = LoggerFactory.getLogger(FormationService.class);
    private static final List<JobStatus> ACTIVE_JOB_STATUSES =
            List.of(JobStatus.READY, JobStatus.RUNNING);
    private static final int MAX_REASON_LENGTH = 2000;

    private final ProjectRepository projectRepository;
    private final FormationJobRepository formationJobRepository;
    private final CandidateTeamRepository candidateTeamRepository;
    private final CandidateTeamMemberRepository candidateTeamMemberRepository;
    private final ProjectService projectService;
    private final com.teamformation.backend.engine.constraint.ConstraintConfigurationService
            constraintConfigurationService;
    private final FormationInputLoader formationInputLoader;
    private final FormationInputSnapshotStore snapshotStore;
    private final FormationPlanPersister formationPlanPersister;
    private final FormationPlanValidator formationPlanValidator;
    private final FormationEngine formationEngine;
    private final CurrentUserService currentUserService;
    private final FormationExecutionThrottle executionThrottle;
    private final FormationJobProperties jobProperties;
    private final TransactionTemplate transactionTemplate;
    private final TransactionTemplate readOnlyTransactionTemplate;
    private final FormationSearchLimits searchLimits;
    private final SecureRandom secureRandom = new SecureRandom();

    public FormationService(
            ProjectRepository projectRepository,
            FormationJobRepository formationJobRepository,
            CandidateTeamRepository candidateTeamRepository,
            CandidateTeamMemberRepository candidateTeamMemberRepository,
            ProjectService projectService,
            com.teamformation.backend.engine.constraint.ConstraintConfigurationService
                    constraintConfigurationService,
            FormationInputLoader formationInputLoader,
            FormationInputSnapshotStore snapshotStore,
            FormationPlanPersister formationPlanPersister,
            FormationPlanValidator formationPlanValidator,
            FormationEngine formationEngine,
            CurrentUserService currentUserService,
            FormationExecutionThrottle executionThrottle,
            FormationSearchProperties formationSearchProperties,
            FormationJobProperties jobProperties,
            PlatformTransactionManager transactionManager) {
        this.projectRepository = projectRepository;
        this.formationJobRepository = formationJobRepository;
        this.candidateTeamRepository = candidateTeamRepository;
        this.candidateTeamMemberRepository = candidateTeamMemberRepository;
        this.projectService = projectService;
        this.constraintConfigurationService = constraintConfigurationService;
        this.formationInputLoader = formationInputLoader;
        this.snapshotStore = snapshotStore;
        this.formationPlanPersister = formationPlanPersister;
        this.formationPlanValidator = formationPlanValidator;
        this.formationEngine = formationEngine;
        this.currentUserService = currentUserService;
        this.executionThrottle = executionThrottle;
        this.jobProperties = jobProperties;
        this.searchLimits = new FormationSearchLimits(
                formationSearchProperties.maxNodes(),
                formationSearchProperties.maxImprovementPasses());
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED);
        this.readOnlyTransactionTemplate = new TransactionTemplate(transactionManager);
        this.readOnlyTransactionTemplate.setReadOnly(true);
        this.readOnlyTransactionTemplate.setIsolationLevel(
                TransactionDefinition.ISOLATION_READ_COMMITTED);
    }

    /**
     * Starts and synchronously completes one formation run.
     */
    public FormationJobResponse startFormation(Authentication authentication, Long projectId) {
        User mentor = currentUserService.requireCurrentUserWithRole(
                authentication, UserRole.MENTOR);

        try (FormationExecutionThrottle.Lease lease =
                     executionThrottle.tryAcquire(mentor.getId())) {
            if (!lease.acquired()) {
                throw FormationException.throttled();
            }

            preflight(projectId, mentor);

            long seed = secureRandom.nextLong() & Long.MAX_VALUE;
            FormationJob job = transactionTemplate.execute(
                    status -> createReadyJob(projectId, mentor, seed));
            Long jobId = Objects.requireNonNull(job, "formation job must be created").getId();

            try {
                transactionTemplate.executeWithoutResult(
                        status -> executeJob(projectId, jobId));
            } catch (RuntimeException failure) {
                markFailed(jobId, failure);
                throw toApiFailure(failure);
            }

            return getJob(authentication, projectId, jobId);
        }
    }

    // ----------------------------------------------------------------------
    // Pre-check: fail fast without waiting for the project lock
    // ----------------------------------------------------------------------

    private void preflight(Long projectId, User mentor) {
        readOnlyTransactionTemplate.executeWithoutResult(status -> {
            Project project = projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                    .orElseThrow(() -> new ResourceNotFoundException(
                            "Project", "id", projectId));
            requireFormationReady(project);
            if (formationJobRepository.existsByProjectIdAndJobStatusIn(
                    projectId, ACTIVE_JOB_STATUSES)) {
                throw FormationException.activeJob();
            }
        });
    }

    // ----------------------------------------------------------------------
    // T1: create the READY job and its immutable input snapshot
    // ----------------------------------------------------------------------

    private FormationJob createReadyJob(Long projectId, User mentor, long seed) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
        if (!Objects.equals(project.getMentor().getId(), mentor.getId())) {
            throw new ResourceNotFoundException("Project", "id", projectId);
        }
        requireFormationReady(project);

        if (formationJobRepository.existsByProjectIdAndJobStatusIn(
                projectId, ACTIVE_JOB_STATUSES)) {
            throw FormationException.activeJob();
        }

        ConstraintConfiguration configuration =
                constraintConfigurationService.loadCurrentConfiguration(projectId);
        FormationInput input = formationInputLoader.load(
                project, configuration, seed, ALGORITHM_VERSION);
        requireFeasible(input);

        FormationJob job = new FormationJob(project, configuration.version(), mentor);
        job.setSeed(seed);
        job.setAlgorithmVersion(ALGORITHM_VERSION);
        job = formationJobRepository.saveAndFlush(job);

        // The snapshot is written in the same transaction as the READY job, so
        // a committed job always has a complete authoritative input.
        snapshotStore.persist(job, input);
        return job;
    }

    private void requireFormationReady(Project project) {
        if (project.getStatus() != ProjectStatus.PUBLISHED
                && project.getStatus() != ProjectStatus.PREFERENCES_LOCKED) {
            throw FormationException.projectNotReady(
                    "project status must be PUBLISHED or PREFERENCES_LOCKED, but was "
                            + project.getStatus());
        }
        if (!projectService.arePreferencesLocked(project)) {
            throw FormationException.projectNotReady(
                    "preferences must be locked before a formation run starts");
        }
    }

    private void requireFeasible(FormationInput input) {
        if (!input.planner().feasible()) {
            throw FormationException.infeasible(input.planner().explanation());
        }

        ConstraintConfiguration configuration = new ConstraintConfiguration(
                input.constraintVersion(),
                input.prohibitedPairs(),
                input.mandatorySkills());
        List<RosterStudentSkills> roster = new ArrayList<>(input.students().size());
        for (FormationStudent student : input.students()) {
            roster.add(new RosterStudentSkills(
                    student.studentId(), student.proficiencyBySkillId()));
        }
        FeasibilityReport report = FeasibilityChecker.check(
                input.eligibleStudentCount(),
                input.minTeamSize(),
                input.maxTeamSize(),
                input.teamSizeSlack(),
                configuration,
                roster,
                input.skillNames());
        if (report.overallStatus() == FeasibilityStatus.INFEASIBLE) {
            String reason = report.findings().stream()
                    .map(finding -> finding.message())
                    .findFirst()
                    .orElse("the feasibility checks reported an infeasible configuration");
            throw FormationException.infeasible(reason);
        }
    }

    // ----------------------------------------------------------------------
    // T2: reconstruct from the snapshot, run the algorithm, persist the result
    // ----------------------------------------------------------------------

    private void executeJob(Long projectId, Long jobId) {
        Project project = projectRepository.findByIdForUpdate(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));

        FormationJob job = formationJobRepository.findByIdForUpdate(jobId)
                .orElseThrow(() -> new FormationException(
                        "Formation job " + jobId + " no longer exists", HttpStatus.CONFLICT));
        if (!Objects.equals(job.getProject().getId(), projectId)) {
            throw new FormationException(
                    "Formation job " + jobId + " does not belong to this project",
                    HttpStatus.CONFLICT);
        }
        if (!job.canStart()) {
            throw new FormationException(
                    "Formation job " + jobId + " is not READY", HttpStatus.CONFLICT);
        }

        // The T1 snapshot pins the constraint version that the run was created
        // with. A save can commit between T1 and T2, so the current version is
        // re-read under the project lock. Running with a snapshot that no longer
        // matches would produce a result that cannot be explained by the project
        // state the mentor sees, so the run fails explicitly instead.
        int currentConstraintVersion =
                constraintConfigurationService.currentVersion(projectId);
        if (!Objects.equals(currentConstraintVersion, job.getConstraintVersion())) {
            throw FormationException.constraintVersionChanged();
        }

        Instant startedAt = Instant.now();
        job.markRunning(startedAt);
        formationJobRepository.saveAndFlush(job);

        FormationInput input = snapshotStore.loadForJob(job);

        FormationSearchLimits runLimits = new FormationSearchLimits(
                searchLimits.maxNodes(),
                searchLimits.maxImprovementPasses(),
                startedAt.plus(jobProperties.executionBudget()));
        FormationPlan plan = formationEngine.form(input, runLimits);
        formationPlanValidator.validate(input, plan);

        try {
            formationPlanPersister.persist(project, job, plan);
        } catch (RuntimeException persistenceFailure) {
            throw new FormationException(
                    "Team formation failed with an internal error; the job was marked FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    FailureCode.PERSISTENCE_VERIFICATION_FAILED,
                    FailurePhase.PERSISTENCE);
        }

        long persistedTeams = candidateTeamRepository.countByFormationJobId(jobId);
        long persistedMembers = candidateTeamMemberRepository.countByFormationJobId(jobId);
        if (persistedTeams != plan.teamCount()
                || persistedMembers != plan.assignedStudentCount()) {
            throw new FormationException(
                    "Team formation failed with an internal error; the job was marked FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    FailureCode.PERSISTENCE_VERIFICATION_FAILED,
                    FailurePhase.VERIFICATION);
        }

        Instant completedAt = Instant.now();
        long durationMillis = Math.max(
                0L, Duration.between(startedAt, completedAt).toMillis());
        job.markCompleted(
                completedAt,
                plan.totalPreferenceScore(),
                plan.unsatisfiedPreferenceStudents(),
                plan.studentsWithoutPreferences(),
                durationMillis);
        formationJobRepository.saveAndFlush(job);
    }

    // ----------------------------------------------------------------------
    // T3: record a post-job failure without exposing internals
    // ----------------------------------------------------------------------

    private void markFailed(Long jobId, RuntimeException failure) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                FormationJob job = formationJobRepository.findByIdForUpdate(jobId)
                        .orElse(null);
                if (job == null) {
                    return;
                }
                if (job.getJobStatus() != JobStatus.READY
                        && job.getJobStatus() != JobStatus.RUNNING) {
                    return;
                }
                job.markFailed(
                        failureCode(failure),
                        failurePhase(failure),
                        safeReason(failure),
                        Instant.now());
                formationJobRepository.saveAndFlush(job);
            });
        } catch (RuntimeException markingFailure) {
            log.error("Could not mark formation job {} as FAILED", jobId, markingFailure);
        }
    }

    private FailureCode failureCode(RuntimeException failure) {
        if (failure instanceof FormationException formationException
                && formationException.getFailureCode() != null) {
            return formationException.getFailureCode();
        }
        if (failure instanceof FormationExecutionBudgetExceededException) {
            return FailureCode.EXECUTION_BUDGET_EXCEEDED;
        }
        if (failure instanceof FormationSearchLimitReachedException) {
            return FailureCode.SEARCH_LIMIT_REACHED;
        }
        if (failure instanceof NoValidFormationException) {
            return FailureCode.NO_VALID_PLAN;
        }
        if (failure instanceof FormationValidationException) {
            return FailureCode.PLAN_VALIDATION_FAILED;
        }
        if (failure instanceof FormationException) {
            String message = Objects.requireNonNullElse(failure.getMessage(), "");
            if (message.contains("snapshot")) {
                return FailureCode.SNAPSHOT_INTEGRITY;
            }
            if (message.contains("constraint version")) {
                return FailureCode.CONSTRAINT_VERSION_CHANGED;
            }
        }
        return FailureCode.INTERNAL_ERROR;
    }

    private FailurePhase failurePhase(RuntimeException failure) {
        if (failure instanceof FormationException formationException
                && formationException.getFailurePhase() != null) {
            return formationException.getFailurePhase();
        }
        if (failure instanceof FormationException && failureCode(failure) == FailureCode.SNAPSHOT_INTEGRITY) {
            return FailurePhase.INPUT_SNAPSHOT;
        }
        if (failure instanceof FormationValidationException) {
            return FailurePhase.VERIFICATION;
        }
        if (failure instanceof FormationSearchLimitReachedException
                || failure instanceof FormationExecutionBudgetExceededException
                || failure instanceof NoValidFormationException) {
            return FailurePhase.EXECUTION;
        }
        return FailurePhase.INTERNAL;
    }

    private String safeReason(RuntimeException failure) {
        String reason;
        if (failure instanceof FormationSearchLimitReachedException
                || failure instanceof FormationExecutionBudgetExceededException
                || failure instanceof NoValidFormationException) {
            reason = failure.getMessage();
        } else if (failure instanceof FormationValidationException validation) {
            reason = "The generated plan failed validation: "
                    + String.join("; ", validation.getViolations());
        } else if (failure instanceof FormationException) {
            reason = failure.getMessage();
        } else {
            reason = "Team formation failed with an internal error";
        }
        if (reason == null || reason.isBlank()) {
            reason = "Team formation failed";
        }
        return reason.length() <= MAX_REASON_LENGTH
                ? reason
                : reason.substring(0, MAX_REASON_LENGTH);
    }

    private FormationException toApiFailure(RuntimeException failure) {
        if (failure instanceof FormationException formationException) {
            return formationException;
        }
        if (failure instanceof FormationSearchLimitReachedException) {
            return FormationException.searchLimitReached(searchLimits.maxNodes());
        }
        if (failure instanceof FormationExecutionBudgetExceededException) {
            return FormationException.executionBudgetExceeded();
        }
        if (failure instanceof NoValidFormationException) {
            return FormationException.noValidPlan(failure.getMessage());
        }
        if (failure instanceof FormationValidationException) {
            return new FormationException(
                    "Team formation produced an invalid plan; the job was marked FAILED",
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    FailureCode.PLAN_VALIDATION_FAILED,
                    FailurePhase.VERIFICATION);
        }
        return FormationException.internalFailure();
    }

    // ----------------------------------------------------------------------
    // Reads
    // ----------------------------------------------------------------------

    public FormationJobResponse getJob(
            Authentication authentication, Long projectId, Long jobId) {
        User mentor = currentUserService.requireCurrentUserWithRole(
                authentication, UserRole.MENTOR);
        return Objects.requireNonNull(readOnlyTransactionTemplate.execute(
                status -> readJob(projectId, jobId, mentor)));
    }

    private FormationJobResponse readJob(Long projectId, Long jobId, User mentor) {
        requireOwnedProject(projectId, mentor);

        FormationJob job = formationJobRepository.findByIdAndProjectId(jobId, projectId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "FormationJob", "id", jobId));

        List<CandidateTeam> teams =
                candidateTeamRepository.findByFormationJobIdOrderByTeamNameAsc(jobId);
        List<CandidateTeamMember> members = candidateTeamMemberRepository
                .findByFormationJobIdOrderByCandidateTeamIdAscStudentIdAsc(jobId);

        Map<Long, List<CandidateTeamMember>> membersByTeam = new HashMap<>();
        for (CandidateTeamMember member : members) {
            membersByTeam.computeIfAbsent(
                    member.getCandidateTeam().getId(), ignored -> new ArrayList<>())
                    .add(member);
        }

        int memberCount = 0;
        double summedTeamScore = 0.0;
        List<CandidateTeamResponse> teamResponses = new ArrayList<>(teams.size());
        for (CandidateTeam team : teams) {
            List<CandidateTeamMember> teamMembers =
                    membersByTeam.getOrDefault(team.getId(), List.of());
            memberCount += teamMembers.size();
            Double score = team.getCompatibilityScore();
            double compatibilityScore = score == null ? 0.0 : score;
            summedTeamScore += compatibilityScore;

            List<CandidateTeamMemberResponse> memberResponses =
                    new ArrayList<>(teamMembers.size());
            for (CandidateTeamMember member : teamMembers) {
                User student = member.getStudent();
                memberResponses.add(new CandidateTeamMemberResponse(
                        student.getId(),
                        student.getFullName(),
                        member.getSource()));
            }
            teamResponses.add(new CandidateTeamResponse(
                    team.getId(),
                    team.getTeamName(),
                    team.getStatus(),
                    compatibilityScore,
                    teamMembers.size(),
                    List.copyOf(memberResponses)));
        }

        // Completion-time metrics are authoritative. For legacy jobs created
        // before V2.11 they are null; the total falls back to the stored team
        // scores and the count metrics stay 0 because they cannot be recovered
        // without re-reading live preferences, which is explicitly forbidden.
        double totalScore = job.getTotalPreferenceScore() != null
                ? job.getTotalPreferenceScore()
                : summedTeamScore;
        int unsatisfied = job.getUnsatisfiedPreferenceStudents() != null
                ? job.getUnsatisfiedPreferenceStudents()
                : 0;
        int withoutPreferences = job.getStudentsWithoutPreferences() != null
                ? job.getStudentsWithoutPreferences()
                : 0;

        return new FormationJobResponse(
                job.getId(),
                projectId,
                job.getJobStatus(),
                job.getConstraintVersion(),
                job.getSeed(),
                job.getAlgorithmVersion(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getErrorMessage(),
                job.getFailureCode(),
                job.getFailurePhase(),
                job.getDurationMillis(),
                job.getCreatedAt(),
                teams.size(),
                memberCount,
                totalScore,
                unsatisfied,
                withoutPreferences,
                List.copyOf(teamResponses));
    }

    public PageResponse<FormationJobSummaryResponse> listJobs(
            Authentication authentication, Long projectId, JobStatus status, Pageable pageable) {
        User mentor = currentUserService.requireCurrentUserWithRole(
                authentication, UserRole.MENTOR);
        return Objects.requireNonNull(readOnlyTransactionTemplate.execute(transactionStatus -> {
            requireOwnedProject(projectId, mentor);
            Page<FormationJob> page = status == null
                    ? formationJobRepository.findByProjectId(projectId, pageable)
                    : formationJobRepository.findByProjectIdAndJobStatus(
                            projectId, status, pageable);
            return PageResponse.from(page.map(this::toSummary));
        }));
    }

    private Project requireOwnedProject(Long projectId, User mentor) {
        return projectRepository.findByIdAndMentorId(projectId, mentor.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Project", "id", projectId));
    }

    private FormationJobSummaryResponse toSummary(FormationJob job) {
        return new FormationJobSummaryResponse(
                job.getId(),
                job.getProject().getId(),
                job.getJobStatus(),
                job.getConstraintVersion(),
                job.getSeed(),
                job.getAlgorithmVersion(),
                job.getStartedAt(),
                job.getCompletedAt(),
                job.getErrorMessage(),
                job.getFailureCode(),
                job.getFailurePhase(),
                job.getDurationMillis(),
                job.getCreatedAt());
    }
}
