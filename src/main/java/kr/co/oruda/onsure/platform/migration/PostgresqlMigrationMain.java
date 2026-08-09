package kr.co.oruda.onsure.platform.migration;

import java.util.Locale;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;

/** Fail-closed command-line entry point for ONSure PostgreSQL schema migrations. */
public final class PostgresqlMigrationMain {
    private PostgresqlMigrationMain() {}

    public static void main(String[] args) {
        try {
            if (args.length != 1) throw new IllegalArgumentException("usage: preflight|validate|info|migrate");
            String action = args[0].toLowerCase(Locale.ROOT);
            PostgresqlMigrationConfiguration configuration = PostgresqlMigrationConfiguration.fromEnvironment();
            if ("preflight".equals(action)) {
                System.out.println("ONSURE_POSTGRESQL_PREFLIGHT PASS_NONFINAL schema=" + configuration.schema()
                        + " migration_authorized=" + configuration.migrationAuthorized());
                return;
            }
            if ("migrate".equals(action) && !configuration.migrationAuthorized()) {
                throw new IllegalStateException("MIGRATION_NOT_AUTHORIZED");
            }
            Flyway flyway = flyway(configuration);
            switch (action) {
                case "validate" -> {
                    flyway.validate();
                    System.out.println("ONSURE_POSTGRESQL_VALIDATE PASS_NONFINAL");
                }
                case "info" -> {
                    MigrationInfo[] pending = flyway.info().pending();
                    System.out.println("ONSURE_POSTGRESQL_INFO PASS_NONFINAL pending=" + pending.length);
                }
                case "migrate" -> {
                    int executed = flyway.migrate().migrationsExecuted;
                    System.out.println("ONSURE_POSTGRESQL_MIGRATE PASS_NONFINAL executed=" + executed);
                }
                default -> throw new IllegalArgumentException("unsupported action");
            }
        } catch (RuntimeException failure) {
            System.err.println("ONSURE_POSTGRESQL_FAIL_CLOSED " + safeFailure(failure));
            System.exit(1);
        }
    }

    static Flyway flyway(PostgresqlMigrationConfiguration configuration) {
        return Flyway.configure()
                .dataSource(configuration.jdbcUrl(), configuration.username(), configuration.password())
                .defaultSchema(configuration.schema())
                .schemas(configuration.schema())
                .createSchemas(true)
                .locations("classpath:db/migration/postgresql")
                .failOnMissingLocations(true)
                .validateMigrationNaming(true)
                .cleanDisabled(true)
                .load();
    }

    private static String safeFailure(RuntimeException failure) {
        if (failure instanceof IllegalArgumentException || failure instanceof IllegalStateException) {
            String message = failure.getMessage();
            return message == null ? failure.getClass().getSimpleName() : message;
        }
        return failure.getClass().getSimpleName();
    }
}
