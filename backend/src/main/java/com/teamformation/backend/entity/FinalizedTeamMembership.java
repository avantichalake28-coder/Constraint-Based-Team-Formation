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
 * Membership of a student in a finalized team.
 * Enforces the invariant: a student can belong to only one finalized team per project.
 * Corresponds to FR-10, FR-12 and owned by Engine E4.
 */
@Entity
@Table(name = "finalized_team_memberships", uniqueConstraints = {
        @UniqueConstraint(name = "uk_finalized_members_project_student", columnNames = {"project_id", "student_id"}),
        @UniqueConstraint(name = "uk_finalized_members_team_student", columnNames = {"finalized_team_id", "student_id"})
})
public class FinalizedTeamMembership {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finalized_team_id", nullable = false)
    private FinalizedTeam finalizedTeam;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", length = 20, nullable = false)
    private MembershipSource source = MembershipSource.ALGORITHM;

    @Column(name = "assigned_at", nullable = false, updatable = false)
    private Instant assignedAt = Instant.now();

    protected FinalizedTeamMembership() {
    }

    public FinalizedTeamMembership(FinalizedTeam finalizedTeam, Project project, User student, MembershipSource source) {
        this.finalizedTeam = finalizedTeam;
        this.project = project;
        this.student = student;
        this.source = (source != null) ? source : MembershipSource.ALGORITHM;
        this.assignedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public FinalizedTeam getFinalizedTeam() {
        return finalizedTeam;
    }

    public void setFinalizedTeam(FinalizedTeam finalizedTeam) {
        this.finalizedTeam = finalizedTeam;
    }

    public Project getProject() {
        return project;
    }

    public void setProject(Project project) {
        this.project = project;
    }

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public MembershipSource getSource() {
        return source;
    }

    public void setSource(MembershipSource source) {
        this.source = source;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinalizedTeamMembership that)) return false;
        return Objects.equals(project, that.project) && Objects.equals(student, that.student);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project, student);
    }
}

