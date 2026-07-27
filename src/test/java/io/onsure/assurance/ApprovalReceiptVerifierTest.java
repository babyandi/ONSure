package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalReceiptVerifierTest {
    @TempDir Path temp;

    @Test
    void trustedReceiptPassesOnceAndReplayFails() throws Exception {
        Instant now = Instant.parse("2026-07-26T12:00:00Z");
        var pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve("approval-public.key");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registryFile = temp.resolve("key-registry.json");
        LocalKeyRegistry registry = new LocalKeyRegistry(registryFile);
        assertEquals(Decision.PASS, registry.register(new LocalKeyRegistry.KeyRecord(
                "approval-key-001", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());

        Map<String, Object> receipt = receipt(now, "approval-key-001", "PATCH_HUNK_APPROVAL");
        receipt.put("signature", LocalReceiptCrypto.sign(receipt, pair.getPrivate()));
        Path receiptFile = temp.resolve("approval.json");
        new ObjectMapper().writeValue(receiptFile.toFile(), receipt);

        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                registryFile, temp.resolve("approval-replay.jsonl"));
        assertEquals(Decision.PASS, verifier.verify(
                receiptFile, "ONSURE_HUNK_APPROVAL_RECEIPT_V1",
                "PATCH_HUNK_APPROVAL", now).decision());
        verifier.requireValidAndConsume(
                receiptFile, "ONSURE_HUNK_APPROVAL_RECEIPT_V1",
                "PATCH_HUNK_APPROVAL", now);
        assertTrue(verifier.verify(
                receiptFile, "ONSURE_HUNK_APPROVAL_RECEIPT_V1",
                "PATCH_HUNK_APPROVAL", now).violations().contains("APPROVAL_RECEIPT_REPLAY"));
    }

    @Test
    void unknownKeyPurposeMismatchAndExpiredReceiptFail() throws Exception {
        Instant now = Instant.parse("2026-07-26T12:00:00Z");
        var pair = LocalReceiptCrypto.generate();
        Map<String, Object> receipt = receipt(now.minus(8, ChronoUnit.DAYS),
                "untrusted-key", "PATCH_HUNK_APPROVAL");
        receipt.put("expires_at", now.minusSeconds(1).toString());
        receipt.put("signature", LocalReceiptCrypto.sign(receipt, pair.getPrivate()));
        Path receiptFile = temp.resolve("untrusted.json");
        new ObjectMapper().writeValue(receiptFile.toFile(), receipt);

        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                temp.resolve("empty-registry.json"), temp.resolve("replay.jsonl"));
        ValidationResult result = verifier.verify(
                receiptFile, "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "GIT_DELIVERY", now);
        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.violations().contains("APPROVAL_PURPOSE_MISMATCH"));
        assertTrue(result.violations().contains("APPROVAL_EXPIRED"));
        assertTrue(result.violations().contains("APPROVAL_TRUSTED_KEY_MISSING"));
    }

    private static Map<String, Object> receipt(Instant approvedAt, String keyId, String purpose) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", "ONSURE_HUNK_APPROVAL_RECEIPT_V1");
        value.put("approval_id", "approval-test-001");
        value.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        value.put("approval_purpose", purpose);
        value.put("nonce", "nonce-approval-test-0001");
        value.put("patch_plan_id", "PATCH-test-001");
        value.put("patch_plan_file_sha256", "a".repeat(64));
        value.put("approved_hunk_ids", java.util.List.of("HUNK-test-001"));
        value.put("branch_name", "fix/test-approved-patch");
        value.put("actor", "reviewer@example.invalid");
        value.put("key_id", keyId);
        value.put("signature_algorithm", "Ed25519");
        value.put("approved_at", approvedAt.toString());
        value.put("expires_at", approvedAt.plus(1, ChronoUnit.HOURS).toString());
        value.put("allow_direct_main_write", false);
        value.put("allow_force_push", false);
        value.put("allow_merge", false);
        return value;
    }
}