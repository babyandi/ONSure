package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.Signature;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class SecurityPrimitivesTest {
    private static final String D = "a".repeat(64);
    private static final String D2 = "b".repeat(64);
    private static final String COMMIT = "c".repeat(40);

    @Test
    void validPermitPassesAndExpiredPermitFails() {
        Instant now = Instant.parse("2026-07-21T10:00:00Z");
        PermitContext valid = new PermitContext(
                "permit-1", "w-1", D, D2, Set.of("BUILD"),
                now.minusSeconds(60), now.plusSeconds(60), false);
        PermitValidator validator = new PermitValidator();
        assertEquals(Decision.PASS, validator.validate(valid, "w-1", D, D2, Set.of("BUILD"), now).decision());

        PermitContext expired = new PermitContext(
                "permit-2", "w-1", D, D2, Set.of("BUILD"),
                now.minusSeconds(120), now.minusSeconds(1), false);
        assertTrue(validator.validate(expired, "w-1", D, D2, Set.of("BUILD"), now)
                .violations().contains("EXPIRED_PERMIT"));
    }

    @Test
    void missingPermitOrValidationInputsFailClosed() {
        PermitValidator validator = new PermitValidator();
        assertTrue(validator.validate(null, "w-1", D, D2, Set.of("BUILD"), Instant.EPOCH)
                .violations().contains("PERMIT_MISSING"));
        PermitContext permit = new PermitContext(
                "permit-1", "w-1", D, D2, Set.of("BUILD"),
                Instant.EPOCH, Instant.EPOCH.plusSeconds(60), false);
        ValidationResult result = validator.validate(permit, "w-1", D, D2, null, null);
        assertTrue(result.violations().contains("REQUIRED_PERMIT_SCOPES_MISSING"));
        assertTrue(result.violations().contains("PERMIT_VALIDATION_TIME_MISSING"));
    }

    @Test
    void invalidPermitWindowIsRejected() {
        PermitContext permit = new PermitContext(
                "permit-1", "w-1", D, D2, Set.of("BUILD"),
                Instant.EPOCH.plusSeconds(60), Instant.EPOCH, false);
        assertTrue(new PermitValidator().validate(permit, "w-1", D, D2, Set.of("BUILD"), Instant.EPOCH)
                .violations().contains("PERMIT_VALIDITY_WINDOW_INVALID"));
    }

    @Test
    void replayIsBlockedAcrossWorkspaceMutation() {
        ReceiptReplayLedger ledger = new ReceiptReplayLedger();
        ReceiptEnvelope first = receipt("r-1", "w-1", D);
        ReceiptEnvelope replayed = receipt("r-1", "w-2", D);
        assertEquals(Decision.PASS, ledger.consume(first).decision());
        assertTrue(ledger.consume(replayed).violations().contains("RECEIPT_REPLAY"));
    }

    @Test
    void validEd25519SignaturePassesAndTamperFails() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("Ed25519");
        KeyPair pair = generator.generateKeyPair();
        byte[] payload = "canonical-receipt".getBytes(StandardCharsets.UTF_8);
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(pair.getPrivate());
        signer.update(payload);
        String signature = Base64.getEncoder().encodeToString(signer.sign());

        Ed25519SignatureVerifier verifier = new Ed25519SignatureVerifier();
        assertEquals(Decision.PASS, verifier.verify(payload, signature, pair.getPublic()).decision());
        assertTrue(verifier.verify("tampered".getBytes(StandardCharsets.UTF_8), signature, pair.getPublic())
                .violations().contains("INVALID_SIGNATURE"));
    }

    private static ReceiptEnvelope receipt(String id, String workspace, String selfHash) {
        return new ReceiptEnvelope(
                id, "OTESTER", "OTESTER_AGENT", workspace, COMMIT, D, "permit-1",
                "TESTED", "OTESTER_VERIFIED", Decision.PASS, Instant.parse("2026-07-21T09:00:00Z"),
                List.of(D), List.of(D2), Map.of("result", "PASS"), "otester-key", "sig", selfHash);
    }
}
