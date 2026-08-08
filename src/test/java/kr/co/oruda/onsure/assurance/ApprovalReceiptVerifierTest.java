package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
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

    @Test
    void externalAnchorRejectsRollbackToStaleLedgerSnapshot() throws Exception {
        AnchorFixture fixture = anchorFixture();
        Path first = writeSignedReceipt(fixture, "approval-anchor-001", "nonce-anchor-test-0001");
        fixture.verifier().requireValidAndConsume(
                first, "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "PATCH_HUNK_APPROVAL", fixture.now());
        byte[] staleSnapshot = Files.readAllBytes(fixture.replayLedger());

        Path second = writeSignedReceipt(fixture, "approval-anchor-002", "nonce-anchor-test-0002");
        fixture.verifier().requireValidAndConsume(
                second, "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "PATCH_HUNK_APPROVAL", fixture.now());
        Files.write(fixture.replayLedger(), staleSnapshot);

        Path third = writeSignedReceipt(fixture, "approval-anchor-003", "nonce-anchor-test-0003");
        ValidationResult result = fixture.verifier().verify(
                third, "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "PATCH_HUNK_APPROVAL", fixture.now());
        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.violations().contains(
                "APPROVAL_REPLAY_EXTERNAL_ANCHOR_SEQUENCE_MISMATCH"));
    }

    @Test
    void externalAnchorRejectsWholeLedgerRewriteEvenWithValidInternalHashes() throws Exception {
        AnchorFixture fixture = anchorFixture();
        for (int index = 1; index <= 2; index++) {
            Path receipt = writeSignedReceipt(fixture, "approval-rehash-00" + index,
                    "nonce-rehash-test-000" + index);
            fixture.verifier().requireValidAndConsume(
                    receipt, "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "PATCH_HUNK_APPROVAL", fixture.now());
        }
        List<String> rewritten = new ArrayList<>();
        String previous = "0".repeat(64);
        for (String line : Files.readAllLines(fixture.replayLedger())) {
            Map<String, Object> entry = fixture.mapper().readValue(line, Map.class);
            entry.put("consumed_at", "2026-07-26T12:30:00Z");
            entry.put("previous_hash", previous);
            entry.remove("entry_hash");
            previous = sha256(fixture.mapper().writeValueAsBytes(new TreeMap<>(entry)));
            entry.put("entry_hash", previous);
            rewritten.add(fixture.mapper().writeValueAsString(entry));
        }
        Files.write(fixture.replayLedger(), rewritten, StandardCharsets.UTF_8);

        Path third = writeSignedReceipt(fixture, "approval-rehash-003", "nonce-rehash-test-0003");
        ValidationResult result = fixture.verifier().verify(
                third, "ONSURE_HUNK_APPROVAL_RECEIPT_V1", "PATCH_HUNK_APPROVAL", fixture.now());
        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.violations().contains("APPROVAL_REPLAY_EXTERNAL_ANCHOR_HEAD_MISMATCH"));
    }

    private AnchorFixture anchorFixture() throws Exception {
        Instant now = Instant.parse("2026-07-26T12:00:00Z");
        var pair = LocalReceiptCrypto.generate();
        Path authorityRoot = temp.resolve("authority");
        Path publicKey = authorityRoot.resolve("approval-public.key");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registryFile = authorityRoot.resolve("key-registry.json");
        LocalKeyRegistry registry = new LocalKeyRegistry(registryFile);
        assertEquals(Decision.PASS, registry.register(new LocalKeyRegistry.KeyRecord(
                "approval-key-anchor", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());
        Path replayLedger = authorityRoot.resolve("approval-replay.jsonl");
        Path externalAnchor = temp.resolve("external-anchor/approval-heads.jsonl");
        return new AnchorFixture(now, pair, replayLedger, new ObjectMapper(),
                new ApprovalReceiptVerifier(registryFile, replayLedger, externalAnchor));
    }

    private Path writeSignedReceipt(AnchorFixture fixture, String approvalId, String nonce)
            throws Exception {
        Map<String, Object> value = receipt(
                fixture.now(), "approval-key-anchor", "PATCH_HUNK_APPROVAL");
        value.put("approval_id", approvalId);
        value.put("nonce", nonce);
        value.put("signature", LocalReceiptCrypto.sign(value, fixture.pair().getPrivate()));
        Path file = temp.resolve(approvalId + ".json");
        fixture.mapper().writeValue(file.toFile(), value);
        return file;
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record AnchorFixture(
            Instant now, java.security.KeyPair pair, Path replayLedger,
            ObjectMapper mapper, ApprovalReceiptVerifier verifier) {}

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
