package com.teamformation.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Generated;
import org.hibernate.generator.EventType;

import java.time.Instant;

/**
 * Maps to the {@code app_metadata} table created by V1__init.sql.
 * Provides a lightweight entity for Hibernate schema validation.
 */
@Entity
@Table(name = "app_metadata")
public class AppMetadata {

    @Id
    @Column(name = "meta_key", nullable = false, length = 100)
    private String metaKey;

    @Column(name = "meta_value", length = 500)
    private String metaValue;

    /**
     * Database default supplies the value. Hibernate must neither write this
     * column on insert nor overwrite it on update; it reads the generated value
     * back so callers see the database timestamp.
     */
    @Generated(event = EventType.INSERT)
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /**
     * Database default supplies the insert value and MySQL's ON UPDATE clause
     * maintains it. Hibernate reads the generated value after insert/update.
     */
    @Generated(event = {EventType.INSERT, EventType.UPDATE})
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AppMetadata() {
        // JPA requires a no-arg constructor.
    }

    public AppMetadata(String metaKey, String metaValue) {
        this.metaKey = metaKey;
        this.metaValue = metaValue;
    }

    public String getMetaKey() {
        return metaKey;
    }

    public void setMetaKey(String metaKey) {
        this.metaKey = metaKey;
    }

    public String getMetaValue() {
        return metaValue;
    }

    public void setMetaValue(String metaValue) {
        this.metaValue = metaValue;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}

