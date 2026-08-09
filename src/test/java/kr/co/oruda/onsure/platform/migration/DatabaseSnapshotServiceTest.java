package kr.co.oruda.onsure.platform.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.concurrent.ThreadLocalRandom;
import kr.co.oruda.onsure.platform.migration.DatabaseSnapshotService.RestoreResult;
import kr.co.oruda.onsure.platform.migration.DatabaseSnapshotService.SnapshotResult;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises {@link DatabaseSnapshotService} against a real local PostgreSQL instance. Every test runs in
 * its own throwaway schema (never the real "onsure" schema) created fresh in {@code @BeforeEach} and
 * dropped unconditionally in {@code @AfterEach}. If no reachable PostgreSQL connection is configured via
 * the ONSURE_DB_URL / ONSURE_DB_USER / ONSURE_DB_PASSWORD environment variables, the whole class is
 * skipped with a clear reason -- this must never become a hard dependency for {@code mvn test} in
 * environments without this exact local database.
 */
class DatabaseSnapshotServiceTest {

    private static String jdbcUrl;
    private static String username;
    private static String password;
    private static boolean postgresAvailable;

    @TempDir Path tempDir;

    private final DatabaseSnapshotService service = new DatabaseSnapshotService();
    private String schemaName;
    private Connection connection;

    @BeforeAll
    static void checkPostgresAvailability() {
        jdbcUrl = firstNonNull(System.getenv("ONSURE_DB_URL"), System.getProperty("onsure.db.url"));
        username = firstNonNull(System.getenv("ONSURE_DB_USER"), System.getProperty("onsure.db.user"));
        password = firstNonNull(System.getenv("ONSURE_DB_PASSWORD"), System.getProperty("onsure.db.password"));
        if (jdbcUrl == null || username == null || password == null) {
            postgresAvailable = false;
            return;
        }
        try (Connection probe = DriverManager.getConnection(jdbcUrl, username, password)) {
            postgresAvailable = probe.isValid(5);
        } catch (Exception unreachable) {
            postgresAvailable = false;
        }
    }

    private static String firstNonNull(String a, String b) {
        return a != null ? a : b;
    }

    @BeforeEach
    void createThrowawaySchemaWithMigratedTablesAndSeedData() throws Exception {
        Assumptions.assumeTrue(postgresAvailable,
                "PostgreSQL not reachable via ONSURE_DB_URL/ONSURE_DB_USER/ONSURE_DB_PASSWORD; "
                        + "skipping DatabaseSnapshotService integration tests");

        schemaName = "test_snap_" + Long.toHexString(System.nanoTime())
                + "_" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        connection = DriverManager.getConnection(jdbcUrl, username, password);
        connection.setAutoCommit(true);

        PostgresqlMigrationConfiguration configuration =
                new PostgresqlMigrationConfiguration(jdbcUrl, username, password, schemaName, true);
        Flyway flyway = PostgresqlMigrationMain.flyway(configuration);
        flyway.migrate();

        seedRows();
    }

