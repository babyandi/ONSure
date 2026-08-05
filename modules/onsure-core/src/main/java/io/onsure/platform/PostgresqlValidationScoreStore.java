package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Optional loopback PostgreSQL persistence for score history; disabled unless explicitly configured. */
final class PostgresqlValidationScoreStore {
    static final String CONTRACT = "ONSURE_POSTGRESQL_VALIDATION_SCORE_STORE_V1";
    private final Map<String, String> environment;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    PostgresqlValidationScoreStore(Map<String, String> environment) {
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
    }

    boolean configured() {
        return "POSTGRESQL".equalsIgnoreCase(environment.getOrDefault("ONSURE_SCORE_STORE", "DISABLED"));
    }

    Map<String, Object> persist(
            String projectId, String targetId, String runId, String sourceSha256,
            String receiptSha256, Instant observedAt, JsonNode scorecard) throws Exception {
        if (!configured()) return Map.of(
                "contract", CONTRACT, "state", "NOT_CONFIGURED", "durable", false);
        requireScorecard(scorecard);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                String previousRunId = previousRunId(connection, targetId);
                insertRun(connection, projectId, targetId, runId, sourceSha256,
                        receiptSha256, observedAt, scorecard, previousRunId);
                insertNodes(connection, runId, scorecard);
                if (previousRunId != null) insertComparison(connection, targetId, previousRunId, runId, scorecard);
                connection.commit();
                return Map.of(
                        "contract", CONTRACT, "state", "STORED", "durable", true,
                        "run_id", runId, "previous_run_id", previousRunId == null ? "NOT_RUN" : previousRunId);
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    List<Map<String, Object>> history(String targetId, int limit) throws Exception {
        if (!configured()) return List.of();
        String sql = "SELECT run_id, source_sha256, receipt_sha256, validation_outcome, "
                + "earned_points, max_points, observed_at FROM validation_run_score "
                + "WHERE target_id=? ORDER BY observed_at DESC LIMIT ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, targetId);
            statement.setInt(2, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> result = new ArrayList<>();
                while (rows.next()) result.add(Map.of(
                        "run_id", rows.getString(1),
                        "source_sha256", rows.getString(2),
                        "receipt_sha256", rows.getString(3),
                        "validation_outcome", rows.getString(4),
                        "earned_points", rows.getBigDecimal(5),
                        "max_points", rows.getBigDecimal(6),
                        "observed_at", rows.getObject(7).toString(),
                        "final_claim_allowed", false));
                return List.copyOf(result);
            }
        }
    }

    private Connection connection() throws Exception {
        String url = environment.getOrDefault(
                "ONSURE_DB_URL", "jdbc:postgresql://127.0.0.1:5432/onsure");
        requireLoopback(url);
        String user = required("ONSURE_DB_USER");
        String password = required("ONSURE_DB_PASSWORD");
        Class.forName("org.postgresql.Driver");
        return DriverManager.getConnection(url, user, password);
    }

