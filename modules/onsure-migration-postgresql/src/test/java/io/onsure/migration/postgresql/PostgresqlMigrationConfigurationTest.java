package io.onsure.migration.postgresql;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

import org.junit.jupiter.api.Test;

class PostgresqlMigrationConfigurationTest {
    @Test
    void acceptsLoopbackPostgresqlWithoutEmbeddedCredentials() {
        PostgresqlMigrationConfiguration configuration = new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure?sslmode=disable",
                "onsure", "secret-fixture", "onsure", false);
        assertEquals("onsure", configuration.schema());
        assertFalse(configuration.toString().contains("secret-fixture"));
    }

    @Test
    void rejectsRemoteDatabaseAndEmbeddedCredentials() {
        assertThrows(IllegalArgumentException.class, () -> configuration("db.example.com", "onsure"));
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://user:pass@127.0.0.1:5432/onsure", "onsure", "secret", "onsure", false));
    }

    @Test
    void rejectsInvalidSchemaAndMissingPassword() {
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", null, "onsure", false));
        assertThrows(IllegalArgumentException.class, () -> new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/onsure", "onsure", "secret", "Bad-Schema", false));
    }

    private static PostgresqlMigrationConfiguration configuration(String host, String user) {
        return new PostgresqlMigrationConfiguration(
                "jdbc:postgresql://" + host + ":5432/onsure", user, "secret", "onsure", false);
    }
}
