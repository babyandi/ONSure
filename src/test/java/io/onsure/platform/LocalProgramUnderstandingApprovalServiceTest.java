package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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

    @Test
    void blocksSecuredCandidateWhenReviewDoesNotBindAnEnvironmentCredential() throws Exception {
        Prepared prepared = prepare("secured-without-runtime-reference", """
                openapi: 3.1.0
                info: {title: Secured Orders, version: '1'}
                security: [{bearerAuth: []}]
                paths:
                  /orders:
                    get:
                      operationId: listOrders
                      responses: {'200': {description: ok}}
                components:
                  securitySchemes: {bearerAuth: {type: http, scheme: bearer}}
                """);
        Authorized authorized = authorize(prepared, Instant.parse("2026-08-05T00:00:00Z"));
        Path planFile = prepared.workspace().resolve(authorized.planFile());
        @SuppressWarnings("unchecked") Map<String, Object> plan = mapper.readValue(planFile.toFile(), Map.class);

        assertEquals("PARTIAL_AUTHORIZATION_BLOCKED_NOT_RUN", plan.get("plan_state"));
        assertEquals(Map.of(), plan.get("runtime_reference_ids"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) plan.get("authorized_candidates");
        assertEquals("BLOCKED_AUTHENTICATION_REFERENCE_MISSING", candidates.get(0).get("state"));
        assertEquals(true, candidates.get(0).get("security_declared"));
    }

    @Test
    void recoversInterruptedReadOnlyRunForOneSafeRetry() throws Exception {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        Prepared prepared = prepare("recover-read");
        Authorized authorized = authorize(prepared, now);
        Map<String, Object> firstClaim = authorized.service().claimExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator);

        LocalProgramUnderstandingApprovalService recovery = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now.plusSeconds(121), ZoneOffset.UTC));
        Map<String, Object> recovered = recovery.recoverInterruptedExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator,
                Duration.ofMinutes(2));
        assertEquals("RECOVERED_RETRY_ALLOWED", recovered.get("recovery_state"));
        Map<String, Object> secondClaim = recovery.claimExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator);
        assertFalse(firstClaim.get("execution_run_id").equals(secondClaim.get("execution_run_id")));
        assertEquals(1, secondClaim.get("recovery_count"));
        assertEquals("READ_ONLY_RETRY_ALLOWED", secondClaim.get("last_recovery_action"));
        LocalProgramUnderstandingApprovalService secondRecovery = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now.plusSeconds(242), ZoneOffset.UTC));
        assertEquals("RECOVERED_RETRY_ALLOWED", secondRecovery.recoverInterruptedExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator,
                Duration.ofMinutes(2)).get("recovery_state"));
        Map<String, Object> thirdClaim = secondRecovery.claimExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator);
        assertEquals(2, thirdClaim.get("recovery_count"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> history =
                (List<Map<String, Object>>) thirdClaim.get("recovery_history");
        assertEquals(history.get(0).get("entry_sha256"), history.get(1).get("previous_entry_sha256"));
    }

    @Test
    void requiresNewApprovalWhenInterruptedWriteOutcomeIsUnknown() throws Exception {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        Prepared prepared = prepare("recover-write", """
                openapi: 3.1.0
                info: {title: Orders, version: '1'}
                paths:
                  /orders:
                    post:
                      operationId: createOrder
                      requestBody:
                        content: {application/json: {schema: {type: object}}}
                      responses: {'201': {description: created}}
                """);
        Authorized authorized = authorize(prepared, now);
        authorized.service().claimExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator);
        LocalProgramUnderstandingApprovalService recovery = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now.plusSeconds(121), ZoneOffset.UTC));
        Map<String, Object> recovered = recovery.recoverInterruptedExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator,
                Duration.ofMinutes(2));
        assertEquals("RECOVERY_REAPPROVAL_REQUIRED", recovered.get("recovery_state"));
        IllegalArgumentException replay = assertThrows(IllegalArgumentException.class, () ->
                recovery.claimExecution(authorized.requestId(), authorized.authorizationId(),
                        authorized.planSha(), operator));
        assertEquals("PROGRAM_EXECUTION_AUTHORIZATION_ALREADY_CLAIMED", replay.getMessage());
    }

    @Test
    void completesInterruptedRunFromExactDurableReceipt() throws Exception {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        Prepared prepared = prepare("recover-receipt");
        Authorized authorized = authorize(prepared, now);
        Map<String, Object> claim = authorized.service().claimExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator);
        Path plan = prepared.workspace().resolve(authorized.planFile());
        Path receipt = plan.resolveSibling("runtime-receipt.json");
        mapper.writerWithDefaultPrettyPrinter().writeValue(receipt.toFile(), Map.ofEntries(
                Map.entry("contract", LocalInferredE2EHttpRunner.CONTRACT),
                Map.entry("run_id", claim.get("execution_run_id")),
                Map.entry("execution_authorization_id", authorized.authorizationId()),
                Map.entry("execution_plan_sha256", authorized.planSha()),
                Map.entry("completed_at", now.plusSeconds(30).toString()),
                Map.entry("step_count", 0), Map.entry("steps", List.of()),
                Map.entry("outcome", "BLOCKED"), Map.entry("customer_data_stored", false),
                Map.entry("response_bodies_stored", false), Map.entry("final_claim_allowed", false)));
        LocalProgramUnderstandingApprovalService recovery = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now.plusSeconds(121), ZoneOffset.UTC));
        Map<String, Object> recovered = recovery.recoverInterruptedExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator,
                Duration.ofMinutes(2));
        assertEquals("RECOVERED_COMPLETED", recovered.get("recovery_state"));
        @SuppressWarnings("unchecked") List<Map<String, Object>> requests =
                (List<Map<String, Object>>) recovery.list(10).get("requests");
        Map<String, Object> stored = requests.stream()
                .filter(value -> authorized.requestId().equals(value.get("request_id"))).findFirst().orElseThrow();
        assertEquals("EXECUTION_COMPLETED", stored.get("state"));
        assertEquals("BLOCKED", stored.get("execution_state"));
        assertEquals(Hashing.file(receipt), stored.get("runtime_receipt_sha256"));
        assertEquals("COMPLETED_FROM_DURABLE_RECEIPT", stored.get("last_recovery_action"));
    }

    @Test
    void rejectsTamperedExecutionStartBeforeRecovery() throws Exception {
        Instant now = Instant.parse("2026-08-05T00:00:00Z");
        Prepared prepared = prepare("recover-tamper");
        Authorized authorized = authorize(prepared, now);
        authorized.service().claimExecution(
                authorized.requestId(), authorized.authorizationId(), authorized.planSha(), operator);
        Path requestFile = prepared.workspace().resolve(
                ".onsure/management/program-understanding-approvals/" + authorized.requestId() + ".json");
        @SuppressWarnings("unchecked") Map<String, Object> stored = mapper.readValue(requestFile.toFile(), Map.class);
        stored.put("execution_started_at", "2000-01-01T00:00:00Z");
        mapper.writerWithDefaultPrettyPrinter().writeValue(requestFile.toFile(), stored);
        IllegalStateException tampered = assertThrows(IllegalStateException.class,
                () -> authorized.service().list(10));
        assertEquals("PROGRAM_EXECUTION_CLAIM_DIGEST_INVALID", tampered.getMessage());
    }

    private Prepared prepare(String suffix) throws Exception {
        return prepare(suffix, """
                openapi: 3.1.0
                info: {title: Orders, version: '1'}
                paths: {/orders: {get: {operationId: listOrders, responses: {'200': {description: ok}}}}}
                """);
    }

    private Prepared prepare(String suffix, String openApi) throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace-" + suffix));
        Path source = Files.createDirectory(temp.resolve("source-" + suffix));
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
        return new Prepared(workspace, understood.get("profile_file_sha256").toString(),
                review.get("review_sha256").toString());
    }

    private com.fasterxml.jackson.databind.JsonNode request(Prepared prepared, int ttl) {
        return mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target",
                "profile_file_sha256", prepared.profileSha(), "review_sha256", prepared.reviewSha(),
                "reason", "prepare isolated synthetic execution", "ttl_seconds", ttl));
    }

    private Authorized authorize(Prepared prepared, Instant now) throws Exception {
        LocalProgramUnderstandingApprovalService service = new LocalProgramUnderstandingApprovalService(
                prepared.workspace(), Clock.fixed(now, ZoneOffset.UTC));
        Map<String, Object> request = service.request(request(prepared, 600), operator);
        Map<String, Object> decision = service.decide(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "decision", "APPROVE", "reason", "recovery test")), approver);
        Map<String, Object> consumed = service.consume(mapper.valueToTree(Map.of(
                "request_id", request.get("request_id"), "receipt_sha256", decision.get("receipt_sha256"),
                "execution_scope", "ISOLATED_SYNTHETIC_LOOPBACK")), operator);
        return new Authorized(service, request.get("request_id").toString(),
                consumed.get("execution_authorization_id").toString(),
                consumed.get("execution_plan_sha256").toString(),
                consumed.get("execution_plan_file").toString());
    }

    private record Prepared(Path workspace, String profileSha, String reviewSha) { }
    private record Authorized(LocalProgramUnderstandingApprovalService service, String requestId,
            String authorizationId, String planSha, String planFile) { }
}
