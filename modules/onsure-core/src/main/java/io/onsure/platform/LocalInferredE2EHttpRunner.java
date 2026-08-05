package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Executes only approved read-only HTTP candidates against an explicit loopback endpoint. */
final class LocalInferredE2EHttpRunner {
    static final String CONTRACT = "ONSURE_INFERRED_E2E_RUNTIME_RECEIPT_V1";
    private static final int MAX_RESPONSE_BYTES = 1_048_576;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;
    private final Map<String, String> environment;
    private final HttpClient client;

    LocalInferredE2EHttpRunner(Path workspaceRoot, Map<String, String> environment, HttpClient client) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
        this.client = client;
    }

    Map<String, Object> run(JsonNode input, LocalAccessControl.Identity operator) throws Exception {
        requireExactFields(input, Set.of("execution_authorization_id", "execution_plan_sha256",
                "base_url_reference_id"));
        String authorizationId = text(input, "execution_authorization_id", 128);
        if (!authorizationId.matches("inferred-e2e-auth-[0-9a-f-]{36}"))
            throw new IllegalArgumentException("INFERRED_E2E_AUTHORIZATION_ID_INVALID");
        String planSha = text(input, "execution_plan_sha256", 64);
        if (!planSha.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("INFERRED_E2E_PLAN_DIGEST_INVALID");
        String reference = text(input, "base_url_reference_id", 256);
        if (!reference.matches("env:[A-Z][A-Z0-9_]{1,127}"))
            throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_REFERENCE_INVALID");
        URI base = loopbackBase(environment.get(reference.substring(4)));
        Path planFile = workspaceRoot.resolve(".onsure/inferred-e2e-authorizations")
                .resolve(authorizationId).resolve("execution-plan.json").normalize();
        if (!planFile.startsWith(workspaceRoot) || !Files.isRegularFile(planFile, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(planFile) || !Hashing.file(planFile).equals(planSha))
            throw new IllegalArgumentException("INFERRED_E2E_PLAN_NOT_FOUND_OR_STALE");
        Map<String, Object> plan = mapper.readValue(planFile.toFile(), new TypeReference<>() {});
        if (!"ONSURE_INFERRED_E2E_EXECUTION_AUTHORIZATION_V1".equals(plan.get("contract"))
                || !authorizationId.equals(plan.get("execution_authorization_id"))
                || !"NOT_RUN".equals(plan.get("execution_state")))
            throw new IllegalArgumentException("INFERRED_E2E_PLAN_BINDING_INVALID");
        String requestId = plan.get("approval_request_id").toString();
        LocalProgramUnderstandingApprovalService approvals =
                new LocalProgramUnderstandingApprovalService(workspaceRoot);
        Map<String, Object> claim = approvals.claimExecution(requestId, authorizationId, planSha, operator);
        String runId = claim.get("execution_run_id").toString();
        Instant started = Instant.now();
        List<Map<String, Object>> steps = new ArrayList<>();
        boolean failed = false;
        boolean blocked = false;
        int executed = 0;
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) plan
                .getOrDefault("authorized_candidates", List.of());
        for (Map<String, Object> candidate : candidates) {
            if (!"READY_FOR_ISOLATED_LOOPBACK_RUNNER".equals(candidate.get("state"))) {
                steps.add(blocked(candidate)); blocked = true; continue;
            }
            String method = candidate.get("http_method").toString();
            String route = candidate.get("http_path").toString();
            if (!List.of("GET", "HEAD", "OPTIONS").contains(method) || !safeRoute(route)) {
                steps.add(blocked(candidate)); blocked = true; continue;
            }
            executed++;
            try {
                URI uri = new URI(base.getScheme(), null, base.getHost(), base.getPort(), route, null, null);
                HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                        .header("Accept", "application/json").header("User-Agent", "ONSure-Inferred-E2E/1")
                        .method(method, HttpRequest.BodyPublishers.noBody()).build();
                long before = System.nanoTime();
                HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
                byte[] body;
                try (InputStream stream = response.body()) { body = stream.readNBytes(MAX_RESPONSE_BYTES + 1); }
                if (body.length > MAX_RESPONSE_BYTES) throw new IllegalStateException("INFERRED_E2E_RESPONSE_TOO_LARGE");
                @SuppressWarnings("unchecked") List<String> expected =
                        (List<String>) candidate.getOrDefault("response_statuses", List.of());
                boolean oracle = expected.isEmpty() ? response.statusCode() >= 200 && response.statusCode() < 300
                        : expected.contains(String.valueOf(response.statusCode()));
                Map<String, Object> step = new LinkedHashMap<>();
                step.put("plan_id", candidate.get("plan_id")); step.put("http_method", method);
                step.put("http_path", route); step.put("request_uri", uri.toString());
                step.put("request_body_sha256", Hashing.sha256(new byte[0]));
                step.put("response_status", response.statusCode()); step.put("expected_statuses", expected);
                step.put("response_body_sha256", Hashing.sha256(body)); step.put("response_bytes", body.length);
                step.put("duration_millis", Duration.ofNanos(System.nanoTime() - before).toMillis());
                step.put("oracle_outcome", oracle ? "PASS_NONFINAL" : "FAIL");
                step.put("response_body_stored", false); step.put("final_claim_allowed", false);
                steps.add(Map.copyOf(step));
                if (!oracle) failed = true;
            } catch (Exception error) {
                steps.add(Map.of("plan_id", candidate.get("plan_id"), "http_method", method,
                        "http_path", route, "oracle_outcome", "FAIL",
                        "error_code", error.getClass().getSimpleName(), "response_body_stored", false,
                        "final_claim_allowed", false));
                failed = true;
            }
        }
        String outcome = failed ? "FAIL" : blocked || executed == 0 ? "BLOCKED" : "PASS_NONFINAL";
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", CONTRACT); receipt.put("run_id", runId);
        receipt.put("execution_authorization_id", authorizationId); receipt.put("execution_plan_sha256", planSha);
        receipt.put("approval_request_sha256", plan.get("approval_request_sha256"));
        receipt.put("approval_receipt_sha256", plan.get("approval_receipt_sha256"));
        receipt.put("base_url_reference_id", reference); receipt.put("base_url_sha256", Hashing.sha256(base.toString()));
        receipt.put("started_at", started.toString()); receipt.put("completed_at", Instant.now().toString());
        receipt.put("step_count", steps.size()); receipt.put("executed_step_count", executed);
        receipt.put("steps", List.copyOf(steps)); receipt.put("outcome", outcome);
        receipt.put("customer_data_stored", false); receipt.put("response_bodies_stored", false);
        receipt.put("source_mutation_allowed", false); receipt.put("final_claim_allowed", false);
        Path receiptFile = planFile.resolveSibling("runtime-receipt.json");
        write(receiptFile, receipt);
        String receiptSha = Hashing.file(receiptFile);
        approvals.completeExecution(requestId, runId, outcome, receiptSha);
        receipt.put("runtime_receipt_sha256", receiptSha);
        receipt.put("runtime_receipt_file", workspaceRoot.relativize(receiptFile).toString().replace('\\', '/'));
        return Map.copyOf(receipt);
    }

    private static Map<String, Object> blocked(Map<String, Object> candidate) {
        return Map.of("plan_id", candidate.get("plan_id"), "http_method", candidate.get("http_method"),
                "http_path", candidate.get("http_path"), "oracle_outcome", "BLOCKED",
                "reason", candidate.get("state"), "response_body_stored", false, "final_claim_allowed", false);
    }
    private static boolean safeRoute(String route) {
        return route.startsWith("/") && !route.contains("{") && !route.contains("..")
                && !route.contains("?") && !route.contains("#") && !route.contains("\\");
    }
    private static URI loopbackBase(String raw) throws Exception {
        if (raw == null || raw.isBlank()) throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_NOT_CONFIGURED");
        URI uri = URI.create(raw);
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (!"http".equals(uri.getScheme()) || !Set.of("127.0.0.1", "localhost", "::1").contains(host)
                || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                || (uri.getPath() != null && !uri.getPath().isEmpty() && !"/".equals(uri.getPath())))
            throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_NOT_LOOPBACK");
        int port = uri.getPort();
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("INFERRED_E2E_BASE_URL_PORT_INVALID");
        return new URI("http", null, host, port, null, null, null);
    }
    private void write(Path file, Object value) throws Exception {
        Path root = workspaceRoot.resolve(".onsure/inferred-e2e-authorizations").normalize();
        if (!file.startsWith(root)) throw new IllegalStateException("INFERRED_E2E_RECEIPT_PATH_INVALID");
        Path temp = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        mapper.writeValue(temp.toFile(), value);
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        finally { Files.deleteIfExists(temp); }
    }
    private static void requireExactFields(JsonNode input, Set<String> fields) {
        if (input == null || !input.isObject()) throw new IllegalArgumentException("INFERRED_E2E_REQUEST_REQUIRED");
        Set<String> actual = new java.util.HashSet<>(); input.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields)) throw new IllegalArgumentException("INFERRED_E2E_REQUEST_FIELDS_INVALID");
    }
    private static String text(JsonNode input, String field, int maximum) {
        JsonNode value = input.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maximum)
            throw new IllegalArgumentException("INFERRED_E2E_REQUEST_TEXT_INVALID:" + field);
        return value.asText();
    }
}
