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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Optional loopback PostgreSQL authoritative score history; disabled unless explicitly configured. */
final class PostgresqlValidationScoreStore {
    static final String CONTRACT = "ONSURE_POSTGRESQL_VALIDATION_SCORE_STORE_V1";
    static final String RUN_RECORD_CONTRACT = "ONSURE_POSTGRESQL_VALIDATION_RUN_RECORD_V1";
    private static final Set<String> OUTCOMES = Set.of(
            "PASS_NONFINAL", "FAIL", "BLOCKED", "NOT_RUN", "INCONCLUSIVE");
    private final Map<String, String> environment;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    PostgresqlValidationScoreStore(Map<String, String> environment) {
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
    }

    boolean configured() {
        return "POSTGRESQL".equalsIgnoreCase(environment.getOrDefault("ONSURE_SCORE_STORE", "DISABLED"));
    }

    record Evidence(
            JsonNode receipt, JsonNode report, JsonNode findings,
            String sourceCommitSha, String evidenceManifestSha256, String reportSha256) {}

    Map<String, Object> persist(
            String projectId, String targetId, String runId, String sourceSha256,
            String receiptSha256, Instant observedAt, JsonNode scorecard,
            Evidence evidence) throws Exception {
        if (!configured()) return Map.of(
                "contract", CONTRACT, "state", "NOT_CONFIGURED", "durable", false);
        requireIdentity(projectId, "PROJECT_ID");
        requireIdentity(targetId, "TARGET_ID");
        requireRunId(runId);
        requireDigest(sourceSha256, "SOURCE_SHA256");
        requireDigest(receiptSha256, "RECEIPT_SHA256");
        requireScorecard(scorecard);
        RunRecord record = runRecord(projectId, targetId, runId, sourceSha256,
                receiptSha256, scorecard, evidence);
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> existing = detail(connection, projectId, targetId, runId);
                if (!existing.isEmpty()) {
                    verifyReadBack(existing, record, scorecard);
                    connection.rollback();
                    return Map.of(
                            "contract", CONTRACT, "state", "ALREADY_STORED_VERIFIED",
                            "durable", true, "read_back", "VERIFIED", "run_id", runId,
                            "previous_run_id", existing.getOrDefault("previous_run_id", "NOT_RUN"));
                }
                String previousRunId = previousRunId(connection, projectId, targetId);
                insertRun(connection, record, observedAt, scorecard, previousRunId);
                insertNodes(connection, runId, scorecard);
                insertFindings(connection, runId, evidence.findings());
                if (previousRunId != null) {
                    insertComparison(connection, projectId, targetId, previousRunId, record, scorecard);
                }
                Map<String, Object> stored = detail(connection, projectId, targetId, runId);
                verifyReadBack(stored, record, scorecard);
                connection.commit();
                return Map.of(
                        "contract", CONTRACT, "state", "STORED", "durable", true,
                        "read_back", "VERIFIED", "run_id", runId,
                        "previous_run_id", previousRunId == null ? "NOT_RUN" : previousRunId,
                        "run_record_sha256", record.runRecordSha256());
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    List<Map<String, Object>> history(String projectId, String targetId, int limit) throws Exception {
        if (!configured()) return List.of();
        requireIdentity(projectId, "PROJECT_ID");
        requireIdentity(targetId, "TARGET_ID");
        String sql = "SELECT run_id FROM validation_run_score WHERE project_id=? AND target_id=? "
                + "ORDER BY observed_at DESC, run_id DESC LIMIT ?";
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setString(2, targetId);
            statement.setInt(3, Math.max(1, Math.min(limit, 100)));
            try (ResultSet rows = statement.executeQuery()) {
                List<String> runIds = new ArrayList<>();
                while (rows.next()) runIds.add(rows.getString(1));
                List<Map<String, Object>> result = new ArrayList<>();
                for (String runId : runIds) result.add(detail(connection, projectId, targetId, runId));
                return List.copyOf(result);
            }
        }
    }

    Map<String, Object> detail(String projectId, String targetId, String runId) throws Exception {
        if (!configured()) return Map.of("state", "NOT_CONFIGURED");
        try (Connection connection = connection()) {
            return detail(connection, projectId, targetId, runId);
        }
    }

