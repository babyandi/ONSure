package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

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

    private Prepared prepare() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace-" + java.util.UUID.randomUUID()));
        Path source = Files.createDirectory(temp.resolve("source-" + java.util.UUID.randomUUID()));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: Orders, version: '1'}
                paths:
                  /orders:
                    get:
                      operationId: listOrders
                      tags: [Orders]
                      responses: {'200': {description: ok}}
                """);
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
        return new Prepared(workspace, consumed.get("execution_authorization_id").toString(),
                consumed.get("execution_plan_sha256").toString());
    }

    private record Prepared(Path workspace, String authorizationId, String planSha) { }
}
