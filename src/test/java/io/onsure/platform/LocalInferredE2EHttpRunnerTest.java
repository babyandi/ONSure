package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalInferredE2EHttpRunnerTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalAccessControl.Identity operator = new LocalAccessControl.Identity(
            "operator-a", LocalAccessControl.Role.OPERATOR, "a".repeat(64));
    private final LocalAccessControl.Identity approver = new LocalAccessControl.Identity(
            "approver-b", LocalAccessControl.Role.APPROVER, "b".repeat(64));

    @Test
    void executesApprovedReadOnlyLoopbackCandidateAndStoresOnlyBodyDigest() throws Exception {
        Prepared prepared = prepare();
        HttpServer server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0), 4);
        server.createContext("/orders", exchange -> {
            byte[] body = "{\"orders\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort();
            LocalInferredE2EHttpRunner runner = new LocalInferredE2EHttpRunner(
                    prepared.workspace(), Map.of("ONSURE_SYNTHETIC_BASE_URL", base),
                    HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build());
            Map<String, Object> receipt = runner.run(mapper.valueToTree(Map.of(
                    "execution_authorization_id", prepared.authorizationId(),
                    "execution_plan_sha256", prepared.planSha(),
                    "base_url_reference_id", "env:ONSURE_SYNTHETIC_BASE_URL")), operator);

            assertEquals("PASS_NONFINAL", receipt.get("outcome"));
            assertEquals(1, ((Number) receipt.get("executed_step_count")).intValue());
            assertFalse((Boolean) receipt.get("response_bodies_stored"));
            @SuppressWarnings("unchecked") List<Map<String, Object>> steps =
                    (List<Map<String, Object>>) receipt.get("steps");
            assertEquals("PASS_NONFINAL", steps.get(0).get("oracle_outcome"));
            assertEquals(200, steps.get(0).get("response_status"));
            assertEquals(false, steps.get(0).get("response_body_stored"));
            assertThrows(IllegalArgumentException.class, () -> runner.run(mapper.valueToTree(Map.of(
                    "execution_authorization_id", prepared.authorizationId(),
                    "execution_plan_sha256", prepared.planSha(),
                    "base_url_reference_id", "env:ONSURE_SYNTHETIC_BASE_URL")), operator));
        } finally { server.stop(0); }
    }

    @Test
    void rejectsNonLoopbackEndpointBeforeClaimingAuthorization() throws Exception {
        Prepared prepared = prepare();
        LocalInferredE2EHttpRunner runner = new LocalInferredE2EHttpRunner(
                prepared.workspace(), Map.of("ONSURE_SYNTHETIC_BASE_URL", "http://example.com:8080"),
                HttpClient.newHttpClient());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                runner.run(mapper.valueToTree(Map.of(
                        "execution_authorization_id", prepared.authorizationId(),
                        "execution_plan_sha256", prepared.planSha(),
                        "base_url_reference_id", "env:ONSURE_SYNTHETIC_BASE_URL")), operator));
        assertEquals("INFERRED_E2E_BASE_URL_NOT_LOOPBACK", error.getMessage());
    }

    @Test
    void rejectsSourceDriftBeforeClaimingAuthorization() throws Exception {
        Prepared prepared = prepare();
        Files.writeString(prepared.source().resolve("drift.txt"), "changed after approval");
        LocalInferredE2EHttpRunner runner = new LocalInferredE2EHttpRunner(
                prepared.workspace(), Map.of("ONSURE_SYNTHETIC_BASE_URL", "http://127.0.0.1:18080"),
                HttpClient.newHttpClient());
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                runner.run(mapper.valueToTree(Map.of(
                        "execution_authorization_id", prepared.authorizationId(),
                        "execution_plan_sha256", prepared.planSha(),
                        "base_url_reference_id", "env:ONSURE_SYNTHETIC_BASE_URL")), operator));
        assertEquals("INFERRED_E2E_TARGET_SOURCE_STALE", error.getMessage());
        Files.delete(prepared.source().resolve("drift.txt"));
        Map<String, Object> retry = runner.run(mapper.valueToTree(Map.of(
                "execution_authorization_id", prepared.authorizationId(),
                "execution_plan_sha256", prepared.planSha(),
                "base_url_reference_id", "env:ONSURE_SYNTHETIC_BASE_URL")), operator);
        assertEquals("FAIL", retry.get("outcome"));
    }

    @Test
    void materializesSyntheticWriteBodyAndPathParameterAndValidatesResponseSchemas() throws Exception {
        Prepared prepared = prepare(openApiWithWriteAndPath());
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0), 4);
        server.createContext("/orders", exchange -> {
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] body = "{\"id\":\"00000000-0000-4000-8000-000000000001\",\"name\":\"ONSURE_SYNTHETIC\",\"quantity\":1}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
            exchange.sendResponseHeaders(201, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.createContext("/orders/", exchange -> {
            byte[] body = "{\"id\":\"00000000-0000-4000-8000-000000000001\",\"name\":\"ONSURE_SYNTHETIC\",\"quantity\":1}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Map<String, Object> receipt = run(prepared, server);
            assertEquals("PASS_NONFINAL", receipt.get("outcome"));
            assertEquals(2, ((Number) receipt.get("executed_step_count")).intValue());
            @SuppressWarnings("unchecked") List<Map<String, Object>> steps =
                    (List<Map<String, Object>>) receipt.get("steps");
            Map<String, Object> post = steps.stream().filter(step -> "POST".equals(step.get("http_method")))
                    .findFirst().orElseThrow();
            Map<String, Object> get = steps.stream().filter(step -> "GET".equals(step.get("http_method")))
                    .findFirst().orElseThrow();
            assertEquals("{\"name\":\"ONSURE_SYNTHETIC\",\"quantity\":1}", receivedBody.get());
            assertTrue(((Number) post.get("request_body_bytes")).intValue() > 0);
            assertEquals(false, post.get("request_body_stored"));
            assertEquals(true, post.get("response_schema_declared"));
            assertEquals(List.of(), post.get("response_schema_errors"));
            assertEquals("/orders/00000000-0000-4000-8000-000000000001", get.get("http_path"));
            assertEquals("PASS_NONFINAL", get.get("oracle_outcome"));
        } finally { server.stop(0); }
    }

    @Test
    void failsWhenActualJsonDoesNotMatchDeclaredResponseSchema() throws Exception {
        Prepared prepared = prepare(openApiWithWriteAndPath());
        HttpServer server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0), 4);
        server.createContext("/orders", exchange -> {
            byte[] body = "{}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders("POST".equals(exchange.getRequestMethod()) ? 201 : 200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Map<String, Object> receipt = run(prepared, server);
            assertEquals("FAIL", receipt.get("outcome"));
            @SuppressWarnings("unchecked") List<Map<String, Object>> steps =
                    (List<Map<String, Object>>) receipt.get("steps");
            assertTrue(steps.stream().anyMatch(step -> "FAIL".equals(step.get("oracle_outcome"))
                    && step.get("response_schema_errors").toString().contains("REQUIRED_MISSING")));
        } finally { server.stop(0); }
    }

    @Test
    void returnsExactDurableReceiptAfterRecoveringInterruptedRun() throws Exception {
        Prepared prepared = prepare();
        LocalProgramUnderstandingApprovalService approvals = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), java.time.Clock.fixed(
                        java.time.Instant.parse("2000-01-01T00:00:00Z"), java.time.ZoneOffset.UTC));
        Map<String, Object> claim = approvals.claimExecution(prepared.requestId(),
                prepared.authorizationId(), prepared.planSha(), operator);
        Path receiptFile = prepared.workspace().resolve(".onsure/inferred-e2e-authorizations")
                .resolve(prepared.authorizationId()).resolve("runtime-receipt.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(receiptFile.toFile(), Map.ofEntries(
                Map.entry("contract", LocalInferredE2EHttpRunner.CONTRACT),
                Map.entry("run_id", claim.get("execution_run_id")),
                Map.entry("execution_authorization_id", prepared.authorizationId()),
                Map.entry("execution_plan_sha256", prepared.planSha()),
                Map.entry("completed_at", "2000-01-01T00:00:01Z"),
                Map.entry("step_count", 0), Map.entry("steps", List.of()),
                Map.entry("outcome", "BLOCKED"), Map.entry("customer_data_stored", false),
                Map.entry("response_bodies_stored", false), Map.entry("final_claim_allowed", false)));
        LocalInferredE2EHttpRunner runner = new LocalInferredE2EHttpRunner(
                prepared.workspace(), Map.of("ONSURE_SYNTHETIC_BASE_URL", "http://127.0.0.1:18080"),
                HttpClient.newHttpClient());
        Map<String, Object> recovered = runner.run(mapper.valueToTree(Map.of(
                "execution_authorization_id", prepared.authorizationId(),
                "execution_plan_sha256", prepared.planSha(),
                "base_url_reference_id", "env:ONSURE_SYNTHETIC_BASE_URL")), operator);
        assertEquals("BLOCKED", recovered.get("outcome"));
        assertEquals(Hashing.file(receiptFile), recovered.get("runtime_receipt_sha256"));
    }

    @Test
    void comparesConsecutiveExactSourceRunsAndReportsRegression() throws Exception {
        Prepared baseline = prepare();
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), 0), 4);
        server.createContext("/orders", exchange -> {
            int status = calls.getAndIncrement() == 0 ? 200 : 500;
            byte[] body = "{\"orders\":[]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            Map<String, Object> first = run(baseline, server);
            assertEquals("NOT_RUN_NO_COMPARABLE_BASELINE", first.get("runtime_comparison_state"));
            Prepared current = authorizeAgain(baseline);
            Map<String, Object> second = run(current, server);
            assertEquals("FAIL", second.get("outcome"));
            assertEquals("COMPARISON_AVAILABLE_NONFINAL", second.get("runtime_comparison_state"));
            assertEquals("REGRESSED", second.get("runtime_comparison_change"));
            Path comparisonFile = current.workspace().resolve(second.get("runtime_comparison_file").toString());
            @SuppressWarnings("unchecked") Map<String, Object> comparison =
                    mapper.readValue(comparisonFile.toFile(), Map.class);
            assertEquals(1, ((Number) comparison.get("regressed_step_count")).intValue());
            assertEquals(first.get("run_id"), comparison.get("baseline_run_id"));
            assertEquals(second.get("run_id"), comparison.get("current_run_id"));
            assertFalse((Boolean) comparison.get("score_eligible"));
            @SuppressWarnings("unchecked") List<Map<String, Object>> history = (List<Map<String, Object>>)
                    new InferredE2ERunComparisonService(current.workspace()).history("target", 10).get("runs");
            assertEquals(2, history.size());
            @SuppressWarnings("unchecked") Map<String, Object> projected =
                    (Map<String, Object>) history.get(0).get("comparison");
            assertEquals("REGRESSED", projected.get("overall_change"));
            Files.writeString(comparisonFile, "{}\n");
            @SuppressWarnings("unchecked") List<Map<String, Object>> tamperedHistory = (List<Map<String, Object>>)
                    new InferredE2ERunComparisonService(current.workspace()).history("target", 10).get("runs");
            @SuppressWarnings("unchecked") Map<String, Object> tampered =
                    (Map<String, Object>) tamperedHistory.get(0).get("comparison");
            assertEquals("STALE_OR_TAMPERED", tampered.get("state"));
        } finally { server.stop(0); }
    }

    private Map<String, Object> run(Prepared prepared, HttpServer server) throws Exception {
        String base = "http://127.0.0.1:" + server.getAddress().getPort();
        return new LocalInferredE2EHttpRunner(
                prepared.workspace(), Map.of("ONSURE_SYNTHETIC_BASE_URL", base),
                HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build())
                .run(mapper.valueToTree(Map.of(
                        "execution_authorization_id", prepared.authorizationId(),
                        "execution_plan_sha256", prepared.planSha(),
                        "base_url_reference_id", "env:ONSURE_SYNTHETIC_BASE_URL")), operator);
    }

    private Prepared authorizeAgain(Prepared existing) throws Exception {
        Path profile = existing.workspace().resolve(".onsure/program-understanding/target/program-profile.json");
        @SuppressWarnings("unchecked") Map<String, Object> review = mapper.readValue(
                profile.resolveSibling("review.json").toFile(), Map.class);
        LocalProgramUnderstandingApprovalService approvals =
                new LocalProgramUnderstandingApprovalService(existing.workspace());
        Map<String, Object> request = approvals.request(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target",
                "profile_file_sha256", Hashing.file(profile), "review_sha256", review.get("review_sha256"),
                "reason", "comparison rerun", "ttl_seconds", 600)), operator);
        Map<String, Object> decision = approvals.decide(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE", "reason", "same source rerun")), approver);
        Map<String, Object> consumed = approvals.consume(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "receipt_sha256", decision.get("receipt_sha256"),
                "execution_scope", "ISOLATED_SYNTHETIC_LOOPBACK")), operator);
        return new Prepared(existing.workspace(), existing.source(), request.get("request_id").toString(),
                consumed.get("execution_authorization_id").toString(),
                consumed.get("execution_plan_sha256").toString());
    }

    private Prepared prepare() throws Exception {
        return prepare("""
                openapi: 3.1.0
                info: {title: Orders, version: '1'}
                paths:
                  /orders:
                    get:
                      operationId: listOrders
                      tags: [Orders]
                      responses: {'200': {description: ok}}
                """);
    }

    private Prepared prepare(String openApi) throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace-" + java.util.UUID.randomUUID()));
        Path source = Files.createDirectory(temp.resolve("source-" + java.util.UUID.randomUUID()));
        Files.writeString(source.resolve("openapi.yaml"), openApi);
        LocalProgramManagementService programs = new LocalProgramManagementService(workspace);
        programs.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local", "project_id", "project",
                "project_name", "Project", "target_id", "target", "target_name", "Target",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        Map<String, Object> understood = programs.understand(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target")));
        @SuppressWarnings("unchecked") Map<String, Object> understanding =
                (Map<String, Object>) understood.get("program_understanding");
        @SuppressWarnings("unchecked") List<Map<String, Object>> questions =
                (List<Map<String, Object>>) understanding.get("minimal_questions");
        List<Map<String, Object>> answers = questions.stream().map(question -> Map.<String, Object>of(
                "question_id", question.get("question_id"), "answer_state", "CONFIRMED",
                "evidence_reference_id", "fixture:" + question.get("question_id"))).toList();
        Map<String, Object> review = programs.reviewUnderstanding(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target",
                "profile_file_sha256", understood.get("profile_file_sha256"), "answers", answers)));
        LocalProgramUnderstandingApprovalService approvals =
                new LocalProgramUnderstandingApprovalService(workspace);
        Map<String, Object> request = approvals.request(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target",
                "profile_file_sha256", understood.get("profile_file_sha256"),
                "review_sha256", review.get("review_sha256"), "reason", "loopback test", "ttl_seconds", 600)), operator);
        Map<String, Object> decision = approvals.decide(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE", "reason", "safe read")), approver);
        Map<String, Object> consumed = approvals.consume(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "receipt_sha256", decision.get("receipt_sha256"),
                "execution_scope", "ISOLATED_SYNTHETIC_LOOPBACK")), operator);
        return new Prepared(workspace, source, request.get("request_id").toString(),
                consumed.get("execution_authorization_id").toString(),
                consumed.get("execution_plan_sha256").toString());
    }

    private static String openApiWithWriteAndPath() {
        return """
                openapi: 3.1.0
                info: {title: Orders, version: '1'}
                paths:
                  /orders:
                    post:
                      operationId: createOrder
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [name, quantity]
                              properties:
                                name: {type: string}
                                quantity: {type: integer, minimum: 1}
                                customerNote: {type: string, example: 'must-not-be-used'}
                      responses:
                        '201':
                          description: created
                          content:
                            application/json:
                              schema: {$ref: '#/components/schemas/Order'}
                  /orders/{orderId}:
                    parameters:
                      - name: orderId
                        in: path
                        required: true
                        schema: {type: string, format: uuid}
                    get:
                      operationId: getOrder
                      responses:
                        '200':
                          description: ok
                          content:
                            application/json:
                              schema: {$ref: '#/components/schemas/Order'}
                components:
                  schemas:
                    Order:
                      type: object
                      required: [id, name, quantity]
                      additionalProperties: false
                      properties:
                        id: {type: string, format: uuid}
                        name: {type: string}
                        quantity: {type: integer}
                """;
    }

    private record Prepared(Path workspace, Path source, String requestId,
            String authorizationId, String planSha) { }
}
