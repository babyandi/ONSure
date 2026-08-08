package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.Signature;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Shared canonical Ed25519 verification for external authority receipts. */
public final class OrudaAuthorityReceiptSignatureVerifier {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public ValidationResult verify(Path receiptFile, Path registryFile, String authority,
            String principalField, String timeField) {
        List<String> violations = new ArrayList<>();
        try {
            if (!Files.isRegularFile(receiptFile) || Files.isSymbolicLink(receiptFile)) {
                return ValidationResult.fail(List.of("ORUDA_SIGNED_AUTHORITY_RECEIPT_MISSING"));
            }
            Map<String, Object> complete = MAPPER.readValue(receiptFile.toFile(), new TypeReference<>() {});
            Object storedReceiptDigest = complete.remove("receipt_sha256");
            if (!(storedReceiptDigest instanceof String receiptDigest)
                    || !receiptDigest.matches("[0-9a-f]{64}")
                    || !receiptDigest.equals(receiptDigest(complete))) {
                violations.add("ORUDA_SIGNED_AUTHORITY_RECEIPT_HASH_MISMATCH");
            }
            String keyId = text(complete.get("signer_key_id"));
            String algorithm = text(complete.get("signature_algorithm"));
            String signatureText = text(complete.get("signature"));
            String principalId = text(complete.get(principalField));
            Instant signedAt;
            try { signedAt = Instant.parse(text(complete.get(timeField))); }
            catch (Exception e) {
                violations.add("ORUDA_SIGNED_AUTHORITY_TIME_INVALID");
                signedAt = null;
            }
            if (!"Ed25519".equals(algorithm)) violations.add("ORUDA_SIGNED_AUTHORITY_ALGORITHM_INVALID");
            if (keyId.isBlank()) violations.add("ORUDA_SIGNED_AUTHORITY_KEY_ID_MISSING");
            if (signatureText.isBlank()) violations.add("ORUDA_SIGNED_AUTHORITY_SIGNATURE_MISSING");
            if (principalId.isBlank()) violations.add("ORUDA_SIGNED_AUTHORITY_PRINCIPAL_MISSING");

            OrudaAuthorityKeyRegistry registry = new OrudaAuthorityKeyRegistry(registryFile);
            if (signedAt != null) {
                ValidationResult keyValidation = registry.validate(keyId, authority, principalId, signedAt);
                if (keyValidation.decision() != Decision.PASS) violations.addAll(keyValidation.violations());
            }
            if (violations.stream().noneMatch(value -> value.contains("KEY")
                    || value.contains("SIGNATURE_MISSING") || value.contains("ALGORITHM"))) {
                Map<String, Object> unsigned = new LinkedHashMap<>(complete);
                unsigned.remove("signature");
                byte[] signature = Base64.getDecoder().decode(signatureText);
                Signature verifier = Signature.getInstance("Ed25519");
                verifier.initVerify(registry.resolve(keyId).publicKey());
                verifier.update(canonicalPayload(unsigned));
                if (!verifier.verify(signature)) violations.add("ORUDA_SIGNED_AUTHORITY_SIGNATURE_INVALID");
            }
        } catch (IllegalArgumentException e) {
            violations.add("ORUDA_SIGNED_AUTHORITY_SIGNATURE_ENCODING_INVALID");
        } catch (Exception e) {
            violations.add("ORUDA_SIGNED_AUTHORITY_RECEIPT_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public static byte[] canonicalPayload(Map<String, Object> unsignedBody) throws Exception {
        return MAPPER.writeValueAsBytes(new TreeMap<>(unsignedBody));
    }

    public static String receiptDigest(Map<String, Object> bodyWithSignatureWithoutReceiptDigest) throws Exception {
        byte[] canonical = MAPPER.writeValueAsBytes(new TreeMap<>(bodyWithSignatureWithoutReceiptDigest));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }

    private static String text(Object value) { return value == null ? "" : value.toString(); }
}
