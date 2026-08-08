package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class SignedReceiptVerifierTest {
    private static final String D = "a".repeat(64);
    private static final String D2 = "b".repeat(64);
    private static final String COMMIT = "c".repeat(40);

    @Test
    void canonicalSerializationIgnoresMapInsertionOrder() {
        ReceiptEnvelope a = receipt(new LinkedHashMap<>(Map.of("b", 2, "a", 1)), "sig");
        LinkedHashMap<String, Object> reversed = new LinkedHashMap<>();
        reversed.put("a", 1); reversed.put("b", 2);
        ReceiptEnvelope b = receipt(reversed, "sig");
        CanonicalReceiptSerializer serializer = new CanonicalReceiptSerializer();
        assertArrayEquals(serializer.serializeForSigning(a), serializer.serializeForSigning(b));
    }

    @Test
    void validSignatureAndAuthorityPass() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant now = Instant.parse("2026-07-21T10:00:00Z");
        SigningKeyRegistry registry = new SigningKeyRegistry();
        assertEquals(Decision.PASS, registry.register(new SigningKeyRegistry.KeyEntry(
                "otester-key", "OTESTER_AGENT", pair.getPublic(), now.minusSeconds(60), now.plusSeconds(60), false)).decision());
        ReceiptEnvelope unsigned = receipt(Map.of("result", "PASS"), "placeholder");
        byte[] payload = new CanonicalReceiptSerializer().serializeForSigning(unsigned);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate()); signer.update(payload);
        ReceiptEnvelope signed = receipt(unsigned.claims(), Base64.getEncoder().encodeToString(signer.sign()));
        ValidationResult result = new SignedReceiptVerifier(new CanonicalReceiptSerializer(), registry,
                new Ed25519SignatureVerifier()).verify(signed, now);
        assertEquals(Decision.PASS, result.decision());
    }

    @Test
    void historicalReceiptRemainsValidAfterKeyExpiryAndLaterRevocation() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant issuedAt = Instant.parse("2026-07-21T09:59:00Z");
        Instant verifyAt = Instant.parse("2026-07-21T12:00:00Z");
        SigningKeyRegistry registry = new SigningKeyRegistry();
        assertEquals(Decision.PASS, registry.register(new SigningKeyRegistry.KeyEntry(
                "otester-key", "OTESTER_AGENT", pair.getPublic(),
                issuedAt.minusSeconds(60), issuedAt.plusSeconds(60), false)).decision());
        assertEquals(Decision.PASS, registry.revoke("otester-key", issuedAt.plusSeconds(30)).decision());
        assertEquals(Decision.PASS, registry.validateForReceipt(receiptAt(Map.of(), "sig", issuedAt), verifyAt).decision());
    }

    @Test
    void receiptAtOrAfterRevocationFails() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant revokedAt = Instant.parse("2026-07-21T10:00:00Z");
        SigningKeyRegistry registry = new SigningKeyRegistry();
        registry.register(new SigningKeyRegistry.KeyEntry(
                "otester-key", "OTESTER_AGENT", pair.getPublic(), revokedAt.minusSeconds(120), revokedAt.plusSeconds(120), false));
        registry.revoke("otester-key", revokedAt);
        ValidationResult result = registry.validateForReceipt(receiptAt(Map.of(), "sig", revokedAt), revokedAt.plusSeconds(1));
        assertTrue(result.violations().contains("KEY_REVOKED"));
    }

    @Test
    void authorityMismatchAndKeyInvalidAtIssuanceFail() throws Exception {
        KeyPair pair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Instant now = Instant.parse("2026-07-21T10:00:00Z");
        SigningKeyRegistry registry = new SigningKeyRegistry();
        registry.register(new SigningKeyRegistry.KeyEntry(
                "otester-key", "OAUDIT_AGENT", pair.getPublic(), now.minusSeconds(120), now.minusSeconds(1), false));
        ValidationResult result = registry.validateForReceipt(receiptAt(Map.of(), "sig", now), now);
        assertTrue(result.violations().contains("KEY_AUTHORITY_MISMATCH"));
        assertTrue(result.violations().contains("KEY_EXPIRED"));
    }

    private static ReceiptEnvelope receipt(Map<String, Object> claims, String signature) {
        return receiptAt(claims, signature, Instant.parse("2026-07-21T09:59:00Z"));
    }

    private static ReceiptEnvelope receiptAt(Map<String, Object> claims, String signature, Instant issuedAt) {
        return new ReceiptEnvelope("r-1", "OTESTER", "OTESTER_AGENT", "w-1", COMMIT, D, "permit-1",
                "TESTED", "OTESTER_VERIFIED", Decision.PASS, issuedAt,
                List.of(D), List.of(D2), claims, "otester-key", signature, D);
    }
}
