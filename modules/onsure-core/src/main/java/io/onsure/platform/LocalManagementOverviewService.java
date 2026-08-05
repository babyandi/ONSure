package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Read-only management projection over actual local catalog, validation and Gateway state. */
final class LocalManagementOverviewService {
    static final String CONTRACT = "ONSURE_MANAGEMENT_OVERVIEW_V1";
    private static final long MAX_JSON_BYTES = 10_485_760L;
    private static final int MAX_PROGRAMS = 200;
    private static final int MAX_RUNS_PER_PROGRAM = 100;
    private final Path workspaceRoot;
    private final Map<String, String> environment;
    private final HttpClient httpClient;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    LocalManagementOverviewService(Path workspaceRoot) {
        this(workspaceRoot, System.getenv(), HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(1)).build());
    }

    LocalManagementOverviewService(
            Path workspaceRoot, Map<String, String> environment, HttpClient httpClient) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(this.workspaceRoot)) {
            throw new IllegalArgumentException("WORKSPACE_ROOT_INVALID");
        }
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
        this.httpClient = java.util.Objects.requireNonNull(httpClient, "httpClient");
    }

    Map<String, Object> overview() throws Exception {
        List<Map<String, Object>> programs = programs();
        long validated = programs.stream()
                .filter(program -> "AVAILABLE".equals(program.get("validation_state"))).count();
        long improvements = programs.stream()
                .mapToLong(program -> ((Number) program.get("improvement_candidate_count")).longValue()).sum();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contract", CONTRACT);
        result.put("generated_at", Instant.now().toString());
        result.put("gateway", gateway());
        result.put("programs", programs);
        result.put("program_count", programs.size());
        result.put("validated_program_count", validated);
        result.put("improvement_candidate_count", improvements);
        result.put("improvement_proof", documentState(
                workspaceRoot.resolve(".onsure/improvement-evidence/improvement-proof.json")));
        result.put("assurance", Map.of(
                "self_validation", "NONFINAL",
                "independent_otester", "NOT_RUN",
                "independent_oaudit", "NOT_RUN",
                "production_go", false,
                "final_claim_allowed", false));
        return Map.copyOf(result);
    }

    private List<Map<String, Object>> programs() throws Exception {
        JsonNode targets = readJson(workspaceRoot.resolve(".onsure/product-catalog/targets.json"));
        if (targets == null || !targets.isArray()) return List.of();
        List<Map<String, Object>> result = new ArrayList<>();
        for (JsonNode registered : targets) {
            if (result.size() >= MAX_PROGRAMS) break;
            JsonNode target = registered.path("target");
            String targetId = safeId(target.path("targetId").asText());
            if (targetId == null) continue;
            String projectId = safeText(registered.path("projectId").asText(), "UNVERIFIED");
            Map<String, Object> validation = latestValidation(projectId, targetId);
            Map<String, Object> program = new LinkedHashMap<>();
            program.put("project_id", projectId);
            program.put("program_id", targetId);
            program.put("program_name", safeText(target.path("targetName").asText(), targetId));
            program.put("program_type", safeText(target.path("targetType").asText(), "UNVERIFIED"));
            program.put("source_reference", safeText(
                    target.path("immutableSourceReference").asText(), "UNVERIFIED"));
            program.put("validation_state", validation.get("state"));
            program.put("latest_validation", validation);
            program.put("program_understanding", programUnderstanding(targetId));
            program.put("improvement_candidate_count", validation.get("improvement_candidate_count"));
            program.put("final_claim_allowed", false);
            result.add(Map.copyOf(program));
        }
        result.sort(Comparator.comparing(value -> value.get("program_id").toString()));
        return List.copyOf(result);
    }

    private Map<String, Object> programUnderstanding(String targetId) throws Exception {
        Path profileFile = workspaceRoot.resolve(".onsure/program-understanding")
                .resolve(targetId).resolve("program-profile.json").normalize();
        JsonNode profile = readJson(profileFile);
        JsonNode understanding = profile == null ? null : profile.path("program_understanding");
        if (understanding == null || !understanding.isObject()
                || !ProgramUnderstandingEngine.CONTRACT.equals(understanding.path("contract").asText())) {
            return Map.of("state", "NOT_RUN", "final_claim_allowed", false);
        }
        Map<String, Object> result = mapper.convertValue(understanding, Map.class);
        result.put("state", "CANDIDATE_REVIEW_REQUIRED");
        result.put("profile_file_sha256", Hashing.file(profileFile));
        JsonNode review = readJson(profileFile.resolveSibling("review.json"));
        if (review != null && "ONSURE_PROGRAM_UNDERSTANDING_REVIEW_V1".equals(review.path("contract").asText())
                && result.get("profile_file_sha256").equals(review.path("profile_file_sha256").asText())) {
            result.put("review", mapper.convertValue(review, Map.class));
            result.put("state", review.path("review_state").asText("CANDIDATE_REVIEW_REQUIRED"));
        }
        try {
            result.put("inferred_e2e_history",
                    new InferredE2ERunComparisonService(workspaceRoot).history(targetId, 20));
        } catch (Exception unavailable) {
            result.put("inferred_e2e_history", Map.of(
                    "state", "INVALID_OR_UNAVAILABLE", "runs", List.of(),
                    "error", unavailable.getMessage() != null
                            && unavailable.getMessage().matches("[A-Z0-9_.:-]{1,200}")
                            ? unavailable.getMessage() : unavailable.getClass().getSimpleName(),
                    "final_claim_allowed", false));
        }
        return Map.copyOf(result);
    }

    private Map<String, Object> latestValidation(String projectId, String targetId) throws Exception {
        Path targetRoot = workspaceRoot.resolve(".onsure/validation-data").resolve(targetId).normalize();
        if (!safeDirectory(targetRoot)) return notRunValidation();
        List<Path> runs;
        try (var stream = Files.list(targetRoot)) {
            runs = stream.filter(this::safeDirectory)
                    .sorted(Comparator.comparing(this::modified).reversed()
                            .thenComparing(path -> path.getFileName().toString(), Comparator.reverseOrder()))
                    .limit(MAX_RUNS_PER_PROGRAM).toList();
        }
        JsonNode currentScorecard = null;
        String currentRunId = null;
        JsonNode currentReport = null;
        for (Path run : runs) {
            JsonNode report = readJson(run.resolve("validation-report.json"));
            if (report == null || !report.isObject()) continue;
            JsonNode scorecard = report.path("scorecard");
            if (currentRunId == null) {
                currentScorecard = scorecard;
                currentRunId = run.getFileName().toString();
                currentReport = report;
                if (!ValidationScorecard.CONTRACT.equals(scorecard.path("contract").asText())) {
                    return Map.copyOf(latestValidationResult(
                            projectId, targetId, run, currentRunId, report, scorecard));
                }
                if (!scoreEvidence(run, report, scorecard).valid()) {
                    return Map.copyOf(latestValidationResult(
                            projectId, targetId, run, currentRunId, report, scorecard));
                }
                continue;
            }
            if (ValidationScorecard.CONTRACT.equals(scorecard.path("contract").asText())
                    && scoreEvidence(run, report, scorecard).valid()) {
                Map<String, Object> result = latestValidationResult(
                        projectId, targetId, targetRoot.resolve(currentRunId), currentRunId,
                        currentReport, currentScorecard);
                Map<?, ?> persistence = result.get("database_persistence") instanceof Map<?, ?> value
                        ? value : Map.of();
                if (!"AUTHORITATIVE_VERIFIED".equals(persistence.get("state"))) {
                    result.put("comparison", ValidationScorecardComparison.compare(
                            run.getFileName().toString(), scorecard, currentRunId, currentScorecard));
                }
                return Map.copyOf(result);
            }
        }
        if (currentRunId != null) {
            return Map.copyOf(latestValidationResult(
                    projectId, targetId, targetRoot.resolve(currentRunId), currentRunId,
                    currentReport, currentScorecard));
        }
        return notRunValidation();
    }

    private Map<String, Object> latestValidationResult(
            String projectId, String targetId, Path run, String runId,
            JsonNode report, JsonNode scorecard) throws Exception {
            JsonNode plans = readJson(run.resolve("remediation-plans.json"));
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("state", "AVAILABLE");
            result.put("run_id", runId);
            result.put("report_id", safeText(report.path("reportId").asText(), "UNVERIFIED"));
            result.put("decision", safeText(report.path("decision").asText(), "UNVERIFIED"));
            result.put("generated_at", safeText(report.path("generatedAt").asText(), "UNVERIFIED"));
            result.put("source_sha256", digestOrNotRun(report.path("sourceDigestBefore").asText()));
            result.put("registration_source_sha256", digestOrNotRun(
                    report.path("sourceDigestBefore").asText()));
            result.put("snapshot_source_sha256", digestOrNotRun(
                    report.path("snapshotSourceDigest").asText()));
            result.put("receipt_sha256", digestOrNotRun(report.path("universalReceiptSha256").asText()));
            result.put("target_provenance_sha256", digestOrNotRun(
                    report.path("targetProvenanceSha256").asText()));
            result.put("target_classification", safeText(
                    report.path("targetClassification").asText(), "UNKNOWN"));
            result.put("real_target_universality_evidence_eligible",
                    report.path("realTargetUniversalityEvidenceEligible").asBoolean(false));
            result.put("finding_count", arraySize(report.path("findings")));
            result.put("evidence_count", arraySize(readJson(run.resolve("evidence.json"))));
            result.put("improvement_candidate_count", arraySize(plans));
            ScoreEvidence integrity = scoreEvidence(run, report, scorecard);
            result.put("evidence_integrity", Map.of(
                    "state", integrity.valid() ? "VERIFIED" : "INVALID",
                    "reason", integrity.reason(),
                    "receipt_sha256", integrity.receiptSha256(),
                    "final_claim_allowed", false));
            if (ValidationScorecard.CONTRACT.equals(scorecard.path("contract").asText())
                    && !integrity.valid()) {
                result.put("state", "INVALID_EVIDENCE");
                result.put("decision", "HOLD");
                result.put("scorecard", Map.of(
                        "state", "WITHHELD_EVIDENCE_INVALID",
                        "reason", integrity.reason(),
                        "final_claim_allowed", false));
                result.put("comparison", Map.of("state", "NOT_RUN_EVIDENCE_INVALID"));
                result.put("database_persistence", Map.of(
                        "state", "NOT_READ_EVIDENCE_INVALID", "history", List.of()));
                result.put("independent_otester", "NOT_RUN");
                result.put("independent_oaudit", "NOT_RUN");
                result.put("final_claim_allowed", false);
                return result;
            }
            result.put("scorecard", scorecard != null && scorecard.isObject()
                    ? mapper.convertValue(scorecard, Map.class) : Map.of("state", "NOT_RUN"));
            result.put("comparison", Map.of("state", "NOT_RUN_NO_COMPARABLE_BASELINE"));
            PostgresqlValidationScoreStore store = new PostgresqlValidationScoreStore(environment);
            if (store.configured()) {
                try {
                    List<Map<String, Object>> history = store.history(projectId, targetId, 20);
                    Map<String, Object> stored = history.stream()
                            .filter(item -> runId.equals(item.get("run_id"))).findFirst().orElse(Map.of());
                    String readBackFailure = databaseReadBackFailure(run, report, scorecard, stored);
                    if (readBackFailure != null) {
                        result.put("state", "INVALID_DATABASE_READ_BACK");
                        result.put("decision", "HOLD");
                        result.put("scorecard", Map.of(
                                "state", "WITHHELD_DATABASE_READ_BACK_INVALID",
                                "reason", readBackFailure, "final_claim_allowed", false));
                        result.put("comparison", Map.of(
                                "state", "NOT_RUN_DATABASE_READ_BACK_INVALID"));
                        result.put("database_persistence", Map.of(
                                "state", "INVALID", "reason", readBackFailure,
                                "history", List.of()));
                        result.put("independent_otester", "NOT_RUN");
                        result.put("independent_oaudit", "NOT_RUN");
                        result.put("final_claim_allowed", false);
                        return result;
                    }
                    result.put("scorecard", stored.get("scorecard"));
                    result.put("comparison", stored.get("comparison"));
                    result.put("database_persistence", Map.of(
                            "state", "AUTHORITATIVE_VERIFIED", "history", history,
                            "read_back_state", stored.get("read_back_state"),
                            "run_record", stored.get("run_record")));
                } catch (Exception unavailable) {
                    result.put("state", "DATABASE_READ_BACK_UNAVAILABLE");
                    result.put("decision", "HOLD");
                    result.put("scorecard", Map.of(
                            "state", "WITHHELD_DATABASE_UNAVAILABLE",
                            "reason", unavailable.getClass().getSimpleName(),
                            "final_claim_allowed", false));
                    result.put("comparison", Map.of(
                            "state", "NOT_RUN_DATABASE_UNAVAILABLE"));
                    result.put("database_persistence", Map.of(
                            "state", "UNAVAILABLE", "history", List.of(),
                            "error", unavailable.getClass().getSimpleName()));
                }
            } else {
                result.put("database_persistence", Map.of("state", "NOT_CONFIGURED", "history", List.of()));
            }
            result.put("independent_otester", "NOT_RUN");
            result.put("independent_oaudit", "NOT_RUN");
            result.put("final_claim_allowed", false);
            return result;
    }

    private String databaseReadBackFailure(
            Path run, JsonNode report, JsonNode scorecard,
            Map<String, Object> stored) throws Exception {
        if (stored.isEmpty()) return "POSTGRESQL_RUN_NOT_FOUND";
        if (!report.path("snapshotSourceDigest").asText().equals(stored.get("source_sha256"))) {
            return "POSTGRESQL_SOURCE_DIGEST_MISMATCH";
        }
        if (!report.path("universalReceiptSha256").asText().equals(stored.get("receipt_sha256"))) {
            return "POSTGRESQL_RECEIPT_DIGEST_MISMATCH";
        }
        if (!mapper.valueToTree(stored.get("scorecard")).equals(scorecard)) {
            return "POSTGRESQL_SCORECARD_MISMATCH";
        }
        if (!Hashing.file(run.resolve("validation-report.json")).equals(stored.get("report_sha256"))) {
            return "POSTGRESQL_REPORT_DIGEST_MISMATCH";
        }
        if (!Hashing.file(run.resolve("evidence.json")).equals(
                stored.get("evidence_manifest_sha256"))) {
            return "POSTGRESQL_EVIDENCE_MANIFEST_DIGEST_MISMATCH";
        }
        return null;
    }

    private ScoreEvidence scoreEvidence(Path run, JsonNode report, JsonNode scorecard) throws Exception {
        if (scorecard == null || !ValidationScorecard.CONTRACT.equals(
                scorecard.path("contract").asText())) {
            return new ScoreEvidence(true, "SCORECARD_NOT_APPLICABLE", "NOT_RUN");
        }
        Path receiptFile = run.resolve(UniversalValidationRunner.RECEIPT_FILE).normalize();
        if (!safeFile(receiptFile)) {
            return new ScoreEvidence(false, "RECEIPT_MISSING_OR_UNSAFE", "NOT_RUN");
        }
        String actualReceiptSha256;
        try {
            actualReceiptSha256 = Hashing.file(receiptFile);
        } catch (Exception unreadable) {
            return new ScoreEvidence(false, "RECEIPT_UNREADABLE", "NOT_RUN");
        }
        String expectedReceiptSha256 = report.path("universalReceiptSha256").asText("");
        if (!expectedReceiptSha256.matches("[0-9a-f]{64}")
                || !actualReceiptSha256.equals(expectedReceiptSha256)) {
            return new ScoreEvidence(false, "RECEIPT_SHA256_MISMATCH", actualReceiptSha256);
        }
        JsonNode receipt;
        try {
            receipt = readJson(receiptFile);
        } catch (Exception malformed) {
            return new ScoreEvidence(false, "RECEIPT_JSON_INVALID", actualReceiptSha256);
        }
        if (receipt == null || !UniversalValidationRunner.CONTRACT.equals(
                receipt.path("contract").asText())) {
            return new ScoreEvidence(false, "RECEIPT_CONTRACT_INVALID", actualReceiptSha256);
        }
        if (!"PASS_NONFINAL".equals(receipt.path("final_evidence_integrity").path("outcome").asText())) {
            return new ScoreEvidence(false, "FINAL_EVIDENCE_INTEGRITY_NOT_PASS", actualReceiptSha256);
        }
        TargetProvenanceRunVerifier.Verification provenance =
                new TargetProvenanceRunVerifier().verify(run);
        if (!provenance.valid()) {
            return new ScoreEvidence(false, "TARGET_PROVENANCE_EVIDENCE_INVALID:"
                    + String.join(",", provenance.reasons()), actualReceiptSha256);
        }
        if (!receipt.path("scorecard").equals(scorecard)) {
            return new ScoreEvidence(false, "REPORT_RECEIPT_SCORECARD_MISMATCH", actualReceiptSha256);
        }
        String reportSource = report.path("snapshotSourceDigest").asText("");
        if (!reportSource.matches("[0-9a-f]{64}")
                || !reportSource.equals(receipt.path("source_digest").asText())) {
            return new ScoreEvidence(false, "REPORT_RECEIPT_SOURCE_MISMATCH", actualReceiptSha256);
        }
        return new ScoreEvidence(true, "RECEIPT_AND_SCORECARD_VERIFIED", actualReceiptSha256);
    }

    private Map<String, Object> gateway() {
        int port = port(environment.getOrDefault("ONSURE_LLM_GATEWAY_PORT", "47312"));
        String provider = safeText(environment.get("ONSURE_LLM_PROVIDER"), "local-mock");
        String model = safeText("openai".equals(provider)
                        ? environment.get("ONSURE_OPENAI_MODEL") : environment.get("ONSURE_LLM_MODEL"),
                "openai".equals(provider) ? "UNVERIFIED" : "onsure-local-mock-v1");
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("settings", Map.of(
                "provider", provider,
                "model", model,
                "port", port,
                "binding", "127.0.0.1",
                "network_egress_default_approved", false,
                "customer_data_default_approved", false,
                "fallback_enabled", false,
                "prompt_or_completion_storage_enabled", false,
                "credential_configured", credentialConfigured(provider),
                "gateway_token_configured", configured("ONSURE_LLM_GATEWAY_TOKEN")));
        String token = environment.get("ONSURE_LLM_GATEWAY_TOKEN");
        if (token == null || token.isBlank()) {
            result.put("state", "NOT_CONFIGURED");
            result.put("health", Map.of("state", "NOT_RUN"));
            result.put("metrics", emptyMetrics("NOT_RUN"));
            return Map.copyOf(result);
        }
        try {
            JsonNode health = request(port, "/v1/health", null);
            JsonNode metrics = request(port, "/v1/metrics", token);
            result.put("state", safeText(health.path("state").asText(), "UNVERIFIED"));
            result.put("health", Map.of(
                    "provider", safeText(health.path("provider").asText(), provider),
                    "provider_health", safeText(health.path("provider_health").asText(), "UNVERIFIED"),
                    "binding", safeText(health.path("binding").asText(), "UNVERIFIED")));
            result.put("metrics", metrics(metrics));
        } catch (Exception unavailable) {
            result.put("state", "UNAVAILABLE");
            result.put("health", Map.of("state", "UNAVAILABLE", "error", unavailable.getClass().getSimpleName()));
            result.put("metrics", emptyMetrics("NOT_RUN"));
        }
        return Map.copyOf(result);
    }

    private JsonNode request(int port, String path, String token) throws Exception {
        HttpRequest.Builder builder = HttpRequest.newBuilder(
                        URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(2)).GET();
        if (token != null) builder.header("Authorization", "Bearer " + token);
        HttpResponse<String> response = httpClient.send(
                builder.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || response.body().length() > MAX_JSON_BYTES) {
            throw new IllegalStateException("GATEWAY_PROJECTION_UNAVAILABLE");
        }
        return mapper.readTree(response.body());
    }

    private Map<String, Object> metrics(JsonNode value) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (String field : List.of(
                "request_count", "success_count", "failure_count", "input_tokens", "output_tokens",
                "total_tokens", "estimated_cost_micros", "actual_cost_micros", "total_duration_millis",
                "retryable_failure_count", "average_duration_millis", "ledger_bytes", "last_sequence")) {
            result.put(field, Math.max(0L, value.path(field).asLong(0)));
        }
        result.put("chain_valid", value.path("chain_valid").asBoolean(false));
        result.put("chain_head_sha256", digestOrNotRun(value.path("chain_head_sha256").asText()));
        result.put("last_observed_at", safeText(value.path("last_observed_at").asText(), "NOT_RUN"));
        result.put("prompt_or_completion_content_recorded", false);
        result.put("final_claim_allowed", false);
        return Map.copyOf(result);
    }

    private static Map<String, Object> emptyMetrics(String state) {
        return Map.ofEntries(
                Map.entry("state", state), Map.entry("request_count", 0L),
                Map.entry("success_count", 0L), Map.entry("failure_count", 0L),
                Map.entry("input_tokens", 0L), Map.entry("output_tokens", 0L),
                Map.entry("total_tokens", 0L), Map.entry("estimated_cost_micros", 0L),
                Map.entry("actual_cost_micros", 0L), Map.entry("total_duration_millis", 0L),
                Map.entry("retryable_failure_count", 0L), Map.entry("average_duration_millis", 0L),
                Map.entry("ledger_bytes", 0L), Map.entry("last_sequence", 0L),
                Map.entry("chain_valid", false), Map.entry("chain_head_sha256", "NOT_RUN"),
                Map.entry("last_observed_at", "NOT_RUN"),
                Map.entry("prompt_or_completion_content_recorded", false),
                Map.entry("final_claim_allowed", false));
    }

    private Map<String, Object> documentState(Path file) throws Exception {
        JsonNode value = readJson(file);
        if (value == null) return Map.of("state", "NOT_RUN", "final_claim_allowed", false);
        return Map.of(
                "state", "AVAILABLE",
                "decision", safeText(value.path("decision").asText(), "UNVERIFIED"),
                "contract", safeText(value.path("contract").asText(), "UNVERIFIED"),
                "final_claim_allowed", false);
    }

    private JsonNode readJson(Path file) throws Exception {
        if (!safeFile(file) || Files.size(file) > MAX_JSON_BYTES) return null;
        return mapper.readTree(file.toFile());
    }

    private boolean safeDirectory(Path path) {
        return path.startsWith(workspaceRoot) && noSymlinkComponents(path)
                && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    private boolean safeFile(Path path) {
        return path.startsWith(workspaceRoot) && noSymlinkComponents(path)
                && Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }

    private boolean noSymlinkComponents(Path path) {
        Path normalized = path.toAbsolutePath().normalize();
        if (!normalized.startsWith(workspaceRoot)) return false;
        Path current = workspaceRoot;
        for (Path component : workspaceRoot.relativize(normalized)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) return false;
        }
        return true;
    }

    private long modified(Path path) {
        try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }

    private boolean credentialConfigured(String provider) {
        return "local-mock".equals(provider) || configured("OPENAI_API_KEY");
    }

    private boolean configured(String key) {
        String value = environment.get(key);
        return value != null && !value.isBlank();
    }

    private static Map<String, Object> notRunValidation() {
        return Map.of(
                "state", "NOT_RUN", "decision", "NOT_RUN", "finding_count", 0,
                "evidence_count", 0, "improvement_candidate_count", 0,
                "independent_otester", "NOT_RUN", "independent_oaudit", "NOT_RUN",
                "final_claim_allowed", false);
    }

    private static int arraySize(JsonNode value) {
        return value != null && value.isArray() ? value.size() : 0;
    }

    private static String safeId(String value) {
        return value != null && value.matches("[A-Za-z0-9._-]{1,128}") ? value : null;
    }

    private static String safeText(String value, String fallback) {
        if (value == null || value.isBlank()) return fallback;
        String cleaned = value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "");
        return cleaned.length() <= 512 ? cleaned : cleaned.substring(0, 512);
    }

    private static String digestOrNotRun(String value) {
        return value != null && value.matches("[0-9a-f]{64}") ? value : "NOT_RUN";
    }

    private static int port(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1024 || value > 65535) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException invalid) {
            return 47312;
        }
    }

    private record ScoreEvidence(boolean valid, String reason, String receiptSha256) {}
}
