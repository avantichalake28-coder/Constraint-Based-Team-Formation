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

import java.time.Instant;
import java.util.Objects;

/**
 * Finalized team approved and locked by the mentor.
 * Separate from candidate teams.
 * Corresponds to FR-12, FR-15 and owned by Engine E4.
 */
@Entity
@Table(name = "finalized_teams", uniqueConstraints = {
        @UniqueConstraint(name = "uk_finalized_teams_id_project", columnNames = {"id", "project_id"})
})
public class FinalizedTeam {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(name = "team_name", length = 100, nullable = false)
    private String teamName;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "finalized_by", nullable = false)
    private User finalizedBy;

    @Column(name = "finalized_at", nullable = false, updatable = false)
    private Instant finalizedAt = Instant.now();

    protected FinalizedTeam() {
    }

    public FinalizedTeam(Project project, String teamName, User finalizedBy) {
        this.project = project;
        this.teamName = teamName;
        this.finalizedBy = finalizedBy;
        this.finalizedAt = Instant.now();
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

    public String getTeamName() {
        return teamName;
    }

    public void setTeamName(String teamName) {
        this.teamName = teamName;
    }

    public User getFinalizedBy() {
        return finalizedBy;
    }

    public void setFinalizedBy(User finalizedBy) {
        this.finalizedBy = finalizedBy;
    }

    public Instant getFinalizedAt() {
        return finalizedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FinalizedTeam that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

