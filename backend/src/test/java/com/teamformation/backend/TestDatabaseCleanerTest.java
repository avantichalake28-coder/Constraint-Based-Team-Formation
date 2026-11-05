package com.teamformation.backend;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The destructive integration-test cleaner must only accept a Testcontainers
 * style MySQL URL: a randomised localhost port and the database name "test".
 */
class TestDatabaseCleanerTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "jdbc:mysql://localhost:32768/test",
            "jdbc:mysql://127.0.0.1:32769/test?useSSL=false&serverTimezone=UTC",
            "jdbc:mysql://[::1]:49153/test",
            "jdbc:mysql://LOCALHOST:3307/test",
            "jdbc:mysql://localhost:3307/test/extra"
    })
    @DisplayName("A randomised localhost MySQL test database is accepted")
    void validate_acceptsTestcontainersUrls(String jdbcUrl) {
        assertThatCode(() -> TestDatabaseCleaner.validateTestDatabaseUrl(jdbcUrl))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("The real development database name is refused even on a Testcontainers-like port")
    void validate_refusesRealDatabaseName() {
        assertThatThrownBy(() -> TestDatabaseCleaner.validateTestDatabaseUrl(
                "jdbc:mysql://localhost:3307/student_team_formation"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to clean a database");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "jdbc:h2:mem:test",
            "jdbc:postgresql://localhost:32768/test",
            "jdbc:mysql://localhost:3306/test",
            "jdbc:mysql://localhost:3307/student_team_formation",
            "jdbc:mysql://localhost:3307/Test",
            "jdbc:mysql://db.example.com:3307/test",
            "jdbc:mysql://192.168.1.5:3307/test",
            "jdbc:mysql://localhost/test",
            "jdbc:mysql://localhost:notaport/test",
            "jdbc:mysql://localhost:0/test",
            "jdbc:mysql://localhost:65536/test",
            "jdbc:mysql://localhost:3307/",
            "jdbc:mysql://:3307/test",
            "mysql://localhost:3307/test"
    })
    @DisplayName("Anything that is not clearly the Testcontainers database is refused")
    void validate_refusesEverythingElse(String jdbcUrl) {
        assertThatThrownBy(() -> TestDatabaseCleaner.validateTestDatabaseUrl(jdbcUrl))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Refusing to clean a database");
    }
}
