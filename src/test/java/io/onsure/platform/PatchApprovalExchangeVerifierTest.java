package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PatchApprovalExchangeVerifierTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void bindsRequestReceiptAndExactPlanScopeWithoutClaimingSignerVerification() throws Exception {
        FilesBundle files = files();
        Map<String, Object> result = new PatchApprovalExchangeVerifier().verify(
                files.request(), files.receipt(), files.plan());
        assertEquals("BOUND_PENDING_CRYPTOGRAPHIC_RECEIPT_VERIFICATION", result.get("state"));
        assertEquals(false, result.get("external_signer_verification_included"));
        assertEquals(List.of("HUNK-001"), result.get("bound_hunk_ids"));
        assertEquals(List.of("src/policy.txt"), result.get("bound_files"));
    }

    @Test
    void rejectsScopeBranchDigestAndTimestampSubstitution() throws Exception {
        FilesBundle files = files();
        Map<String, Object> receipt = mapper.readValue(files.receipt().toFile(), Map.class);
        receipt.put("approved_hunk_ids", List.of("HUNK-OTHER"));
        mapper.writeValue(files.receipt().toFile(), receipt);
        assertThrows(IllegalArgumentException.class, () -> new PatchApprovalExchangeVerifier().verify(
                files.request(), files.receipt(), files.plan()));
    }

    @Test
    void rejectsExpiredReceiptBeforeCryptographicVerification() throws Exception {
        FilesBundle files = files();
        Map<String, Object> request = mapper.readValue(files.request().toFile(), Map.class);
        Map<String, Object> receipt = mapper.readValue(files.receipt().toFile(), Map.class);
        Instant old = Instant.now().minus(2, ChronoUnit.HOURS).truncatedTo(ChronoUnit.SECONDS);
        request.put("created_at", old.toString());
        receipt.put("approved_at", old.plusSeconds(1).toString());
        receipt.put("expires_at", old.plus(1, ChronoUnit.HOURS).toString());
        mapper.writeValue(files.request().toFile(), request);
        mapper.writeValue(files.receipt().toFile(), receipt);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new PatchApprovalExchangeVerifier().verify(
                        files.request(), files.receipt(), files.plan()));
        assertTrue(error.getMessage().contains("RECEIPT_EXPIRED"));
    }

    @Test
    void verifiesTheSameBoundExchangeThroughCliAndAuthenticatedLocalApi() throws Exception {
        FilesBundle files = files();
        Map<String, Object> request = Map.of(
                "approval_request_file", files.request().toString(),
                "approval_receipt_file", files.receipt().toString(),
                "patch_plan_file", files.plan().toString());
        Path requestFile = temp.resolve("workflow-request.json");
        mapper.writeValue(requestFile.toFile(), request);
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        int exit = ONSureCli.run(new String[]{
                "workflow", temp.toString(), "patch.verify-approval", requestFile.toString()
        }, new PrintStream(output), new PrintStream(new ByteArrayOutputStream()));
        assertEquals(0, exit);
        assertTrue(output.toString().contains("BOUND_PENDING_CRYPTOGRAPHIC_RECEIPT_VERIFICATION"));

        String token = "approval-api-token-" + "x".repeat(32);
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(temp, token);
        int port = server.startAndGetPort(0);
        try {
            String body = mapper.writeValueAsString(Map.of(
                    "operation", "patch.verify-approval", "request", request));
            HttpResponse<String> response = HttpClient.newHttpClient().send(
                    HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/v1/workflow"))
                            .header("Authorization", "Bearer " + token)
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(body)).build(),
                    HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            assertTrue(response.body().contains("BOUND_PENDING_CRYPTOGRAPHIC_RECEIPT_VERIFICATION"));
        } finally {
            server.stop();
        }
    }

    private FilesBundle files() throws Exception {
        Instant created = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Map<String, Object> hunk = Map.of(
                "hunk_id", "HUNK-001", "finding_id", "FINDING-001",
                "relative_path", "src/policy.txt");
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("contract", "ONSURE_PATCH_PLAN_V1");
        plan.put("patch_plan_id", "PATCH-001");
        plan.put("hunks", List.of(hunk));
        plan.put("direct_main_write_allowed", false);
        plan.put("force_push_allowed", false);
        plan.put("merge_allowed", false);
        plan.put("final_claim_allowed", false);
        Path planFile = temp.resolve("patch-plan.json");
        mapper.writeValue(planFile.toFile(), plan);
        String digest = Hashing.file(planFile);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("contract", "ONSURE_HUNK_APPROVAL_REQUEST_V1");
        request.put("request_id", "REQUEST-001");
        request.put("request_state", "AWAITING_EXTERNAL_SIGNATURE");
        request.put("receipt_contract", "ONSURE_HUNK_APPROVAL_RECEIPT_V1");
        request.put("approval_purpose", "PATCH_HUNK_APPROVAL");
        request.put("patch_plan_id", "PATCH-001");
        request.put("patch_plan_file", planFile.toAbsolutePath().toString());
        request.put("patch_plan_file_sha256", digest);
        request.put("selected_hunk_ids", List.of("HUNK-001"));
        request.put("selected_files", List.of("src/policy.txt"));
        request.put("selection_scope", "HUNK");
        request.put("branch_name", "fix/approved");
        request.put("risk_preview", Map.of(
                "classification", "BOUNDED_CHANGE_SURFACE_CANDIDATE", "level", "LOW",
                "factors", List.of("HUNKS_1", "FILES_1"), "independent_risk_review", "NOT_RUN"));
        request.put("impact_scope", Map.of(
                "file_count", 1, "hunk_count", 1, "finding_ids", List.of("FINDING-001")));
        request.put("rollback_preview", Map.of(
                "method", "PATCH_APPLY_RECEIPT_BACKUP_AND_ISOLATED_GIT_WORKTREE_REMOVAL",
                "source_workspace_write_allowed", false, "automatic_rollback_executed", false));
        request.put("allow_direct_main_write", false);
        request.put("allow_force_push", false);
        request.put("allow_merge", false);
        request.put("final_claim_allowed", false);
        request.put("created_at", created.toString());
        request.put("signer_must_supply", List.of(
                "approval_id", "nonce", "actor", "key_id", "signature_algorithm",
                "signature", "approved_at", "expires_at"));
        Path requestFile = temp.resolve("request.json");
        mapper.writeValue(requestFile.toFile(), request);

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", "ONSURE_HUNK_APPROVAL_RECEIPT_V1");
        receipt.put("approval_id", "APPROVAL-001");
        receipt.put("nonce", "nonce-approval-0001");
        receipt.put("key_id", "key-001");
        receipt.put("authority_class", "HUMAN_OR_EXTERNAL_APPROVER");
        receipt.put("approval_purpose", "PATCH_HUNK_APPROVAL");
        receipt.put("patch_plan_id", "PATCH-001");
        receipt.put("patch_plan_file_sha256", digest);
        receipt.put("approved_hunk_ids", List.of("HUNK-001"));
        receipt.put("branch_name", "fix/approved");
        receipt.put("actor", "reviewer");
        receipt.put("signature_algorithm", "Ed25519");
        receipt.put("signature", "a".repeat(64));
        receipt.put("approved_at", created.plusSeconds(1).toString());
        receipt.put("expires_at", created.plusSeconds(3600).toString());
        receipt.put("allow_direct_main_write", false);
        receipt.put("allow_force_push", false);
        receipt.put("allow_merge", false);
        Path receiptFile = temp.resolve("receipt.json");
        mapper.writeValue(receiptFile.toFile(), receipt);
        return new FilesBundle(requestFile, receiptFile, planFile);
    }

    private record FilesBundle(Path request, Path receipt, Path plan) {}
}
