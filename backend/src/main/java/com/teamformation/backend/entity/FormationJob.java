package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.FailureCode;
import com.teamformation.backend.entity.enums.FailurePhase;
import com.teamformation.backend.entity.enums.JobStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Objects;

/**
 * Formation job execution entity tracking job states, seed, and algorithm version.
 * Corresponds to Slide 6 & 7 (Engine 3 job lifecycle) and owned by Engine E3.
 */
@Entity
@Table(name = "formation_jobs")
public class FormationJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "job_status", length = 20, nullable = false)
    private JobStatus jobStatus = JobStatus.READY;

    @Column(name = "constraint_version", nullable = false)
    private Integer constraintVersion = 1;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triggered_by_id", nullable = false)
    private User triggeredBy;

    @Column(name = "seed")
    private Long seed;

    @Column(name = "algorithm_version", length = 50)
    private String algorithmVersion;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_code", length = 50)
    private FailureCode failureCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "failure_phase", length = 30)
    private FailurePhase failurePhase;

    @Column(name = "total_preference_score")
    private Double totalPreferenceScore;

    @Column(name = "unsatisfied_preference_students")
    private Integer unsatisfiedPreferenceStudents;

    @Column(name = "students_without_preferences")
    private Integer studentsWithoutPreferences;

    @Column(name = "duration_millis")
    private Long durationMillis;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected FormationJob() {
    }

    public FormationJob(Project project, Integer constraintVersion, User triggeredBy) {
        this.project = project;
        this.constraintVersion = (constraintVersion != null) ? constraintVersion : 1;
        this.triggeredBy = triggeredBy;
        this.jobStatus = JobStatus.READY;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public JobStatus getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(JobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    public Integer getConstraintVersion() {
        return constraintVersion;
    }

    public void setConstraintVersion(Integer constraintVersion) {
        this.constraintVersion = constraintVersion;
    }

    public User getTriggeredBy() {
        return triggeredBy;
    }

    public void setTriggeredBy(User triggeredBy) {
        this.triggeredBy = triggeredBy;
    }

    public Long getSeed() {
        return seed;
    }

    public void setSeed(Long seed) {
        this.seed = seed;
    }

    public String getAlgorithmVersion() {
        return algorithmVersion;
    }

    public void setAlgorithmVersion(String algorithmVersion) {
        this.algorithmVersion = algorithmVersion;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(Instant startedAt) {
        this.startedAt = startedAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public void setCompletedAt(Instant completedAt) {
        this.completedAt = completedAt;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public FailureCode getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(FailureCode failureCode) {
        this.failureCode = failureCode;
    }

    public FailurePhase getFailurePhase() {
        return failurePhase;
    }

    public void setFailurePhase(FailurePhase failurePhase) {
        this.failurePhase = failurePhase;
    }

    public Double getTotalPreferenceScore() {
        return totalPreferenceScore;
    }

    public void setTotalPreferenceScore(Double totalPreferenceScore) {
        this.totalPreferenceScore = totalPreferenceScore;
    }

    public Integer getUnsatisfiedPreferenceStudents() {
        return unsatisfiedPreferenceStudents;
    }

    public void setUnsatisfiedPreferenceStudents(Integer unsatisfiedPreferenceStudents) {
        this.unsatisfiedPreferenceStudents = unsatisfiedPreferenceStudents;
    }

    public Integer getStudentsWithoutPreferences() {
        return studentsWithoutPreferences;
    }

    public void setStudentsWithoutPreferences(Integer studentsWithoutPreferences) {
        this.studentsWithoutPreferences = studentsWithoutPreferences;
    }

    public Long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(Long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /**
     * Moves a READY job into RUNNING. Only this transition is allowed.
     */
    public void markRunning(Instant startedAt) {
        requireStatus(JobStatus.READY, "start");
        this.jobStatus = JobStatus.RUNNING;
        this.startedAt = startedAt;
    }

    /**
     * Moves a RUNNING job into COMPLETED. Only this transition is allowed.
     */
    public void markCompleted(Instant completedAt) {
        markCompleted(completedAt, null, null, null, null);
    }

    /**
     * Moves a RUNNING job into COMPLETED and records the completion-time
     * preference metrics so later reads never recompute them from live data.
     */
    public void markCompleted(
            Instant completedAt,
            Double totalPreferenceScore,
            Integer unsatisfiedPreferenceStudents,
            Integer studentsWithoutPreferences,
            Long durationMillis) {
        requireStatus(JobStatus.RUNNING, "complete");
        this.jobStatus = JobStatus.COMPLETED;
        this.completedAt = completedAt;
        this.totalPreferenceScore = totalPreferenceScore;
        this.unsatisfiedPreferenceStudents = unsatisfiedPreferenceStudents;
        this.studentsWithoutPreferences = studentsWithoutPreferences;
        this.durationMillis = durationMillis;
        clearFailure();
    }

    /**
     * Moves a READY or RUNNING job into FAILED. The message is truncated to the
     * database column width and must never contain a stack trace. The legacy
     * overload records an internal failure; callers that know the phase should
     * use {@link #markFailed(FailureCode, FailurePhase, String, Instant)}.
     */
    public void markFailed(String reason, Instant completedAt) {
        markFailed(FailureCode.INTERNAL_ERROR, FailurePhase.INTERNAL, reason, completedAt);
    }

    public void markFailed(
            FailureCode code,
            FailurePhase phase,
            String reason,
            Instant completedAt) {
        if (jobStatus != JobStatus.READY && jobStatus != JobStatus.RUNNING) {
            throw new IllegalStateException(
                    "Cannot fail formation job in state " + jobStatus + " (expected READY or RUNNING)");
        }
        this.jobStatus = JobStatus.FAILED;
        this.errorMessage = truncate(reason, 2000);
        this.failureCode = code;
        this.failurePhase = phase;
        this.completedAt = completedAt;
    }

    /**
     * A successful run must never retain stale failure information.
     */
    private void clearFailure() {
        this.errorMessage = null;
        this.failureCode = null;
        this.failurePhase = null;
    }

    /**
     * Returns true only for the READY to RUNNING transition.
     */
    public boolean canStart() {
        return jobStatus == JobStatus.READY;
    }

    private void requireStatus(JobStatus expected, String action) {
        if (jobStatus != expected) {
            throw new IllegalStateException(
                    "Cannot " + action + " formation job in state " + jobStatus + " (expected " + expected + ")");
        }
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FormationJob that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
