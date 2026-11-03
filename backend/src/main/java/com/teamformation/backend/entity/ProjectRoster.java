package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.RosterEligibilityStatus;
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
 * Eligible student roster entry for a project.
 * Corresponds to FR-03, FR-10, FR-11 and owned by Engine E1 (roster) and Engine E2 (divisibility).
 */
@Entity
@Table(name = "project_roster", uniqueConstraints = {
        @UniqueConstraint(name = "uk_project_student_roster", columnNames = {"project_id", "student_id"})
})
public class ProjectRoster {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @Enumerated(EnumType.STRING)
    @Column(name = "eligibility_status", length = 20, nullable = false)
    private RosterEligibilityStatus eligibilityStatus = RosterEligibilityStatus.ELIGIBLE;

    @Column(name = "enrolled_at", nullable = false, updatable = false)
    private Instant enrolledAt = Instant.now();

    protected ProjectRoster() {
    }

    public ProjectRoster(Project project, User student, RosterEligibilityStatus eligibilityStatus) {
        this.project = project;
        this.student = student;
        this.eligibilityStatus = eligibilityStatus;
        this.enrolledAt = Instant.now();
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

    public User getStudent() {
        return student;
    }

    public void setStudent(User student) {
        this.student = student;
    }

    public RosterEligibilityStatus getEligibilityStatus() {
        return eligibilityStatus;
    }

    public void setEligibilityStatus(RosterEligibilityStatus eligibilityStatus) {
        this.eligibilityStatus = eligibilityStatus;
    }

    public Instant getEnrolledAt() {
        return enrolledAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectRoster that)) return false;
        return Objects.equals(project, that.project) && Objects.equals(student, that.student);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project, student);
    }
}