    private void insertRun(
            Connection connection, String projectId, String targetId, String runId,
            String sourceSha256, String receiptSha256, Instant observedAt,
            JsonNode scorecard, String previousRunId) throws Exception {
        String sql = "INSERT INTO validation_run_score "
                + "(run_id, project_id, target_id, source_sha256, receipt_sha256, validation_outcome, "
                + "earned_points, max_points, scorecard_json, previous_run_id, observed_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            statement.setString(2, projectId);
            statement.setString(3, targetId);
            statement.setString(4, sourceSha256);
            statement.setString(5, receiptSha256);
            statement.setString(6, scorecard.path("validation_outcome").asText());
            statement.setBigDecimal(7, scorecard.path("earned_points").decimalValue());
            statement.setBigDecimal(8, scorecard.path("max_points").decimalValue());
            statement.setString(9, mapper.writeValueAsString(scorecard));
            statement.setString(10, previousRunId);
            statement.setObject(11, observedAt);
            statement.executeUpdate();
        }
    }

    private void insertNodes(Connection connection, String runId, JsonNode scorecard) throws Exception {
        String sql = "INSERT INTO validation_score_node "
                + "(run_id, node_type, node_id, parent_node_id, outcome, possible_points, earned_points, "
                + "diagnosis, improvement_guide, output_sha256, environment_sha256) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            addNodes(statement, runId, "DOMAIN", scorecard.path("assessment_domains"), "area_id");
            addNodes(statement, runId, "PHASE", scorecard.path("phases"), "phase");
            addNodes(statement, runId, "GROUP", scorecard.path("groups"), "group");
            addNodes(statement, runId, "AREA", scorecard.path("assessment_areas"), "area_id");
            addNodes(statement, runId, "STEP", scorecard.path("steps"), "step_id");
            statement.executeBatch();
        }
    }

    private void addNodes(
            PreparedStatement statement, String runId, String nodeType,
            JsonNode nodes, String idField) throws Exception {
        if (!nodes.isArray()) return;
        for (JsonNode node : nodes) {
            statement.setString(1, runId);
            statement.setString(2, nodeType);
            statement.setString(3, node.path(idField).asText());
            statement.setString(4, parent(nodeType, node));
            statement.setString(5, node.path("outcome").asText("NOT_RUN"));
            statement.setBigDecimal(6, node.path("possible_points").decimalValue());
            statement.setBigDecimal(7, node.path("earned_points").decimalValue());
            statement.setString(8, node.path("diagnosis").asText());
            statement.setString(9, node.path("improvement_guide").asText());
            nullable(statement, 10, node.path("output_sha256").asText(""));
            nullable(statement, 11, node.path("environment_sha256").asText(""));
            statement.addBatch();
        }
    }

    private void insertComparison(
            Connection connection, String targetId, String previousRunId,
            String currentRunId, JsonNode current) throws Exception {
        String select = "SELECT scorecard_json FROM validation_run_score WHERE run_id=?";
        JsonNode baseline;
        try (PreparedStatement statement = connection.prepareStatement(select)) {
            statement.setString(1, previousRunId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return;
                baseline = mapper.readTree(row.getString(1));
            }
        }
        Map<String, Object> comparison = ValidationScorecardComparison.compare(
                previousRunId, baseline, currentRunId, current);
        String insert = "INSERT INTO validation_run_comparison "
                + "(comparison_id, target_id, baseline_run_id, current_run_id, total_delta_points, comparison_json) "
                + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb))";
        try (PreparedStatement statement = connection.prepareStatement(insert)) {
            statement.setString(1, "comparison-" + previousRunId + "-" + currentRunId);
            statement.setString(2, targetId);
            statement.setString(3, previousRunId);
            statement.setString(4, currentRunId);
            statement.setBigDecimal(5, (java.math.BigDecimal) comparison.get("total_delta_points"));
            statement.setString(6, mapper.writeValueAsString(comparison));
            statement.executeUpdate();
        }
    }

    private String previousRunId(Connection connection, String targetId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT run_id FROM validation_run_score WHERE target_id=? ORDER BY observed_at DESC LIMIT 1")) {
            statement.setString(1, targetId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private static String parent(String nodeType, JsonNode node) {
        return switch (nodeType) {
            case "AREA" -> node.path("domain").asText(null);
            case "STEP" -> node.path("group").asText(null);
            default -> null;
        };
    }

    private static void nullable(PreparedStatement statement, int index, String value) throws Exception {
        if (value == null || value.isBlank()) statement.setNull(index, java.sql.Types.CHAR);
        else statement.setString(index, value);
    }

    private static void requireScorecard(JsonNode scorecard) {
        if (scorecard == null || !ValidationScorecard.CONTRACT.equals(scorecard.path("contract").asText())) {
            throw new IllegalArgumentException("VALIDATION_SCORECARD_INVALID");
        }
    }

    private String required(String key) {
        String value = environment.get(key);
        if (value == null || value.isBlank()) throw new IllegalStateException(key + "_REQUIRED");
        return value;
    }

    static void requireLoopback(String jdbcUrl) {
        if (jdbcUrl == null || !jdbcUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalArgumentException("ONSURE_SCORE_DB_URL_INVALID");
        }
        URI uri = URI.create(jdbcUrl.substring("jdbc:".length()));
        if (!SetHolder.LOOPBACK.contains(uri.getHost())) {
            throw new SecurityException("ONSURE_SCORE_DB_MUST_BE_LOOPBACK");
        }
    }

    private static final class SetHolder {
        private static final java.util.Set<String> LOOPBACK = java.util.Set.of("127.0.0.1", "localhost", "::1", "[::1]");
    }
}
