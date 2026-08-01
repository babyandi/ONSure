package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.GitWorkflowService;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalReceiptIdCollisionTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sameApprovalIdWithDifferentValidReceiptIsCollisionNotReplay() throws Exception {
        Instant now = Instant.now();
        var pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve("approval.public");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registryFile = temp.resolve("registry.json");
        new LocalKeyRegistry(registryFile).register(new LocalKeyRegistry.KeyRecord(
                "approval-key-001", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null));
        Path replayLedger = temp.resolve("replay.jsonl");

        Path original = writeReceipt("original.json", pair, now,
                "reviewer-a@example.invalid", "nonce-approval-collision-0001");
        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(registryFile, replayLedger);
        verifier.requireValidAndConsume(
                original, GitWorkflowService.DELIVERY_APPROVAL_CONTRACT,
                GitWorkflowService.APPROVAL_PURPOSE, now);

        Path collision = writeReceipt("collision.json", pair, now,
                "reviewer-b@example.invalid", "nonce-approval-collision-0002");
        ValidationResult result = verifier.verify(
                collision, GitWorkflowService.DELIVERY_APPROVAL_CONTRACT,
                GitWorkflowService.APPROVAL_PURPOSE, now);
        assertTrue(result.violations().contains("APPROVAL_RECEIPT_ID_COLLISION"));
    }

    private Path writeReceipt(
            String fileName,
            java.security.KeyPair pair,
            Instant now,
            String actor,
            String nonce) throws Exception {
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", GitWorkflowService.DELIVERY_APPROVAL_CONTRACT);
        receipt.put("approval_id", "approval-shared-id-001");
        receipt.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        receipt.put("approval_purpose", GitWorkflowService.APPROVAL_PURPOSE);
        receipt.put("nonce", nonce);
        receipt.put("actor", actor);
        receipt.put("key_id", "approval-key-001");
        receipt.put("signature_algorithm", "Ed25519");
        receipt.put("approved_at", now.toString());
        receipt.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        receipt.put("signature", LocalReceiptCrypto.sign(receipt, pair.getPrivate()));
        Path file = temp.resolve(fileName);
        mapper.writeValue(file.toFile(), receipt);
        return file;
    }
}
