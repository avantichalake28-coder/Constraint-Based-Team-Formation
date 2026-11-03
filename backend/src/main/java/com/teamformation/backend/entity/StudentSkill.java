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
 * Mapping between a student profile and their skills with proficiency (1 to 5).
 * Corresponds to FR-02 and owned by Engine E1.
 */
@Entity
@Table(name = "student_skills", uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_skill", columnNames = { "profile_id", "skill_id" })
})
public class StudentSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "profile_id", nullable = false)
    private StudentProfile studentProfile;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    @Min(1)
    @Max(5)
    @Column(name = "proficiency_level", nullable = false)
    private Integer proficiencyLevel = 1;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    protected StudentSkill() {
    }

    public StudentSkill(StudentProfile studentProfile, Skill skill, Integer proficiencyLevel) {
        this.studentProfile = studentProfile;
        this.skill = skill;
        this.proficiencyLevel = (proficiencyLevel != null) ? proficiencyLevel : 1;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public StudentProfile getStudentProfile() {
        return studentProfile;
    }

    public void setStudentProfile(StudentProfile studentProfile) {
        this.studentProfile = studentProfile;
    }

    public Skill getSkill() {
        return skill;
    }

    public void setSkill(Skill skill) {
        this.skill = skill;
    }

    public Integer getProficiencyLevel() {
        return proficiencyLevel;
    }

    public void setProficiencyLevel(Integer proficiencyLevel) {
        this.proficiencyLevel = proficiencyLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (!(o instanceof StudentSkill that))
            return false;
        return Objects.equals(studentProfile, that.studentProfile) && Objects.equals(skill, that.skill);
    }

    @Override
    public int hashCode() {
        return Objects.hash(studentProfile, skill);
    }
}
