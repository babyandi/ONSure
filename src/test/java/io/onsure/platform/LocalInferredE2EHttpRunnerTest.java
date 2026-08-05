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
        return new Prepared(workspace, source, consumed.get("execution_authorization_id").toString(),
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

    private record Prepared(Path workspace, Path source, String authorizationId, String planSha) { }
}
