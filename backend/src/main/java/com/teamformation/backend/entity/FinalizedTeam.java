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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "formation_job_id")
    private FormationJob formationJob;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_team_id")
    private CandidateTeam candidateTeam;

    @Column(name = "constraint_version")
    private Integer constraintVersion;

    @Column(name = "seed")
    private Long seed;

    @Column(name = "algorithm_version", length = 50)
    private String algorithmVersion;

    @Column(name = "compatibility_score")
    private Double compatibilityScore;

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

    /**
     * Creates a finalized team with full Engine 3 provenance. Engine 4 owns the
     * workflow; this constructor only defines the provenance contract.
     */
    public FinalizedTeam(
            Project project,
            FormationJob formationJob,
            CandidateTeam candidateTeam,
            String teamName,
            User finalizedBy,
            Integer constraintVersion,
            Long seed,
            String algorithmVersion,
            Double compatibilityScore) {
        this(project, teamName, finalizedBy);
        this.formationJob = formationJob;
        this.candidateTeam = candidateTeam;
        this.constraintVersion = constraintVersion;
        this.seed = seed;
        this.algorithmVersion = algorithmVersion;
        this.compatibilityScore = compatibilityScore;
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

    public CandidateTeam getCandidateTeam() {
        return candidateTeam;
    }

    public void setCandidateTeam(CandidateTeam candidateTeam) {
        this.candidateTeam = candidateTeam;
    }

    public Integer getConstraintVersion() {
        return constraintVersion;
    }

    public void setConstraintVersion(Integer constraintVersion) {
        this.constraintVersion = constraintVersion;
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

    public Double getCompatibilityScore() {
        return compatibilityScore;
    }

    public void setCompatibilityScore(Double compatibilityScore) {
        this.compatibilityScore = compatibilityScore;
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

