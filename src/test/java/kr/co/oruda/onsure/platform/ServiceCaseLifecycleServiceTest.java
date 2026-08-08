package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.assurance.ApprovalReceiptVerifier;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.LocalKeyRegistry;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ServiceCaseLifecycleServiceTest {
    @TempDir Path temp;

    @Test
    void paymentReceiptCannotStartWorkUntilSignedVerificationPasses() throws Exception {
        ServiceCaseLifecycleService service = new ServiceCaseLifecycleService(temp.resolve("cases"));
        openAndQuote(service, "case-001");
        service.recordPaymentReceipt("case-001", "provider-001", "receipt-001",
                "KRW", 100000, "a".repeat(64), "customer-001");
        assertThrows(IllegalStateException.class,
                () -> service.startWork("case-001", "operator-001"));
        assertThrows(IllegalStateException.class, () -> service.verifyPayment(
                "case-001", "verifier-001", "b".repeat(64), true, "operator-001"));

        KeyMaterial key = approvalKey("payment-key-001");
        Map<String, Object> state = service.read("case-001");
        Path receipt = signedVerification(
                key, "payment-approval-001", "PAYMENT_VERIFICATION", "PAYMENT",
                "case-001", ((Number) state.get("revision")).longValue(),
                "provider-001", "receipt-001", "a".repeat(64), "PASS");
        service.verifyPayment("case-001", receipt, key.registry(),
                temp.resolve("approval-replay.jsonl"), "operator-001");
        assertEquals("PAYMENT_CONFIRMED", service.read("case-001").get("status"));
        service.startWork("case-001", "operator-001");
        assertEquals("IN_PROGRESS", service.read("case-001").get("status"));
        assertTrue(service.verify("case-001").valid());
    }

    @Test
    void providerReceiptReplayAcrossCasesIsRejected() throws Exception {
        ServiceCaseLifecycleService service = new ServiceCaseLifecycleService(temp.resolve("replay"));
        openAndQuote(service, "case-a");
        openAndQuote(service, "case-b");
        service.recordPaymentReceipt("case-a", "provider-001", "shared-receipt",
                "KRW", 100000, "c".repeat(64), "customer-a");
        assertThrows(IllegalStateException.class, () -> service.recordPaymentReceipt(
                "case-b", "provider-001", "shared-receipt",
                "KRW", 100000, "c".repeat(64), "customer-b"));
    }

    @Test
    void missingTenantAndStaleVerificationRevisionFailClosed() throws Exception {
        ServiceCaseLifecycleService service = new ServiceCaseLifecycleService(temp.resolve("stale"));
        Map<String, Object> incomplete = Map.of(
                "contract", "ONSURE_TENANT_CONTEXT_V1",
                "organization_id", "org-001",
                "tenant_id", "tenant-001",
                "actor_id", "actor-001");
        assertThrows(IllegalArgumentException.class,
                () -> service.open(incomplete, "case-invalid", "VALIDATION", "git:abc", "actor-001"));

        openAndQuote(service, "case-stale");
        service.recordPaymentReceipt("case-stale", "provider-001", "receipt-stale",
                "KRW", 100000, "d".repeat(64), "customer-001");
        KeyMaterial key = approvalKey("payment-key-stale");
        Map<String, Object> state = service.read("case-stale");
        Path receipt = signedVerification(
                key, "payment-approval-stale", "PAYMENT_VERIFICATION", "PAYMENT",
                "case-stale", ((Number) state.get("revision")).longValue(),
                "provider-001", "receipt-stale", "d".repeat(64), "PASS");
        service.setLegalHold("case-stale", true, "investigation", "auditor-001");
        assertThrows(IllegalStateException.class, () -> service.verifyPayment(
                "case-stale", receipt, key.registry(), temp.resolve("stale-replay.jsonl"), "operator-001"));
    }

    private void openAndQuote(ServiceCaseLifecycleService service, String caseId) throws Exception {
        service.open(tenant(), caseId, "AI_CODE_VALIDATION", "git:" + "a".repeat(40), "customer-001");
        service.recordPreflight(caseId, "PASS", "b".repeat(64), List.of(), "operator-001");
        service.createQuote(caseId, "quote-" + caseId, "KRW", 100000,
                Instant.now().plus(1, ChronoUnit.DAYS),
                List.of("PROGRAM_PROFILE", "VALIDATION_REPORT"), "operator-001");
        service.acceptOrder(caseId, "quote-" + caseId, "customer-001");
    }

    private KeyMaterial approvalKey(String keyId) throws Exception {
        var pair = LocalReceiptCrypto.generate();
        Path publicKey = temp.resolve(keyId + ".public");
        Path registry = temp.resolve(keyId + ".registry.json");
        LocalReceiptCrypto.writePublicKey(publicKey, pair.getPublic());
        LocalKeyRegistry keyRegistry = new LocalKeyRegistry(registry);
        Instant now = Instant.now();
        assertEquals(Decision.PASS, keyRegistry.register(new LocalKeyRegistry.KeyRecord(
                keyId, ApprovalReceiptVerifier.AUTHORITY, publicKey.toString(),
                now.minus(1, ChronoUnit.DAYS), now.plus(1, ChronoUnit.DAYS), false, null)).decision());
        return new KeyMaterial(keyId, pair, registry);
    }

    private Path signedVerification(
            KeyMaterial key,
            String approvalId,
            String purpose,
            String type,
            String caseId,
            long revision,
            String provider,
            String providerReceiptId,
            String providerReceiptDigest,
            String decision) throws Exception {
        Instant now = Instant.now();
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", ServiceCaseLifecycleService.VERIFICATION_RECEIPT_CONTRACT);
        receipt.put("approval_id", approvalId);
        receipt.put("authority_class", ApprovalReceiptVerifier.AUTHORITY_CLASS);
        receipt.put("approval_purpose", purpose);
        receipt.put("nonce", "nonce-" + approvalId + "-0001");
        receipt.put("case_id", caseId);
        receipt.put("case_revision", revision);
        receipt.put("verification_type", type);
        receipt.put("provider", provider);
        receipt.put("provider_receipt_id", providerReceiptId);
        receipt.put("provider_receipt_digest", providerReceiptDigest);
        receipt.put("decision", decision);
        receipt.put("reasons", List.of("PROVIDER_API_VERIFIED"));
        receipt.put("actor", "verifier@example.invalid");
        receipt.put("key_id", key.keyId());
        receipt.put("signature_algorithm", "Ed25519");
        receipt.put("approved_at", now.toString());
        receipt.put("expires_at", now.plus(1, ChronoUnit.HOURS).toString());
        receipt.put("signature", LocalReceiptCrypto.sign(receipt, key.pair().getPrivate()));
        Path file = temp.resolve(approvalId + ".json");
        new ObjectMapper().writeValue(file.toFile(), receipt);
        return file;
    }

    private static Map<String, Object> tenant() {
        return Map.of(
                "contract", "ONSURE_TENANT_CONTEXT_V1",
                "organization_id", "org-001",
                "tenant_id", "tenant-001",
                "workspace_id", "workspace-001",
                "actor_id", "actor-001");
    }

    private record KeyMaterial(String keyId, java.security.KeyPair pair, Path registry) {}
}