    Map<String, Object> authorizeImprovementComparison(
            String projectId, String targetId, ChangeLineage lineage) throws Exception {
        if (!configured()) return Map.of("contract", CONTRACT, "state", "NOT_CONFIGURED", "durable", false);
        requireIdentity(projectId, "PROJECT_ID");
        requireIdentity(targetId, "TARGET_ID");
        Objects.requireNonNull(lineage, "lineage");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            try {
                Map<String, Object> baseline = detail(
                        connection, projectId, targetId, lineage.baselineRunId());
                Map<String, Object> current = detail(
                        connection, projectId, targetId, lineage.currentRunId());
                if (baseline.isEmpty() || current.isEmpty()) {
                    throw new IllegalStateException("IMPROVEMENT_LINEAGE_RUN_NOT_FOUND_IN_PROJECT_TARGET");
                }
                ComparisonEligibility eligibility = comparisonEligibility(
                        String.valueOf(baseline.get("source_sha256")),
                        String.valueOf(current.get("source_sha256")),
                        String.valueOf(baseline.get("environment_sha256")),
                        String.valueOf(current.get("environment_sha256")),
                        String.valueOf(baseline.get("profile_sha256")),
                        String.valueOf(current.get("profile_sha256")),
                        String.valueOf(baseline.get("toolchain_sha256")),
                        String.valueOf(current.get("toolchain_sha256")),
                        lineage.baselineRunId(), lineage.currentRunId(), lineage);
                if (!eligibility.comparable() || !"IMPROVEMENT".equals(eligibility.type())) {
                    throw new IllegalStateException("IMPROVEMENT_LINEAGE_NOT_COMPARABLE:" + eligibility.reason());
                }
                JsonNode runRecord = mapper.valueToTree(current.get("run_record"));
                ((com.fasterxml.jackson.databind.node.ObjectNode) runRecord)
                        .set("improvement_lineage", mapper.valueToTree(lineage.asMap()));
                ((com.fasterxml.jackson.databind.node.ObjectNode) runRecord)
                        .put("comparison_intent", "IMPROVEMENT");
                ((com.fasterxml.jackson.databind.node.ObjectNode) runRecord)
                        .remove("run_record_sha256");
                ((com.fasterxml.jackson.databind.node.ObjectNode) runRecord)
                        .put("run_record_sha256", semanticDigest(runRecord));
                try (PreparedStatement update = connection.prepareStatement(
                        "UPDATE validation_run_score SET improvement_baseline_run_id=?, "
                                + "patch_apply_receipt_sha256=?, improvement_proof_sha256=?, "
                                + "run_record_json=CAST(? AS jsonb) "
                                + "WHERE project_id=? AND target_id=? AND run_id=?")) {
                    update.setString(1, lineage.baselineRunId());
                    update.setString(2, lineage.patchApplyReceiptSha256());
                    update.setString(3, lineage.improvementProofSha256());
                    update.setString(4, mapper.writeValueAsString(runRecord));
                    update.setString(5, projectId);
                    update.setString(6, targetId);
                    update.setString(7, lineage.currentRunId());
                    if (update.executeUpdate() != 1) throw new IllegalStateException("IMPROVEMENT_RUN_UPDATE_FAILED");
                }
                try (PreparedStatement delete = connection.prepareStatement(
                        "DELETE FROM validation_run_comparison WHERE project_id=? AND target_id=? AND current_run_id=?")) {
                    delete.setString(1, projectId);
                    delete.setString(2, targetId);
                    delete.setString(3, lineage.currentRunId());
                    delete.executeUpdate();
                }
                RunRecord comparisonRecord = new RunRecord(
                        projectId, targetId, lineage.currentRunId(), lineage.currentSourceSha256(),
                        null, String.valueOf(current.get("receipt_sha256")), "NOT_RUN",
                        String.valueOf(current.get("profile_sha256")),
                        String.valueOf(current.get("environment_sha256")),
                        String.valueOf(current.get("toolchain_sha256")), null, null, null, null,
                        String.valueOf(current.get("scorecard_sha256")),
                        String.valueOf(current.get("run_status")), Map.of(), "NOT_RUN", lineage);
                insertComparison(connection, projectId, targetId, lineage.baselineRunId(),
                        comparisonRecord, mapper.valueToTree(current.get("scorecard")));
                connection.commit();
                return detail(projectId, targetId, lineage.currentRunId());
            } catch (Exception failure) {
                connection.rollback();
                throw failure;
            }
        }
    }

    private Map<String, Object> detail(
            Connection connection, String projectId, String targetId, String runId) throws Exception {
        String sql = "SELECT source_sha256, receipt_sha256, validation_outcome, earned_points, max_points, "
                + "previous_run_id, observed_at, scorecard_json, run_record_json, scorecard_sha256, "
                + "environment_sha256, profile_sha256, toolchain_sha256, evidence_manifest_sha256, "
                + "report_sha256, run_status FROM validation_run_score "
                + "WHERE project_id=? AND target_id=? AND run_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setString(2, targetId);
            statement.setString(3, runId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return Map.of();
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("run_id", runId);
                value.put("project_id", projectId);
                value.put("target_id", targetId);
                value.put("source_sha256", row.getString(1));
                value.put("receipt_sha256", row.getString(2));
                value.put("validation_outcome", row.getString(3));
                value.put("earned_points", row.getBigDecimal(4));
                value.put("max_points", row.getBigDecimal(5));
                value.put("previous_run_id", nullableText(row.getString(6)));
                value.put("observed_at", row.getObject(7).toString());
                value.put("scorecard", json(row.getString(8)));
                value.put("run_record", json(row.getString(9)));
                value.put("scorecard_sha256", nullableText(row.getString(10)));
                value.put("environment_sha256", nullableText(row.getString(11)));
                value.put("profile_sha256", nullableText(row.getString(12)));
                value.put("toolchain_sha256", nullableText(row.getString(13)));
                value.put("evidence_manifest_sha256", nullableText(row.getString(14)));
                value.put("report_sha256", nullableText(row.getString(15)));
                value.put("run_status", nullableText(row.getString(16)));
                value.put("nodes", nodes(connection, runId));
                value.put("findings", findings(connection, runId));
                value.put("comparison", comparison(connection, projectId, targetId, runId));
                value.put("read_back_state", "VERIFIED_FROM_POSTGRESQL");
                value.put("final_claim_allowed", false);
                return Map.copyOf(value);
            }
        }
    }

    private List<Map<String, Object>> nodes(Connection connection, String runId) throws Exception {
        String sql = "SELECT node_type, node_id, parent_node_id, outcome, possible_points, earned_points, "
                + "diagnosis, improvement_guide, output_sha256, environment_sha256, node_json "
                + "FROM validation_score_node WHERE run_id=? ORDER BY node_type, node_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> values = new ArrayList<>();
                while (rows.next()) {
                    Map<String, Object> value = new LinkedHashMap<>();
                    value.put("node_type", rows.getString(1));
                    value.put("node_id", rows.getString(2));
                    value.put("parent_node_id", nullableText(rows.getString(3)));
                    value.put("outcome", rows.getString(4));
                    value.put("possible_points", rows.getBigDecimal(5));
                    value.put("earned_points", rows.getBigDecimal(6));
                    value.put("diagnosis", rows.getString(7));
                    value.put("improvement_guide", rows.getString(8));
                    value.put("output_sha256", nullableText(rows.getString(9)));
                    value.put("environment_sha256", nullableText(rows.getString(10)));
                    value.put("node", json(rows.getString(11)));
                    values.add(Map.copyOf(value));
                }
                return List.copyOf(values);
            }
        }
    }

    private List<Map<String, Object>> findings(Connection connection, String runId) throws Exception {
        String sql = "SELECT finding_id, severity, status, diagnosis, improvement_guide, finding_json "
                + "FROM validation_run_finding WHERE run_id=? ORDER BY finding_id";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, runId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Map<String, Object>> values = new ArrayList<>();
                while (rows.next()) values.add(Map.of(
                        "finding_id", rows.getString(1), "severity", rows.getString(2),
                        "status", rows.getString(3), "diagnosis", rows.getString(4),
                        "improvement_guide", rows.getString(5), "finding", json(rows.getString(6))));
                return List.copyOf(values);
            }
        }
    }

    private Map<String, Object> comparison(
            Connection connection, String projectId, String targetId, String currentRunId) throws Exception {
        String sql = "SELECT comparison_json FROM validation_run_comparison "
                + "WHERE project_id=? AND target_id=? AND current_run_id=? ORDER BY created_at DESC LIMIT 1";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setString(2, targetId);
            statement.setString(3, currentRunId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? mapper.convertValue(json(row.getString(1)), Map.class)
                        : Map.of("state", "NOT_RUN_NO_COMPARABLE_BASELINE");
            }
        }
    }

    private Connection connection() throws Exception {
        String url = environment.getOrDefault(
                "ONSURE_DB_URL", "jdbc:postgresql://127.0.0.1:5432/onsure");
        requireLoopback(url);
        String user = required("ONSURE_DB_USER");
        String password = required("ONSURE_DB_PASSWORD");
        String schema = environment.getOrDefault("ONSURE_DB_SCHEMA", "onsure");
        requireSchema(schema);
        Class.forName("org.postgresql.Driver");
        Connection connection = DriverManager.getConnection(url, user, password);
        connection.setSchema(schema);
        return connection;
    }

    private void insertRun(
            Connection connection, RunRecord record, Instant observedAt,
            JsonNode scorecard, String previousRunId) throws Exception {
        String sql = "INSERT INTO validation_run_score "
                + "(run_id, project_id, target_id, source_sha256, receipt_sha256, validation_outcome, "
                + "earned_points, max_points, scorecard_json, previous_run_id, observed_at, "
                + "source_commit_sha, profile_id, profile_sha256, environment_sha256, toolchain_sha256, "
                + "input_sha256, output_sha256, evidence_manifest_sha256, report_sha256, "
                + "scorecard_sha256, run_status, run_record_json, improvement_baseline_run_id, "
                + "patch_apply_receipt_sha256, improvement_proof_sha256) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, "
                + "CAST(? AS jsonb), ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, record.runId());
            statement.setString(2, record.projectId());
            statement.setString(3, record.targetId());
            statement.setString(4, record.sourceSha256());
            statement.setString(5, record.receiptSha256());
            statement.setString(6, record.runStatus());
            statement.setBigDecimal(7, scorecard.path("earned_points").decimalValue());
            statement.setBigDecimal(8, scorecard.path("max_points").decimalValue());
            statement.setString(9, mapper.writeValueAsString(scorecard));
            statement.setString(10, previousRunId);
            statement.setObject(11, observedAt);
            nullable(statement, 12, record.sourceCommitSha(), java.sql.Types.VARCHAR);
            statement.setString(13, record.profileId());
            statement.setString(14, record.profileSha256());
            statement.setString(15, record.environmentSha256());
            statement.setString(16, record.toolchainSha256());
            statement.setString(17, record.inputSha256());
            statement.setString(18, record.outputSha256());
            statement.setString(19, record.evidenceManifestSha256());
            statement.setString(20, record.reportSha256());
            statement.setString(21, record.scorecardSha256());
            statement.setString(22, record.runStatus());
            statement.setString(23, mapper.writeValueAsString(record.runRecord()));
            ChangeLineage lineage = record.changeLineage();
            nullable(statement, 24, lineage == null ? null : lineage.baselineRunId(), java.sql.Types.VARCHAR);
            nullable(statement, 25, lineage == null ? null : lineage.patchApplyReceiptSha256(), java.sql.Types.CHAR);
            nullable(statement, 26, lineage == null ? null : lineage.improvementProofSha256(), java.sql.Types.CHAR);
            statement.executeUpdate();
        }
    }

    private void insertNodes(Connection connection, String runId, JsonNode scorecard) throws Exception {
        String sql = "INSERT INTO validation_score_node "
                + "(run_id, node_type, node_id, parent_node_id, outcome, possible_points, earned_points, "
                + "diagnosis, improvement_guide, output_sha256, environment_sha256, node_json) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CAST(? AS jsonb))";
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
            nullable(statement, 4, parent(nodeType, node), java.sql.Types.VARCHAR);
            statement.setString(5, node.path("outcome").asText("NOT_RUN"));
            statement.setBigDecimal(6, node.path("possible_points").decimalValue());
            statement.setBigDecimal(7, node.path("earned_points").decimalValue());
            statement.setString(8, node.path("diagnosis").asText());
            statement.setString(9, node.path("improvement_guide").asText());
            nullable(statement, 10, node.path("output_sha256").asText(""), java.sql.Types.CHAR);
            nullable(statement, 11, node.path("environment_sha256").asText(""), java.sql.Types.CHAR);
            statement.setString(12, mapper.writeValueAsString(node));
            statement.addBatch();
        }
    }

    private void insertFindings(Connection connection, String runId, JsonNode findings) throws Exception {
        if (findings == null || !findings.isArray()) return;
        String sql = "INSERT INTO validation_run_finding "
                + "(run_id, finding_id, severity, status, diagnosis, improvement_guide, finding_json) "
                + "VALUES (?, ?, ?, ?, ?, ?, CAST(? AS jsonb))";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (JsonNode finding : findings) {
                statement.setString(1, runId);
                statement.setString(2, requiredText(finding, "finding_id", "FINDING_ID"));
                statement.setString(3, requiredText(finding, "severity", "FINDING_SEVERITY"));
                statement.setString(4, requiredText(finding, "status", "FINDING_STATUS"));
                statement.setString(5, requiredText(finding, "title", "FINDING_DIAGNOSIS"));
                statement.setString(6, requiredText(finding, "improvement", "FINDING_IMPROVEMENT"));
                statement.setString(7, mapper.writeValueAsString(finding));
                statement.addBatch();
            }
            statement.executeBatch();
        }
    }

    private void insertComparison(
            Connection connection, String projectId, String targetId, String baselineRunId,
            RunRecord current, JsonNode currentScorecard) throws Exception {
        StoredComparisonBaseline baseline = comparisonBaseline(connection, projectId, targetId, baselineRunId);
        ComparisonEligibility eligibility = comparisonEligibility(
                baseline.sourceSha256(), current.sourceSha256(),
                baseline.environmentSha256(), current.environmentSha256(),
                baseline.profileSha256(), current.profileSha256(),
                baseline.toolchainSha256(), current.toolchainSha256(),
                baselineRunId, current.runId(), current.changeLineage());
        Map<String, Object> comparison;
        java.math.BigDecimal totalDelta;
        if (eligibility.comparable()) {
            Map<String, Object> calculated = ValidationScorecardComparison.compare(
                    baselineRunId, baseline.scorecard(), current.runId(), currentScorecard);
            Map<String, Object> augmented = new LinkedHashMap<>(calculated);
            augmented.put("comparison_eligibility", "COMPARABLE");
            augmented.put("comparison_reason", eligibility.reason());
            augmented.put("comparison_type", eligibility.type());
            addComparisonDigests(augmented, baseline, current);
            comparison = Map.copyOf(augmented);
            totalDelta = (java.math.BigDecimal) comparison.get("total_delta_points");
        } else {
            Map<String, Object> blocked = new LinkedHashMap<>();
            blocked.put("contract", ValidationScorecardComparison.CONTRACT);
            blocked.put("baseline_run_id", baselineRunId);
            blocked.put("current_run_id", current.runId());
            blocked.put("state", "NOT_COMPARABLE");
            blocked.put("comparison_eligibility", "NOT_COMPARABLE");
            blocked.put("comparison_reason", eligibility.reason());
            blocked.put("comparison_type", eligibility.type());
            blocked.put("changes", List.of());
            blocked.put("final_claim_allowed", false);
            addComparisonDigests(blocked, baseline, current);
            comparison = Map.copyOf(blocked);
            totalDelta = java.math.BigDecimal.ZERO;
        }
        String sql = "INSERT INTO validation_run_comparison "
                + "(comparison_id, target_id, baseline_run_id, current_run_id, total_delta_points, "
                + "comparison_json, project_id, comparison_state, comparison_reason, "
                + "comparison_type, "
                + "baseline_source_sha256, current_source_sha256, baseline_environment_sha256, "
                + "current_environment_sha256, baseline_profile_sha256, current_profile_sha256, "
                + "baseline_toolchain_sha256, current_toolchain_sha256) "
                + "VALUES (?, ?, ?, ?, ?, CAST(? AS jsonb), ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, "comparison-" + Hashing.sha256(
                    projectId + "\u0000" + targetId + "\u0000" + baselineRunId + "\u0000" + current.runId())
                    .substring(0, 32));
            statement.setString(2, targetId);
            statement.setString(3, baselineRunId);
            statement.setString(4, current.runId());
            statement.setBigDecimal(5, totalDelta);
            statement.setString(6, mapper.writeValueAsString(comparison));
            statement.setString(7, projectId);
            statement.setString(8, eligibility.comparable() ? "COMPARABLE" : "NOT_COMPARABLE");
            statement.setString(9, eligibility.reason());
            statement.setString(10, eligibility.type());
            statement.setString(11, baseline.sourceSha256());
            statement.setString(12, current.sourceSha256());
            statement.setString(13, baseline.environmentSha256());
            statement.setString(14, current.environmentSha256());
            statement.setString(15, baseline.profileSha256());
            statement.setString(16, current.profileSha256());
            statement.setString(17, baseline.toolchainSha256());
            statement.setString(18, current.toolchainSha256());
            statement.executeUpdate();
        }
    }

    private static void addComparisonDigests(
            Map<String, Object> value, StoredComparisonBaseline baseline, RunRecord current) {
        value.put("baseline_source_sha256", baseline.sourceSha256());
        value.put("current_source_sha256", current.sourceSha256());
        value.put("baseline_environment_sha256", nullableText(baseline.environmentSha256()));
        value.put("current_environment_sha256", current.environmentSha256());
        value.put("baseline_profile_sha256", nullableText(baseline.profileSha256()));
        value.put("current_profile_sha256", current.profileSha256());
        value.put("baseline_toolchain_sha256", nullableText(baseline.toolchainSha256()));
        value.put("current_toolchain_sha256", current.toolchainSha256());
    }

    private StoredComparisonBaseline comparisonBaseline(
            Connection connection, String projectId, String targetId, String runId) throws Exception {
        String sql = "SELECT source_sha256, environment_sha256, profile_sha256, toolchain_sha256, scorecard_json "
                + "FROM validation_run_score WHERE project_id=? AND target_id=? AND run_id=?";
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, projectId);
            statement.setString(2, targetId);
            statement.setString(3, runId);
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) throw new IllegalStateException("COMPARISON_BASELINE_NOT_FOUND");
                return new StoredComparisonBaseline(
                        row.getString(1), row.getString(2), row.getString(3), row.getString(4),
                        mapper.readTree(row.getString(5)));
            }
        }
    }

    static ComparisonEligibility comparisonEligibility(
            String baselineSource, String currentSource,
            String baselineEnvironment, String currentEnvironment,
            String baselineProfile, String currentProfile,
            String baselineToolchain, String currentToolchain,
            String baselineRunId, String currentRunId, ChangeLineage lineage) {
        if (!Objects.equals(baselineEnvironment, currentEnvironment)) {
            return new ComparisonEligibility(false, "NOT_COMPARABLE", "ENVIRONMENT_DIGEST_CHANGED");
        }
        if (!Objects.equals(baselineProfile, currentProfile)) {
            return new ComparisonEligibility(false, "NOT_COMPARABLE", "PROFILE_DIGEST_CHANGED");
        }
        if (!Objects.equals(baselineToolchain, currentToolchain)) {
            return new ComparisonEligibility(false, "NOT_COMPARABLE", "TOOLCHAIN_DIGEST_CHANGED");
        }
        if (Objects.equals(baselineSource, currentSource)) {
            return new ComparisonEligibility(true, "REPEATABILITY",
                    "EXACT_SOURCE_ENVIRONMENT_PROFILE_TOOLCHAIN_MATCH");
        }
        if (lineage != null && lineage.approved()
                && baselineRunId.equals(lineage.baselineRunId())
                && currentRunId.equals(lineage.currentRunId())
                && baselineSource.equals(lineage.baselineSourceSha256())
                && currentSource.equals(lineage.currentSourceSha256())) {
            return new ComparisonEligibility(true, "IMPROVEMENT",
                    "APPROVED_PATCH_AND_IMPROVEMENT_PROOF_LINEAGE_VERIFIED");
        }
        return new ComparisonEligibility(false, "NOT_COMPARABLE",
                "NOT_COMPARABLE_MISSING_CHANGE_CAUSALITY");
    }

    record ComparisonEligibility(boolean comparable, String type, String reason) {}

    private String previousRunId(Connection connection, String projectId, String targetId) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT run_id FROM validation_run_score WHERE project_id=? AND target_id=? "
                        + "ORDER BY observed_at DESC, run_id DESC LIMIT 1")) {
            statement.setString(1, projectId);
            statement.setString(2, targetId);
            try (ResultSet row = statement.executeQuery()) {
                return row.next() ? row.getString(1) : null;
            }
        }
    }

    private RunRecord runRecord(
            String projectId, String targetId, String runId, String sourceSha256,
            String receiptSha256, JsonNode scorecard, Evidence evidence) throws Exception {
        if (evidence == null || evidence.receipt() == null || !evidence.receipt().isObject()
                || evidence.report() == null || !evidence.report().isObject()
                || evidence.findings() == null || !evidence.findings().isArray()) {
            throw new IllegalArgumentException("VALIDATION_RUN_EVIDENCE_INVALID");
        }
        JsonNode receipt = evidence.receipt();
        String runStatus = receipt.path("overall_outcome").asText();
        if (!OUTCOMES.contains(runStatus)
                || !runStatus.equals(scorecard.path("validation_outcome").asText())) {
            throw new IllegalArgumentException("VALIDATION_RUN_STATUS_MISMATCH");
        }
        String receiptSource = receipt.path("source_digest").asText();
        if (!sourceSha256.equals(receiptSource)) {
            throw new IllegalArgumentException("VALIDATION_RUN_SOURCE_MISMATCH");
        }
        String profileId = requiredText(receipt, "profile_id", "PROFILE_ID");
        String environmentSha256 = requiredDigest(
                receipt.path("environment_evidence").path("sha256").asText(), "ENVIRONMENT_SHA256");
        String outputSha256 = requiredDigest(
                receipt.path("final_evidence_integrity").path("output_sha256").asText(), "OUTPUT_SHA256");
        String evidenceManifestSha256 = requiredDigest(
                evidence.evidenceManifestSha256(), "EVIDENCE_MANIFEST_SHA256");
        String reportSha256 = requiredDigest(evidence.reportSha256(), "REPORT_SHA256");
        String scorecardSha256 = Hashing.sha256(mapper.writeValueAsBytes(scorecard));
        String profileSha256 = semanticDigest(Map.of(
                "profile_id", profileId,
                "environment_requirements_sha256", receipt.path(
                        "environment_requirements_sha256").asText("NOT_RUN"),
                "external_environment_profile", receipt.path("external_environment_profile"),
                "external_execution_profile", receipt.path("external_execution_profile")));
        String toolchainSha256 = semanticDigest(Map.of(
                "technologies", receipt.path("technologies"),
                "environment_evidence", receipt.path("environment_evidence")));
        String inputSha256 = semanticDigest(Map.of(
                "source_sha256", sourceSha256,
                "snapshot_sha256", requiredDigest(receipt.path("snapshot_digest").asText(), "SNAPSHOT_SHA256"),
                "profile_sha256", profileSha256,
                "environment_sha256", environmentSha256));
        String sourceCommitSha = optionalCommit(evidence.sourceCommitSha());
        ChangeLineage changeLineage = changeLineage(
                evidence.report(), runId, sourceSha256);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", RUN_RECORD_CONTRACT);
        body.put("project_id", projectId);
        body.put("target_id", targetId);
        body.put("run_id", runId);
        body.put("run_status", runStatus);
        body.put("source_sha256", sourceSha256);
        body.put("source_commit_sha", sourceCommitSha == null ? "NOT_RUN" : sourceCommitSha);
        body.put("snapshot_sha256", receipt.path("snapshot_digest").asText());
        body.put("profile_id", profileId);
        body.put("profile_sha256", profileSha256);
        body.put("environment_sha256", environmentSha256);
        body.put("toolchain_sha256", toolchainSha256);
        body.put("input_sha256", inputSha256);
        body.put("output_sha256", outputSha256);
        body.put("evidence_manifest_sha256", evidenceManifestSha256);
        body.put("report_sha256", reportSha256);
        body.put("receipt_sha256", receiptSha256);
        body.put("scorecard_sha256", scorecardSha256);
        body.put("finding_count", evidence.findings().size());
        body.put("comparison_intent", changeLineage == null ? "REPEATABILITY_OR_NOT_COMPARABLE"
                : "IMPROVEMENT");
        if (changeLineage != null) {
            body.put("improvement_lineage", changeLineage.asMap());
        }
        body.put("final_evidence_integrity", receipt.path("final_evidence_integrity").path("outcome").asText());
        body.put("final_claim_allowed", false);
        String runRecordSha256 = Hashing.sha256(mapper.writeValueAsBytes(body));
        body.put("run_record_sha256", runRecordSha256);
        return new RunRecord(projectId, targetId, runId, sourceSha256, sourceCommitSha,
                receiptSha256, profileId, profileSha256, environmentSha256, toolchainSha256,
                inputSha256, outputSha256, evidenceManifestSha256, reportSha256,
                scorecardSha256, runStatus, Map.copyOf(body), runRecordSha256, changeLineage);
    }

    private static ChangeLineage changeLineage(
            JsonNode report, String currentRunId, String currentSourceSha256) {
        JsonNode node = report.path("improvement_lineage");
        if (node.isMissingNode() || node.isNull()) return null;
        if (!node.isObject()
                || !"ONSURE_IMPROVEMENT_LINEAGE_V1".equals(node.path("contract").asText())
                || !"APPROVED".equals(node.path("approval_state").asText())) {
            throw new IllegalArgumentException("IMPROVEMENT_LINEAGE_NOT_APPROVED");
        }
        String baselineRunId = node.path("baseline_run_id").asText();
        String boundCurrentRunId = node.path("current_run_id").asText();
        String baselineSource = requiredDigest(
                node.path("baseline_source_sha256").asText(), "BASELINE_SOURCE_SHA256");
        String boundCurrentSource = requiredDigest(
                node.path("current_source_sha256").asText(), "CURRENT_SOURCE_SHA256");
        String patchReceipt = requiredDigest(
                node.path("patch_apply_receipt_sha256").asText(), "PATCH_APPLY_RECEIPT_SHA256");
        String improvementProof = requiredDigest(
                node.path("improvement_proof_sha256").asText(), "IMPROVEMENT_PROOF_SHA256");
        requireRunId(baselineRunId);
        requireRunId(boundCurrentRunId);
        if (!currentRunId.equals(boundCurrentRunId)
                || !currentSourceSha256.equals(boundCurrentSource)) {
            throw new IllegalArgumentException("IMPROVEMENT_LINEAGE_CURRENT_RUN_BINDING_MISMATCH");
        }
        return new ChangeLineage(true, baselineRunId, boundCurrentRunId, baselineSource,
                boundCurrentSource, patchReceipt, improvementProof);
    }

    private String semanticDigest(Object value) throws Exception {
        return Hashing.sha256(mapper.writeValueAsBytes(value));
    }

    private void verifyReadBack(
            Map<String, Object> stored, RunRecord expected, JsonNode scorecard) {
        if (stored.isEmpty()
                || !expected.sourceSha256().equals(stored.get("source_sha256"))
                || !expected.receiptSha256().equals(stored.get("receipt_sha256"))
                || !expected.runStatus().equals(stored.get("run_status"))
                || !expected.scorecardSha256().equals(stored.get("scorecard_sha256"))
                || !expected.environmentSha256().equals(stored.get("environment_sha256"))
                || !expected.profileSha256().equals(stored.get("profile_sha256"))
                || !expected.toolchainSha256().equals(stored.get("toolchain_sha256"))
                || !expected.evidenceManifestSha256().equals(stored.get("evidence_manifest_sha256"))
                || !expected.reportSha256().equals(stored.get("report_sha256"))
                || !mapper.valueToTree(stored.get("scorecard")).equals(scorecard)
                || !mapper.valueToTree(stored.get("run_record")).equals(mapper.valueToTree(expected.runRecord()))) {
            throw new IllegalStateException("POSTGRESQL_SCORE_READ_BACK_MISMATCH");
        }
    }

    private static String parent(String nodeType, JsonNode node) {
        return switch (nodeType) {
            case "AREA" -> node.path("domain").asText(null);
            case "STEP" -> node.path("group").asText(null);
            default -> null;
        };
    }

    private JsonNode json(String value) throws Exception {
        return value == null ? mapper.createObjectNode() : mapper.readTree(value);
    }

    private static String nullableText(String value) {
        return value == null || value.isBlank() ? "NOT_RUN" : value;
    }

    private static void nullable(
            PreparedStatement statement, int index, String value, int sqlType) throws Exception {
        if (value == null || value.isBlank()) statement.setNull(index, sqlType);
        else statement.setString(index, value);
    }

    private static String requiredText(JsonNode value, String field, String label) {
        String result = value.path(field).asText("");
        if (result.isBlank() || result.length() > 4000) {
            throw new IllegalArgumentException(label + "_INVALID");
        }
        return result;
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

    private static void requireIdentity(String value, String label) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException(label + "_INVALID");
        }
    }

    private static void requireRunId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException("RUN_ID_INVALID");
        }
    }

    private static String requiredDigest(String value, String label) {
        requireDigest(value, label);
        return value;
    }

    private static void requireDigest(String value, String label) {
        if (value == null || !value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(label + "_INVALID");
        }
    }

    private static String optionalCommit(String value) {
        if (value == null || value.isBlank() || "NOT_RUN".equals(value)) return null;
        if (!value.matches("[0-9a-f]{40,64}")) {
            throw new IllegalArgumentException("SOURCE_COMMIT_SHA_INVALID");
        }
        return value;
    }

    private static void requireSchema(String value) {
        if (value == null || !value.matches("[a-z_][a-z0-9_]{0,62}")) {
            throw new IllegalArgumentException("ONSURE_DB_SCHEMA_INVALID");
        }
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

    private record RunRecord(
            String projectId, String targetId, String runId, String sourceSha256,
            String sourceCommitSha, String receiptSha256, String profileId, String profileSha256,
            String environmentSha256, String toolchainSha256, String inputSha256, String outputSha256,
            String evidenceManifestSha256, String reportSha256, String scorecardSha256,
            String runStatus, Map<String, Object> runRecord, String runRecordSha256,
            ChangeLineage changeLineage) {}

    record ChangeLineage(
            boolean approved, String baselineRunId, String currentRunId,
            String baselineSourceSha256, String currentSourceSha256,
            String patchApplyReceiptSha256, String improvementProofSha256) {
        Map<String, Object> asMap() {
            return Map.of(
                    "contract", "ONSURE_IMPROVEMENT_LINEAGE_V1",
                    "approval_state", approved ? "APPROVED" : "NOT_APPROVED",
                    "baseline_run_id", baselineRunId,
                    "current_run_id", currentRunId,
                    "baseline_source_sha256", baselineSourceSha256,
                    "current_source_sha256", currentSourceSha256,
                    "patch_apply_receipt_sha256", patchApplyReceiptSha256,
                    "improvement_proof_sha256", improvementProofSha256);
        }
    }

    private record StoredComparisonBaseline(
            String sourceSha256, String environmentSha256, String profileSha256,
            String toolchainSha256, JsonNode scorecard) {}

    private static final class SetHolder {
        private static final Set<String> LOOPBACK = Set.of("127.0.0.1", "localhost", "::1", "[::1]");
    }
}
