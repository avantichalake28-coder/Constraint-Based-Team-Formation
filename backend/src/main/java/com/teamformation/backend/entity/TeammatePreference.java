package com.teamformation.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.Instant;
import java.util.Objects;

/**
 * Teammate preference submitted by a student for a project (at most 2, ranked 1 or 2).
 * Corresponds to FR-07, FR-09 and owned by Engine E1 (submission) and Engine E3 (conflict resolution).
 */
@Entity
@Table(name = "teammate_preferences", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_preference", columnNames = {"project_id", "student_id", "preferred_student_id"}),
        @UniqueConstraint(name = "uk_preferences_project_student_rank", columnNames = {"project_id", "student_id", "priority_rank"})
})
public class TeammatePreference {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "student_id", nullable = false)
    private User student;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "preferred_student_id", nullable = false)
    private User preferredStudent;

    @Min(1)
    @Max(2)
    @Column(name = "priority_rank", nullable = false)
    private Integer rank = 1;

    @Column(name = "submitted_at", nullable = false, updatable = false)
    private Instant submittedAt = Instant.now();

    protected TeammatePreference() {
    }

    public TeammatePreference(Project project, User student, User preferredStudent, Integer rank) {
        this.project = project;
        this.student = student;
        this.preferredStudent = preferredStudent;
        this.rank = (rank != null) ? rank : 1;
        this.submittedAt = Instant.now();
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

    public User getPreferredStudent() {
        return preferredStudent;
    }

    public void setPreferredStudent(User preferredStudent) {
        this.preferredStudent = preferredStudent;
    }

    public Integer getRank() {
        return rank;
    }

    public void setRank(Integer rank) {
        this.rank = rank;
    }

    public Instant getSubmittedAt() {
        return submittedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TeammatePreference that)) return false;
        return Objects.equals(project, that.project) &&
                Objects.equals(student, that.student) &&
                Objects.equals(preferredStudent, that.preferredStudent);
    }

    @Override
    public int hashCode() {
        return Objects.hash(project, student, preferredStudent);
    }
}