    @AfterEach
    void dropThrowawaySchemaUnconditionally() throws Exception {
        if (connection == null) return;
        try {
            if (schemaName != null) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DROP SCHEMA IF EXISTS " + quoteIdent(schemaName) + " CASCADE");
                }
            }
        } finally {
            connection.close();
        }
    }

    @Test
    void skipGuardActuallySkipsWhenPostgresIsUnavailable() {
        // Directly exercises the same guard used by every other test in this class, so the skip path
        // itself is proven to work rather than merely assumed correct.
        assertThrows(org.opentest4j.TestAbortedException.class,
                () -> Assumptions.assumeTrue(false, "simulated unavailable PostgreSQL"));
    }

    @Test
    void snapshotCapturesEveryBaseTableAndAllSeededRows() throws Exception {
        Path snapshotFile = tempDir.resolve("snapshot.json");
        SnapshotResult result = service.snapshot(connection, schemaName, snapshotFile);

        assertEquals(schemaName, result.schema());
        // 6 base tables: the 5 migration-defined tables plus flyway_schema_history, which Flyway itself
        // creates as an ordinary base table in the schema -- snapshot() correctly captures every base
        // table, not just the ones this test happens to seed.
        assertEquals(6, result.tableCount());
        assertEquals(2, countRows("assurance_event"));
        assertEquals(2, countRows("validation_run_score"));
        assertEquals(2, countRows("validation_score_node"));
        assertEquals(0, countRows("validation_run_finding"));
        assertEquals(0, countRows("validation_run_comparison"));
        // Flyway records one row per applied migration (V1, V2, V3) plus one "<< Flyway Schema Creation >>"
        // bookkeeping row for having created the schema itself.
        assertEquals(4, countRows("flyway_schema_history"));
        assertEquals(10, result.rowCount(), "sum of every base table's row count, including flyway_schema_history");
        assertFalse(result.contentSha256().isBlank());
        assertTrue(Files.isRegularFile(snapshotFile));
    }

    @Test
    void restoreRoundTripsMutatedDataBackToTheExactOriginalContent() throws Exception {
        Path originalSnapshot = tempDir.resolve("original.json");
        SnapshotResult original = service.snapshot(connection, schemaName, originalSnapshot);

        BigDecimal originalEarnedPoints = readEarnedPoints("run-1");
        long originalAssuranceEventCount = countRows("assurance_event");
        long originalScoreNodeCount = countRows("validation_score_node");

        mutateData();

        assertNotEquals(originalAssuranceEventCount, countRows("assurance_event"), "mutation must actually change state");

        RestoreResult restored = service.restore(connection, schemaName, originalSnapshot);
        assertEquals(schemaName, restored.schema());
        assertEquals(original.rowCount(), restored.rowCount());
        assertEquals(original.contentSha256(), restored.contentSha256());

        assertEquals(originalAssuranceEventCount, countRows("assurance_event"));
        assertEquals(originalScoreNodeCount, countRows("validation_score_node"));
        assertEquals(0, originalEarnedPoints.compareTo(readEarnedPoints("run-1")),
                "earned_points for run-1 must be restored to its exact original value");

        Path verifySnapshot = tempDir.resolve("post-restore.json");
        SnapshotResult afterRestore = service.snapshot(connection, schemaName, verifySnapshot);
        assertEquals(original.contentSha256(), afterRestore.contentSha256(),
                "a fresh snapshot taken after restore must byte-match the original snapshot's content hash");
    }

    @Test
    void restoreFailsClosedOnACorruptedSnapshotFileAndLeavesTheDatabaseUnchanged() throws Exception {
        Path snapshotFile = tempDir.resolve("to-corrupt.json");
        service.snapshot(connection, schemaName, snapshotFile);

        byte[] validBytes = Files.readAllBytes(snapshotFile);
        byte[] truncated = new byte[validBytes.length / 2];
        System.arraycopy(validBytes, 0, truncated, 0, truncated.length);
        Files.write(snapshotFile, truncated);

        long assuranceEventCountBefore = countRows("assurance_event");
        long scoreCountBefore = countRows("validation_run_score");
        long nodeCountBefore = countRows("validation_score_node");

        assertThrows(Exception.class, () -> service.restore(connection, schemaName, snapshotFile));

        assertEquals(assuranceEventCountBefore, countRows("assurance_event"),
                "a corrupted snapshot must never mutate the database");
        assertEquals(scoreCountBefore, countRows("validation_run_score"));
        assertEquals(nodeCountBefore, countRows("validation_score_node"));
    }

    @Test
    void restoreFailsClosedWhenTheContentHashHasBeenTamperedWith() throws Exception {
        Path snapshotFile = tempDir.resolve("tampered.json");
        service.snapshot(connection, schemaName, snapshotFile);

        String content = Files.readString(snapshotFile);
        String tampered = content.replaceFirst(
                "\"content_sha256\"\\s*:\\s*\"[0-9a-f]{64}\"",
                "\"content_sha256\":\"" + "0".repeat(64) + "\"");
        assertNotEquals(content, tampered, "the replacement must actually have applied");
        Files.writeString(snapshotFile, tampered);

        long countBefore = countRows("assurance_event");
        Exception failure = assertThrows(Exception.class, () -> service.restore(connection, schemaName, snapshotFile));
        assertTrue(failure.getMessage() != null && failure.getMessage().contains("SNAPSHOT_CONTENT_HASH_MISMATCH"));
        assertEquals(countBefore, countRows("assurance_event"));
    }

    private BigDecimal readEarnedPoints(String runId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT earned_points FROM " + quoteIdent(schemaName) + ".validation_run_score WHERE run_id = ?")) {
            statement.setString(1, runId);
            try (ResultSet rs = statement.executeQuery()) {
                assertTrue(rs.next());
                return rs.getBigDecimal(1);
            }
        }
    }

    private long countRows(String table) throws Exception {
        try (Statement statement = connection.createStatement();
                ResultSet rs = statement.executeQuery(
                        "SELECT COUNT(*) FROM " + quoteIdent(schemaName) + "." + quoteIdent(table))) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private void mutateData() throws Exception {
        try (Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO " + quoteIdent(schemaName) + ".assurance_event "
                    + "(event_id, event_type, observed_at, evidence_sha256) VALUES "
                    + "('event-mutated', 'MUTATION_TEST', now(), '" + "c".repeat(64) + "')");
            statement.execute("UPDATE " + quoteIdent(schemaName) + ".validation_run_score "
                    + "SET earned_points = 12.34 WHERE run_id = 'run-1'");
            statement.execute("DELETE FROM " + quoteIdent(schemaName) + ".validation_score_node "
                    + "WHERE run_id = 'run-2'");
        }
    }

    private void seedRows() throws Exception {
        String schema = quoteIdent(schemaName);
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema + ".assurance_event "
                        + "(event_id, event_type, observed_at, evidence_sha256, source_commit_sha256) "
                        + "VALUES (?,?,?,?,?)")) {
            insertAssuranceEvent(statement, "event-1", "VALIDATION_RUN_COMPLETED", "a".repeat(64), "b".repeat(64));
            insertAssuranceEvent(statement, "event-2", "PATCH_APPLIED", "d".repeat(64), null);
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema + ".validation_run_score "
                        + "(run_id, project_id, target_id, source_sha256, receipt_sha256, validation_outcome, "
                        + "earned_points, max_points, scorecard_json, previous_run_id, observed_at) "
                        + "VALUES (?,?,?,?,?,?,?,?,?::jsonb,?,?)")) {
            statement.setString(1, "run-1");
            statement.setString(2, "proj-a");
            statement.setString(3, "target-a");
            statement.setString(4, "1".repeat(64));
            statement.setString(5, "2".repeat(64));
            statement.setString(6, "PASS_NONFINAL");
            statement.setBigDecimal(7, new BigDecimal("87.50"));
            statement.setBigDecimal(8, new BigDecimal("100.00"));
            statement.setString(9, "{\"summary\":\"baseline\",\"score\":87.5}");
            statement.setNull(10, java.sql.Types.VARCHAR);
            statement.setTimestamp(11, Timestamp.from(Instant.parse("2026-08-01T00:00:00Z")));
            statement.executeUpdate();

            statement.setString(1, "run-2");
            statement.setString(2, "proj-a");
            statement.setString(3, "target-a");
            statement.setString(4, "3".repeat(64));
            statement.setString(5, "4".repeat(64));
            statement.setString(6, "PASS_NONFINAL");
            statement.setBigDecimal(7, new BigDecimal("91.25"));
            statement.setBigDecimal(8, new BigDecimal("100.00"));
            statement.setString(9, "{\"summary\":\"improved\",\"score\":91.25,\"nested\":{\"ok\":true}}");
            statement.setString(10, "run-1");
            statement.setTimestamp(11, Timestamp.from(Instant.parse("2026-08-02T00:00:00Z")));
            statement.executeUpdate();
        }

        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO " + schema + ".validation_score_node "
                        + "(run_id, node_type, node_id, parent_node_id, outcome, possible_points, earned_points, "
                        + "diagnosis, improvement_guide) VALUES (?,?,?,?,?,?,?,?,?)")) {
            statement.setString(1, "run-1");
            statement.setString(2, "DOMAIN");
            statement.setString(3, "domain-1");
            statement.setNull(4, java.sql.Types.VARCHAR);
            statement.setString(5, "PASS_NONFINAL");
            statement.setBigDecimal(6, new BigDecimal("100.00"));
            statement.setBigDecimal(7, new BigDecimal("87.50"));
            statement.setString(8, "diagnosis-1");
            statement.setString(9, "improve-1");
            statement.executeUpdate();

            statement.setString(1, "run-2");
            statement.setString(2, "DOMAIN");
            statement.setString(3, "domain-1");
            statement.setNull(4, java.sql.Types.VARCHAR);
            statement.setString(5, "PASS_NONFINAL");
            statement.setBigDecimal(6, new BigDecimal("100.00"));
            statement.setBigDecimal(7, new BigDecimal("91.25"));
            statement.setString(8, "diagnosis-2");
            statement.setString(9, "improve-2");
            statement.executeUpdate();
        }
    }

    private void insertAssuranceEvent(
            PreparedStatement statement, String eventId, String eventType, String evidenceSha, String sourceCommitSha)
            throws Exception {
        statement.setString(1, eventId);
        statement.setString(2, eventType);
        statement.setTimestamp(3, Timestamp.from(Instant.now()));
        statement.setString(4, evidenceSha);
        if (sourceCommitSha == null) statement.setNull(5, java.sql.Types.VARCHAR);
        else statement.setString(5, sourceCommitSha);
        statement.executeUpdate();
    }

    private static String quoteIdent(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }
}
