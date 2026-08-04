package io.onsure.assurance;

import java.security.PublicKey;
import java.security.Signature;
import java.util.Base64;
import java.util.List;

public final class Ed25519SignatureVerifier {
    public ValidationResult verify(byte[] canonicalPayload, String base64Signature, PublicKey publicKey) {
        if (canonicalPayload == null || canonicalPayload.length == 0) {
            return ValidationResult.fail(List.of("EMPTY_CANONICAL_PAYLOAD"));
        }
        if (base64Signature == null || base64Signature.isBlank()) {
            return ValidationResult.fail(List.of("MISSING_SIGNATURE"));
        }
        if (publicKey == null) {
            return ValidationResult.fail(List.of("MISSING_PUBLIC_KEY"));
        }
        try {
            byte[] signatureBytes = Base64.getDecoder().decode(base64Signature);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(publicKey);
            verifier.update(canonicalPayload);
            return verifier.verify(signatureBytes)
                    ? ValidationResult.pass()
                    : ValidationResult.fail(List.of("INVALID_SIGNATURE"));
        } catch (IllegalArgumentException e) {
            return ValidationResult.fail(List.of("INVALID_SIGNATURE_ENCODING"));
        } catch (Exception e) {
            return ValidationResult.fail(List.of("SIGNATURE_VERIFICATION_ERROR"));
        }
    }
}
