package com.teamformation.backend.entity;

import com.teamformation.backend.entity.enums.ConstraintRuleType;
import com.teamformation.backend.entity.enums.ConstraintType;
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
 * Hard constraints and soft preferences configured for a project.
 * Supports versioning (Slide 5) and feasibility checks.
 * Corresponds to FR-05, FR-06 and owned by Engine E2.
 */
@Entity
@Table(name = "project_constraints")
public class ProjectConstraint {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Enumerated(EnumType.STRING)
    @Column(name = "constraint_type", length = 20, nullable = false)
    private ConstraintType constraintType;

    @Enumerated(EnumType.STRING)
    @Column(name = "rule_type", length = 50, nullable = false)
    private ConstraintRuleType ruleType;

    @Column(name = "rule_configuration", length = 2000, nullable = false)
    private String ruleConfiguration;

    @Column(name = "weight")
    private Double weight = 1.0;

    @Column(name = "version", nullable = false)
    private Integer version = 1;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected ProjectConstraint() {
    }

    public ProjectConstraint(Project project, ConstraintType constraintType, ConstraintRuleType ruleType, String ruleConfiguration, Double weight, Integer version) {
        this.project = project;
        this.constraintType = constraintType;
        this.ruleType = ruleType;
        this.ruleConfiguration = ruleConfiguration;
        this.weight = weight;
        this.version = (version != null) ? version : 1;
        this.active = true;
        this.createdAt = Instant.now();
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

    public ConstraintType getConstraintType() {
        return constraintType;
    }

    public void setConstraintType(ConstraintType constraintType) {
        this.constraintType = constraintType;
    }

    public ConstraintRuleType getRuleType() {
        return ruleType;
    }

    public void setRuleType(ConstraintRuleType ruleType) {
        this.ruleType = ruleType;
    }

    public String getRuleConfiguration() {
        return ruleConfiguration;
    }

    public void setRuleConfiguration(String ruleConfiguration) {
        this.ruleConfiguration = ruleConfiguration;
    }

    public Double getWeight() {
        return weight;
    }

    public void setWeight(Double weight) {
        this.weight = weight;
    }

    public Integer getVersion() {
        return version;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ProjectConstraint that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}

