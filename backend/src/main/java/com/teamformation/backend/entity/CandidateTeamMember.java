package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.MembershipSource;
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
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;
import java.util.Objects;

/**
 * Membership of a student in a candidate team during a formation run.
 * Corresponds to FR-08, FR-10 and owned by Engine E3.
 */
@Entity
@Table(name = "candidate_team_members", uniqueConstraints = {
        @UniqueConstraint(name = "uk_candidate_team_member", columnNames = {"candidate_team_id", "student_id"}),
        @UniqueConstraint(name = "uk_candidate_members_job_student", columnNames = {"formation_job_id", "student_id"})
})
public class CandidateTeamMember {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "candidate_team_id", nullable = false)
    private CandidateTeam candidateTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Column(name = "formation_job_id", nullable = false)
    private Long formationJobId;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private MembershipSource source = MembershipSource.ALGORITHM;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changed_by")
    private User changedBy;

    @Column(name = "changed_at")
    private Instant changedAt;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    protected CandidateTeamMember() {
    }

    public CandidateTeamMember(CandidateTeam candidateTeam, User student, Long formationJobId, MembershipSource source) {
        this.candidateTeam = candidateTeam;
        this.student = student;
        this.formationJobId = formationJobId;
        this.source = (source != null) ? source : MembershipSource.ALGORITHM;
        this.assignedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public CandidateTeam getCandidateTeam() {
        return candidateTeam;
    }

    public void setCandidateTeam(CandidateTeam candidateTeam) {
        this.candidateTeam = candidateTeam;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public Long getFormationJobId() {
        return formationJobId;
    }

    public void setFormationJobId(Long formationJobId) {
        this.formationJobId = formationJobId;
    }

    public MembershipSource getSource() {
        return source;
    }

    public void setSource(MembershipSource source) {
        this.source = source;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(User changedBy) {
        this.changedBy = changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CandidateTeamMember that)) return false;
        return Objects.equals(candidateTeam, that.candidateTeam) && Objects.equals(student, that.student);
    }

    @Override
    public int hashCode() {
        return Objects.hash(candidateTeam, student);
    }
}

