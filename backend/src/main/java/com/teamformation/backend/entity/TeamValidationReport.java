package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.ValidationStatus;
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
 * Validation report produced after evaluating candidate teams against hard and soft constraints.
 * Corresponds to FR-12, FR-13 and owned by Engine E4.
 */
@Entity
@Table(name = "team_validation_reports")
public class TeamValidationReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_job_id")
    private FormationJob formationJob;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", length = 20, nullable = false)
    private ValidationStatus validationStatus = ValidationStatus.PASSED;

    @Column(name = "summary", length = 2000)
    private String summary;

    @Column(name = "evaluated_at", nullable = false, updatable = false)
    private Instant evaluatedAt = Instant.now();

    protected TeamValidationReport() {
    }

    public TeamValidationReport(Project project, FormationJob formationJob, ValidationStatus validationStatus, String summary) {
        this.project = project;
        this.formationJob = formationJob;
        this.validationStatus = validationStatus;
        this.summary = summary;
        this.evaluatedAt = Instant.now();
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

    public FormationJob getFormationJob() {
        return formationJob;
    }

    public void setFormationJob(FormationJob formationJob) {
        this.formationJob = formationJob;
    }

    public ValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(ValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public Instant getEvaluatedAt() {
        return evaluatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeamValidationReport that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

