package com.teamformation.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import java.time.Instant;
import java.util.Objects;

/**
 * Student profile details including SRN, department, semester, and interests.
 * Corresponds to FR-02 and owned by Engine E1.
 */
@Entity
@Table(name = "student_profiles")
public class StudentProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @NotBlank
    @Pattern(regexp = "^[0-9]{2}FE[0-9]{2}[A-Z]{3}[0-9]{3}$", message = "SRN must match format: 2 digits, FE, 2 digits, 3 letters, 3 digits (e.g. 02FE24BCS141)")
    @Column(name = "srn", length = 50, nullable = false, unique = true)
    private String srn;

    @Column(name = "department", length = 100)
    private String department;

    @Column(name = "semester")
    private Integer semester;

    @Column(name = "interests", length = 1000)
    private String interests;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected StudentProfile() {
    }

    public StudentProfile(User user, String srn, String department, Integer semester, String interests) {
        this.user = user;
        setSrn(srn);
        this.department = department;
        this.semester = semester;
        this.interests = interests;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public String getSrn() {
        return srn;
    }

    public void setSrn(String srn) {
        this.srn = (srn != null) ? srn.trim().toUpperCase() : null;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public Integer getSemester() {
        return semester;
    }

    public void setSemester(Integer semester) {
        this.semester = semester;
    }

    public String getInterests() {
        return interests;
    }

    public void setInterests(String interests) {
        this.interests = interests;
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
        if (this == o)
            return true;
        if (!(o instanceof StudentProfile that))
            return false;
        return Objects.equals(srn, that.srn);
    }

    @Override
    public int hashCode() {
        return Objects.hash(srn);
    }
}
