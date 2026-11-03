package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.FindingSeverity;
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
 * Individual validation finding or violation record associated with a validation report.
 * Provides granular explainability for why a team passed or failed.
 * Corresponds to FR-12, FR-14 and owned by Engine E4.
 */
@Entity
@Table(name = "validation_findings")
public class ValidationFinding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "report_id", nullable = false)
    private TeamValidationReport report;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_team_id")
    private CandidateTeam candidateTeam;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", length = 20, nullable = false)
    private FindingSeverity severity = FindingSeverity.WARNING;

    @Column(name = "rule_code", length = 100, nullable = false)
    private String ruleCode;

    @Column(name = "message", length = 2000, nullable = false)
    private String message;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ValidationFinding() {
    }

    public ValidationFinding(TeamValidationReport report, CandidateTeam candidateTeam, FindingSeverity severity, String ruleCode, String message) {
        this.report = report;
        this.candidateTeam = candidateTeam;
        this.severity = severity;
        this.ruleCode = ruleCode;
        this.message = message;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public TeamValidationReport getReport() {
        return report;
    }

    public void setReport(TeamValidationReport report) {
        this.report = report;
    }

    public CandidateTeam getCandidateTeam() {
        return candidateTeam;
    }

    public void setCandidateTeam(CandidateTeam candidateTeam) {
        this.candidateTeam = candidateTeam;
    }

    public FindingSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(FindingSeverity severity) {
        this.severity = severity;
    }

    public String getRuleCode() {
        return ruleCode;
    }

    public void setRuleCode(String ruleCode) {
        this.ruleCode = ruleCode;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ValidationFinding that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
