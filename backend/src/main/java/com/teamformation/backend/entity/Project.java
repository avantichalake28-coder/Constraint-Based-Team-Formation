package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.ProjectStatus;
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
import jakarta.persistence.Version;
import jakarta.validation.constraints.Min;
import org.hibernate.annotations.DynamicUpdate;

import java.time.Instant;
import java.util.Objects;

/**
 * Academic project entity defined by mentors.
 * Corresponds to FR-03, FR-04 and owned by Engine E1 (data) & Engine E2 (rules).
 */
@Entity
@DynamicUpdate
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "mentor_id", nullable = false)
    private User mentor;

    @Column(name = "title", length = 200, nullable = false)
    private String title;

    @Column(name = "problem_statement", length = 2000, nullable = false)
    private String problemStatement;

    @Column(name = "course_code", length = 50, nullable = false)
    private String courseCode;

    @Min(2)
    @Column(name = "min_team_size", nullable = false)
    private Integer minTeamSize = 2;

    @Column(name = "max_team_size", nullable = false)
    private Integer maxTeamSize = 4;

    @Column(name = "team_size_slack", nullable = false)
    private Integer teamSizeSlack = 1;

    @Column(name = "preference_deadline")
    private Instant preferenceDeadline;

    @Column(name = "preferences_locked_at")
    private Instant preferencesLockedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private ProjectStatus status = ProjectStatus.DRAFT;

    @Version
    @Column(name = "version", nullable = false)
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected Project() {
    }

    public Project(User mentor, String title, String problemStatement, String courseCode, Integer minTeamSize, Integer maxTeamSize, Integer teamSizeSlack, Instant preferenceDeadline) {
        this.mentor = mentor;
        this.title = title;
        this.problemStatement = problemStatement;
        this.courseCode = courseCode;
        this.minTeamSize = (minTeamSize != null) ? minTeamSize : 2;
        this.maxTeamSize = (maxTeamSize != null) ? maxTeamSize : 4;
        this.teamSizeSlack = (teamSizeSlack != null) ? teamSizeSlack : 1;
        this.preferenceDeadline = preferenceDeadline;
        this.status = ProjectStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getMentor() {
        return mentor;
    }

    public void setMentor(User mentor) {
        this.mentor = mentor;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getProblemStatement() {
        return problemStatement;
    }

    public void setProblemStatement(String problemStatement) {
        this.problemStatement = problemStatement;
    }

    public String getCourseCode() {
        return courseCode;
    }

    public void setCourseCode(String courseCode) {
        this.courseCode = courseCode;
    }

    public Integer getMinTeamSize() {
        return minTeamSize;
    }

    public void setMinTeamSize(Integer minTeamSize) {
        this.minTeamSize = minTeamSize;
    }

    public Integer getMaxTeamSize() {
        return maxTeamSize;
    }

    public void setMaxTeamSize(Integer maxTeamSize) {
        this.maxTeamSize = maxTeamSize;
    }

    public Integer getTeamSizeSlack() {
        return teamSizeSlack;
    }

    public void setTeamSizeSlack(Integer teamSizeSlack) {
        this.teamSizeSlack = teamSizeSlack;
    }

    public Instant getPreferenceDeadline() {
        return preferenceDeadline;
    }

    public void setPreferenceDeadline(Instant preferenceDeadline) {
        this.preferenceDeadline = preferenceDeadline;
    }

    public Instant getPreferencesLockedAt() {
        return preferencesLockedAt;
    }

    public void setPreferencesLockedAt(Instant preferencesLockedAt) {
        this.preferencesLockedAt = preferencesLockedAt;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    /**
     * Optimistic-lock value. A new project starts at zero; every project row
     * mutation increments it. It is never set by callers.
     */
    public Long getVersion() {
        return version;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Project project)) return false;
        return Objects.equals(id, project.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
