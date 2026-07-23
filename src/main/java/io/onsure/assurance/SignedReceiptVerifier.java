package io.onsure.assurance;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class SignedReceiptVerifier {
    private final CanonicalReceiptSerializer serializer;
    private final SigningKeyRegistry registry;
    private final Ed25519SignatureVerifier signatureVerifier;

    public SignedReceiptVerifier(CanonicalReceiptSerializer serializer, SigningKeyRegistry registry,
            Ed25519SignatureVerifier signatureVerifier) {
        this.serializer = serializer;
        this.registry = registry;
        this.signatureVerifier = signatureVerifier;
    }

    public ValidationResult verify(ReceiptEnvelope receipt, Instant now) {
        List<String> violations = new ArrayList<>();
        ValidationResult envelope = new ReceiptEnvelopeValidator().validate(receipt);
        violations.addAll(envelope.violations());
        ValidationResult key = registry.validateForReceipt(receipt, now);
        violations.addAll(key.violations());
        if (key.decision() == Decision.PASS) {
            ValidationResult signature = signatureVerifier.verify(
                    serializer.serializeForSigning(receipt),
                    receipt.signature(),
                    registry.getPublicKey(receipt.keyId()));
            violations.addAll(signature.violations());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }
}
