package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalProgramUnderstandingApprovalServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();
    private final LocalAccessControl.Identity operator = new LocalAccessControl.Identity(
            "operator-a", LocalAccessControl.Role.OPERATOR, "a".repeat(64));
    private final LocalAccessControl.Identity approver = new LocalAccessControl.Identity(
            "approver-b", LocalAccessControl.Role.APPROVER, "b".repeat(64));

    @Test
    void approvesExactReviewOnceWithoutExecutingIt() throws Exception {
        Prepared prepared = prepare("approved");
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        LocalProgramUnderstandingApprovalService service = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now, ZoneOffset.UTC));
        Map<String, Object> request = service.request(request(prepared, 600), operator);
        Map<String, Object> receipt = service.decide(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE",
                "reason", "isolated synthetic execution only")), approver);

        assertEquals("APPROVED_NOT_EXECUTED", receipt.get("state"));
        assertEquals("NOT_RUN", receipt.get("execution_state"));
        assertFalse((Boolean) receipt.get("execution_consumed"));
        assertEquals(true, receipt.get("single_use_for_execution"));
        IllegalArgumentException wrongReceipt = assertThrows(IllegalArgumentException.class, () ->
                service.consume(mapper.valueToTree(Map.of(
                        "request_id", request.get("request_id"), "receipt_sha256", "0".repeat(64),
                        "execution_scope", "ISOLATED_SYNTHETIC_LOOPBACK")), operator));
        assertEquals("PROGRAM_APPROVAL_RECEIPT_BINDING_INVALID", wrongReceipt.getMessage());
        Map<String, Object> authorization = service.consume(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "receipt_sha256", receipt.get("receipt_sha256"),
                "execution_scope", "ISOLATED_SYNTHETIC_LOOPBACK")), operator);
        assertEquals("CONSUMED_FOR_EXECUTION_AUTHORIZATION", authorization.get("state"));
        assertEquals(true, authorization.get("execution_consumed"));
        assertEquals("NOT_RUN", authorization.get("execution_state"));
        assertEquals("AUTHORIZED_NOT_RUN", authorization.get("execution_plan_state"));
        Path planFile = prepared.workspace().resolve(authorization.get("execution_plan_file").toString());
        assertEquals(authorization.get("execution_plan_sha256"), Hashing.file(planFile));
        @SuppressWarnings("unchecked") Map<String, Object> plan = mapper.readValue(planFile.toFile(), Map.class);
        assertEquals("ONSURE_INFERRED_E2E_EXECUTION_AUTHORIZATION_V1", plan.get("contract"));
        assertEquals("NOT_RUN", plan.get("execution_state"));
        assertEquals(false, plan.get("customer_data_allowed"));
        IllegalArgumentException replay = assertThrows(IllegalArgumentException.class, () ->
                service.consume(mapper.valueToTree(Map.of(
                        "request_id", request.get("request_id"), "receipt_sha256", receipt.get("receipt_sha256"),
                        "execution_scope", "ISOLATED_SYNTHETIC_LOOPBACK")), operator));
        assertEquals("PROGRAM_APPROVAL_ALREADY_CONSUMED_OR_NOT_APPROVED", replay.getMessage());
        assertThrows(IllegalArgumentException.class, () -> service.decide(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE", "reason", "replay")), approver));
    }

    @Test
    void rejectsExpiredAndStaleReviewApprovals() throws Exception {
        Prepared prepared = prepare("expired");
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        LocalProgramUnderstandingApprovalService issuer = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now, ZoneOffset.UTC));
        Map<String, Object> request = issuer.request(request(prepared, 60), operator);
        LocalProgramUnderstandingApprovalService expired = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now.plusSeconds(61), ZoneOffset.UTC));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () ->
                expired.decide(mapper.valueToTree(Map.of(
                        "request_id", request.get("request_id"), "decision", "APPROVE", "reason", "late")), approver));
        assertEquals("PROGRAM_APPROVAL_REQUEST_EXPIRED", error.getMessage());

        Prepared stale = prepare("stale");
        LocalProgramUnderstandingApprovalService staleService = new LocalProgramUnderstandingApprovalService(
                stale.workspace(), Clock.fixed(now, ZoneOffset.UTC));
        Map<String, Object> staleRequest = staleService.request(request(stale, 600), operator);
        Files.writeString(stale.workspace().resolve(".onsure/program-understanding/target/review.json"), "{}\n");
        IllegalArgumentException staleError = assertThrows(IllegalArgumentException.class, () ->
                staleService.decide(mapper.valueToTree(Map.of(
                        "request_id", staleRequest.get("request_id"), "decision", "APPROVE", "reason", "stale")), approver));
        assertEquals("PROGRAM_APPROVAL_REVIEW_STALE_OR_TAMPERED", staleError.getMessage());
    }

    private Prepared prepare(String suffix) throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace-" + suffix));
        Path source = Files.createDirectory(temp.resolve("source-" + suffix));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: Orders, version: '1'}
                paths: {/orders: {get: {operationId: listOrders, responses: {'200': {description: ok}}}}}
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
        return new Prepared(workspace, understood.get("profile_file_sha256").toString(),
                review.get("review_sha256").toString());
    }

    private com.fasterxml.jackson.databind.JsonNode request(Prepared prepared, int ttl) {
        return mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target",
                "profile_file_sha256", prepared.profileSha(), "review_sha256", prepared.reviewSha(),
                "reason", "prepare isolated synthetic execution", "ttl_seconds", ttl));
    }

    private record Prepared(Path workspace, String profileSha, String reviewSha) { }
}
