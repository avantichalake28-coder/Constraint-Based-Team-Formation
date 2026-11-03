package com.teamformation.backend;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test that uses Testcontainers to start a real MySQL 8.0 instance.
 * A missing Docker daemon fails the build; tests are never skipped.
 *
 * <p>Starting the Spring context also runs Flyway V1-V4 and Hibernate schema
 * validation ({@code ddl-auto: validate}) against the migrated schema.</p>
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
@ActiveProfiles("test")
class BackendApplicationTests {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void contextLoadsAndHibernateValidatesMigratedSchema() {
        String version = jdbcTemplate.queryForObject("SELECT VERSION()", String.class);
        assertThat(version).startsWith("8.0");

        Integer appliedMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1 AND version IN ('1','2','3','4')",
                Integer.class);
        assertThat(appliedMigrations).isEqualTo(4);
    }
}
