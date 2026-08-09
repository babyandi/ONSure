package kr.co.oruda.onsure.platform.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PostgresqlMigrationConfigurationTest {

    @Test
    void acceptsAValidLoopbackConfiguration() {
        var configuration = new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", "secret", "onsure", false);
        assertEquals("jdbc:postgresql://127.0.0.1:5432/onsure", configuration.jdbcUrl());
        assertEquals("onsure", configuration.username());
        assertEquals("onsure", configuration.schema());
        assertFalse(configuration.migrationAuthorized());
        assertTrue(configuration.toString().contains("<redacted>"));
        assertFalse(configuration.toString().contains("secret"));
    }

    @Test
    void acceptsLocalhostAsALoopbackHost() {
        var configuration = new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://localhost:5432/onsure", "onsure", "secret", "onsure", true);
        assertTrue(configuration.migrationAuthorized());
    }

    @Test
    void rejectsAJdbcUrlThatDoesNotStartWithPostgresqlScheme() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:mysql://127.0.0.1:3306/onsure", "onsure", "secret", "onsure", false));
    }

    @Test
    void rejectsANonLoopbackHost() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new PostgresqlMigrationConfiguration(
                        "jdbc:postgresql://db.example.com:5432/onsure", "onsure", "secret", "onsure", false));
        assertTrue(failure.getMessage().contains("loopback"));
    }

    @Test
    void rejectsEmbeddedUserinfoInTheJdbcUrl() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://onsure:secret@127.0.0.1:5432/onsure", "onsure", "secret", "onsure", false));
    }

    @Test
    void rejectsAMissingPath() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432", "onsure", "secret", "onsure", false));
    }

    @Test
    void rejectsAnInvalidUsername() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "1nvalid user", "secret", "onsure", false));
    }

    @Test
    void rejectsAMissingPassword() {
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new PostgresqlMigrationConfiguration(
                        "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", null, "onsure", false));
        assertTrue(failure.getMessage().contains("ONSURE_DB_PASSWORD"));
    }

    @Test
    void rejectsABlankPassword() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", "   ", "onsure", false));
    }

    @Test
    void rejectsAPasswordContainingANewline() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", "secret\ninjected", "onsure", false));
    }

    @Test
    void rejectsAnInvalidSchemaName() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", "secret", "Not-A-Valid-Schema", false));
    }

    @Test
    void migrationIsNotAuthorizedByDefaultFromEnvironmentWhenFlagIsAbsent() {
        // fromEnvironment() defaults ONSURE_MIGRATION_AUTHORIZED to "false" when unset; this test only
        // asserts the constructor-level default-deny behavior since we cannot safely mutate process
        // environment variables from a unit test.
        var configuration = new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", "secret", "onsure", false);
        assertFalse(configuration.migrationAuthorized());
    }
}
