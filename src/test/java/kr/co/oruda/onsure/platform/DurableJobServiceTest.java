package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.assurance.ApprovalReceiptVerifier;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.LocalKeyRegistry;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import kr.co.oruda.onsure.platform.DurableJobService.ApprovalBinding;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableJobServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void checkpointPauseResumeCancelAndRevisionCasAreEnforced() throws Exception {
        DurableJobService jobs = new DurableJobService(temp.resolve("jobs"));
        jobs.create("job-001", "validation.run", "a".repeat(64), "operator-001");
        jobs.start("job-001", 1, "operator-001");
        jobs.checkpoint("job-001", 2, "STATIC_ANALYSIS", "finding:12",
                List.of("artifact-a", "artifact-b"), List.of(), null, null, "operator-001");
        assertThrows(IllegalStateException.class,
                () -> jobs.pause("job-001", 2, "operator-001"));
        jobs.pause("job-001", 3, "operator-001");
        jobs.resume("job-001", 4, "operator-001");
        jobs.cancel("job-001", 5, "user requested cancellation", "operator-001");
        assertEquals("CANCELLED", jobs.read("job-001").get("status"));
        assertThrows(IllegalStateException.class,
                () -> jobs.resume("job-001", 6, "operator-001"));
    }

    @Test
    void restartRestoresCheckpointAndConsumedApprovalHistoryAsPaused() throws Exception {
        ApprovalFixture approval = consumedApproval();
        Path jobsRoot = temp.resolve("jobs-with-approval");
        DurableJobService beforeRestart = new DurableJobService(jobsRoot);
        beforeRestart.create("job-restore-001", "patch.apply", "b".repeat(64), "operator-001");
        beforeRestart.start("job-restore-001", 1, "operator-001");
        beforeRestart.checkpoint("job-restore-001", 2, "PATCH_APPLY", "hunk:1",
                List.of("patch-plan.json"), List.of(new ApprovalBinding(
                        approval.receipt(), "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "PATCH_HUNK_APPROVAL")),
                approval.registry(), approval.replayLedger(), "operator-001");

        DurableJobService afterRestart = new DurableJobService(jobsRoot);
        Map<String, Object> receipt = afterRestart.recoverAllAfterRestart("restart-controller");
        assertEquals(1, receipt.get("recovered_count"));
        assertTrue(Files.isRegularFile(jobsRoot.resolve("restart-restore-receipt.json")));
        Map<String, Object> state = afterRestart.read("job-restore-001");
        assertEquals("PAUSED", state.get("status"));
        assertEquals(true, state.get("approval_history_restored"));
        assertEquals(1, ((List<?>) state.get("approval_history")).size());
        afterRestart.resume("job-restore-001", 4, "operator-001");
        assertEquals("RUNNING", afterRestart.read("job-restore-001").get("status"));
    }

    @Test
    void runningJobWithoutCheckpointRecoversToHold() throws Exception {
        Path jobsRoot = temp.resolve("jobs-hold");
        DurableJobService jobs = new DurableJobService(jobsRoot);
        jobs.create("job-hold-001", "validation.run", "c".repeat(64), "operator-001");
        jobs.start("job-hold-001", 1, "operator-001");

        new DurableJobService(jobsRoot).recoverAllAfterRestart("restart-controller");
        assertEquals("RECOVERY_HOLD",
                new DurableJobService(jobsRoot).read("job-hold-001").get("status"));
    }

    private ApprovalFixture consumedApproval() throws Exception {
        Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var pair = LocalReceiptCrypto.generate();
        Path authority = temp.resolve("authority");
        Path publicKey = authority.resolve("approval-public.key");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registryFile = authority.resolve("trusted-key-registry.json");
        assertEquals(Decision.PASS, new LocalKeyRegistry(registryFile).register(
                new LocalKeyRegistry.KeyRecord(
                        "approval-key-job", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                        now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", "ONSURE_HUNK_APPROVAL_RECEIPT_V1");
        value.put("approval_id", "approval-job-checkpoint-001");
        value.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        value.put("approval_purpose", "PATCH_HUNK_APPROVAL");
        value.put("nonce", "nonce-job-checkpoint-0001");
        value.put("patch_plan_id", "PATCH-job-001");
        value.put("patch_plan_file_sha256", "d".repeat(64));
        value.put("approved_hunk_ids", List.of("HUNK-job-001"));
        value.put("branch_name", "fix/job-checkpoint");
        value.put("actor", "reviewer@example.invalid");
        value.put("key_id", "approval-key-job");
        value.put("signature_algorithm", "Ed25519");
        value.put("approved_at", now.toString());
        value.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        value.put("allow_direct_main_write", false);
        value.put("allow_force_push", false);
        value.put("allow_merge", false);
        value.put("signature", LocalReceiptCrypto.sign(value, pair.getPrivate()));
        Path receipt = temp.resolve("job-approval.json");
        mapper.writeValue(receipt.toFile(), value);
        Path replay = authority.resolve("approval-replay-ledger.jsonl");
        new ApprovalReceiptVerifier(registryFile, replay).requireValidAndConsume(
                receipt, "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "PATCH_HUNK_APPROVAL", now);
        return new ApprovalFixture(receipt, registryFile, replay);
    }

    private record ApprovalFixture(Path receipt, Path registry, Path replayLedger) {}
}
