package com.teamformation.backend;

import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

/**
 * Removes all mutable rows between non-transactional integration tests.
 * Schema-history and seed metadata tables are intentionally preserved.
 *
 * <p>The cleaner refuses to touch anything that does not look like the
 * Testcontainers MySQL database: a localhost URL with a non-default randomised
 * host port and the database name {@code test}. This prevents an accidental
 * {@code clean verify} from truncating a real database such as
 * {@code student_team_formation}.</p>
 */
public final class TestDatabaseCleaner {

    private static final String MYSQL_URL_PREFIX = "jdbc:mysql://";
    private static final String TEST_DATABASE_NAME = "test";
    private static final int REAL_MYSQL_DEFAULT_PORT = 3306;

    private static final List<String> MUTABLE_TABLES = List.of(
            "validation_findings",
            "mentor_approvals",
            "team_validation_reports",
            "finalized_team_memberships",
            "finalized_teams",
            "candidate_team_members",
            "candidate_teams",
            "formation_input_snapshot_skills",
            "formation_input_snapshot_skill_names",
            "formation_input_snapshot_preferences",
            "formation_input_snapshot_constraints",
            "formation_input_snapshot_students",
            "formation_input_snapshots",
            "formation_jobs",
            "project_constraint_versions",
            "project_constraints",
            "teammate_preferences",
            "project_roster",
            "projects",
            "email_verification_tokens",
            "student_skills",
            "student_profiles",
            "skills",
            "users");

    private TestDatabaseCleaner() {
    }

    public static void clean(JdbcTemplate jdbcTemplate) {
        String url = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getURL());
        validateTestDatabaseUrl(url);

        jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
        try {
            for (String table : MUTABLE_TABLES) {
                jdbcTemplate.execute("TRUNCATE TABLE " + table);
            }
        } finally {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        }
    }

    /**
     * Visible for testing. Throws {@link IllegalStateException} when the URL is
     * not a Testcontainers-style MySQL database.
     */
    public static void validateTestDatabaseUrl(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith(MYSQL_URL_PREFIX)) {
            throw refuse(jdbcUrl);
        }

        String withoutScheme = jdbcUrl.substring(MYSQL_URL_PREFIX.length());
        int queryStart = withoutScheme.indexOf('?');
        if (queryStart >= 0) {
            withoutScheme = withoutScheme.substring(0, queryStart);
        }
        int slash = withoutScheme.indexOf('/');
        if (slash < 0) {
            throw refuse(jdbcUrl);
        }

        String hostAndPort = withoutScheme.substring(0, slash);
        String databaseAndPath = withoutScheme.substring(slash + 1);
        int pathSeparator = databaseAndPath.indexOf('/');
        String database = pathSeparator >= 0
                ? databaseAndPath.substring(0, pathSeparator)
                : databaseAndPath;

        int colon = hostAndPort.lastIndexOf(':');
        if (colon <= 0 || colon == hostAndPort.length() - 1) {
            throw refuse(jdbcUrl);
        }
        String host = hostAndPort.substring(0, colon);
        String portText = hostAndPort.substring(colon + 1);

        int port;
        try {
            port = Integer.parseInt(portText);
        } catch (NumberFormatException ex) {
            throw refuse(jdbcUrl);
        }

        boolean localHost = "localhost".equalsIgnoreCase(host)
                || "127.0.0.1".equals(host)
                || "[::1]".equals(host);
        if (!localHost
                || port <= 0
                || port > 65535
                || port == REAL_MYSQL_DEFAULT_PORT
                || !TEST_DATABASE_NAME.equals(database)) {
            throw refuse(jdbcUrl);
        }
    }

    private static IllegalStateException refuse(String jdbcUrl) {
        return new IllegalStateException(
                "Refusing to clean a database that is not a Testcontainers MySQL database "
                        + "(expected a randomised localhost port and database name 'test'): " + jdbcUrl);
    }
}
