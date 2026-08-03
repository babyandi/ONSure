package io.onsure.migration.postgresql;

import java.net.URI;
import java.util.Map;

/** Validated local PostgreSQL connection facts; password values must never be logged. */
public final class PostgresqlMigrationConfiguration {
    private final String jdbcUrl;
    private final String username;
    private final String password;
    private final String schema;
    private final boolean migrationAuthorized;

    public PostgresqlMigrationConfiguration(
            String jdbcUrl, String username, String password, String schema, boolean migrationAuthorized) {
        validateJdbcUrl(jdbcUrl);
        if (username == null || !username.matches("[A-Za-z_][A-Za-z0-9_-]{0,62}")) {
            throw new IllegalArgumentException("ONSURE_DB_USER");
        }
        if (password == null || password.isBlank() || password.length() > 4096
                || password.indexOf('\n') >= 0 || password.indexOf('\r') >= 0) {
            throw new IllegalArgumentException("ONSURE_DB_PASSWORD is required");
        }
        if (schema == null || !schema.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("ONSURE_DB_SCHEMA");
        }
        this.jdbcUrl = jdbcUrl;
        this.username = username;
        this.password = password;
        this.schema = schema;
        this.migrationAuthorized = migrationAuthorized;
    }

    public static PostgresqlMigrationConfiguration fromEnvironment() {
        Map<String, String> environment = System.getenv();
        return new PostgresqlMigrationConfiguration(
                environment.getOrDefault("ONSURE_DB_URL", "jdbc:postgresql://127.0.0.1:5432/onsure"),
                environment.getOrDefault("ONSURE_DB_USER", "onsure"),
                environment.get("ONSURE_DB_PASSWORD"),
                environment.getOrDefault("ONSURE_DB_SCHEMA", "onsure"),
                "true".equals(environment.getOrDefault("ONSURE_MIGRATION_AUTHORIZED", "false")));
    }

    public String jdbcUrl() { return jdbcUrl; }
    public String username() { return username; }
    String password() { return password; }
    public String schema() { return schema; }
    public boolean migrationAuthorized() { return migrationAuthorized; }

    @Override
    public String toString() {
        return "PostgresqlMigrationConfiguration[jdbcUrl=" + jdbcUrl + ", username=" + username
                + ", password=<redacted>, schema=" + schema
                + ", migrationAuthorized=" + migrationAuthorized + "]";
    }

    private static void validateJdbcUrl(String value) {
        if (value == null || !value.startsWith("jdbc:postgresql://") || value.length() > 2048) {
            throw new IllegalArgumentException("ONSURE_DB_URL");
        }
        URI uri;
        try {
            uri = URI.create(value.substring("jdbc:".length()));
        } catch (IllegalArgumentException invalid) {
            throw new IllegalArgumentException("ONSURE_DB_URL", invalid);
        }
        if (!"postgresql".equals(uri.getScheme())
                || !("127.0.0.1".equals(uri.getHost()) || "localhost".equals(uri.getHost()))
                || uri.getUserInfo() != null
                || uri.getPath() == null || uri.getPath().length() < 2) {
            throw new IllegalArgumentException("ONSURE_DB_URL must target loopback PostgreSQL without embedded credentials");
        }
    }
}
