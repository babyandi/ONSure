package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.platform.GitWorkflowService;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ConsumedApprovalReceiptVerifierTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void onlyTrustedPreviouslyConsumedReceiptCanAuthorizeLaterTransition() throws Exception {
        Instant now = Instant.now();
        var pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve("approval.public");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        Path registryFile = temp.resolve("key-registry.json");
        LocalKeyRegistry registry = new LocalKeyRegistry(registryFile);
        registry.register(new LocalKeyRegistry.KeyRecord(
                "approval-key-001", ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null));
        Path replayLedger = temp.resolve("replay.jsonl");

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", GitWorkflowService.DELIVERY_APPROVAL_CONTRACT);
        receipt.put("approval_id", "approval-consumed-001");
        receipt.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        receipt.put("approval_purpose", GitWorkflowService.APPROVAL_PURPOSE);
        receipt.put("nonce", "nonce-consumed-approval-0001");
        receipt.put("actor", "reviewer@example.invalid");
        receipt.put("key_id", "approval-key-001");
        receipt.put("signature_algorithm", "Ed25519");
        receipt.put("approved_at", now.toString());
        receipt.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        receipt.put("signature", LocalReceiptCrypto.sign(receipt, pair.getPrivate()));
        Path receiptFile = temp.resolve("approval.json");
        mapper.writeValue(receiptFile.toFile(), receipt);

        assertThrows(IllegalStateException.class, () ->
                ConsumedApprovalReceiptVerifier.requireTrustedConsumed(
                        receiptFile, registryFile, replayLedger,
                        GitWorkflowService.DELIVERY_APPROVAL_CONTRACT,
                        GitWorkflowService.APPROVAL_PURPOSE, now,
                        "TEST_CONSUMED_APPROVAL_INVALID"));

        new ApprovalReceiptVerifier(registryFile, replayLedger).requireValidAndConsume(
                receiptFile, GitWorkflowService.DELIVERY_APPROVAL_CONTRACT,
                GitWorkflowService.APPROVAL_PURPOSE, now);
        assertDoesNotThrow(() -> ConsumedApprovalReceiptVerifier.requireTrustedConsumed(
                receiptFile, registryFile, replayLedger,
                GitWorkflowService.DELIVERY_APPROVAL_CONTRACT,
                GitWorkflowService.APPROVAL_PURPOSE, now,
                "TEST_CONSUMED_APPROVAL_INVALID"));

        Map<String, Object> tampered = new LinkedHashMap<>(receipt);
        tampered.put("actor", "attacker@example.invalid");
        Path tamperedFile = temp.resolve("tampered-approval.json");
        mapper.writeValue(tamperedFile.toFile(), tampered);
        assertThrows(IllegalStateException.class, () ->
                ConsumedApprovalReceiptVerifier.requireTrustedConsumed(
                        tamperedFile, registryFile, replayLedger,
                        GitWorkflowService.DELIVERY_APPROVAL_CONTRACT,
                        GitWorkflowService.APPROVAL_PURPOSE, now,
                        "TEST_CONSUMED_APPROVAL_INVALID"));
    }
}
