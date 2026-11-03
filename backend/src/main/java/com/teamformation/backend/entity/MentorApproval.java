package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.ApprovalStatus;
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
 * Mentor approval decision recording auditability, user identity, decision timestamp, and comments.
 * Corresponds to NFR-06, FR-12 and owned by Engine E4.
 */
@Entity
@Table(name = "mentor_approvals")
public class MentorApproval {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "validation_report_id", nullable = false)
    private TeamValidationReport validationReport;

    @Enumerated(EnumType.STRING)
    @Column(name = "approval_status", length = 20, nullable = false)
    private ApprovalStatus approvalStatus = ApprovalStatus.APPROVED;

    @Column(name = "decision_timestamp", nullable = false)
    private Instant decisionTimestamp = Instant.now();

    @Column(name = "remarks", length = 1000)
    private String remarks;

    protected MentorApproval() {
    }

    public MentorApproval(Project project, User mentor, TeamValidationReport validationReport, ApprovalStatus approvalStatus, String remarks) {
        this.project = project;
        this.mentor = mentor;
        this.validationReport = validationReport;
        this.approvalStatus = approvalStatus;
        this.remarks = remarks;
        this.decisionTimestamp = Instant.now();
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

    public User getMentor() {
        return mentor;
    }

    public void setMentor(User mentor) {
        this.mentor = mentor;
    }

    public TeamValidationReport getValidationReport() {
        return validationReport;
    }

    public void setValidationReport(TeamValidationReport validationReport) {
        this.validationReport = validationReport;
    }

    public ApprovalStatus getApprovalStatus() {
        return approvalStatus;
    }

    public void setApprovalStatus(ApprovalStatus approvalStatus) {
        this.approvalStatus = approvalStatus;
    }

    public Instant getDecisionTimestamp() {
        return decisionTimestamp;
    }

    public void setDecisionTimestamp(Instant decisionTimestamp) {
        this.decisionTimestamp = decisionTimestamp;
    }

    public String getRemarks() {
        return remarks;
    }

    public void setRemarks(String remarks) {
        this.remarks = remarks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MentorApproval that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

