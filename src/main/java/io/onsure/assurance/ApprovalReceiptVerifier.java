package io.onsure.assurance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Verifies human/external approval receipts before patch or Git mutations. */
public final class ApprovalReceiptVerifier {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public ValidationResult verify(Path receiptFile, String expectedContract) {
        List<String> violations = new ArrayList<>();
        try {
            Path receipt = receiptFile.toAbsolutePath().normalize();
            if (!Files.isRegularFile(receipt, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(receipt)) {
                return ValidationResult.fail(List.of("APPROVAL_RECEIPT_FILE_INVALID"));
            }
            Map<String, Object> value = mapper.readValue(receipt.toFile(), new TypeReference<>() {});
            if (!Objects.equals(expectedContract, value.get("contract"))) {
                violations.add("APPROVAL_RECEIPT_CONTRACT_MISMATCH");
            }
            if (!"HUMAN_OR_EXTERNAL_APPROVER".equals(value.get("authority_class"))) {
                violations.add("APPROVAL_AUTHORITY_CLASS_INVALID");
            }
            String actor = string(value, "actor");
            if (actor.isBlank() || "ONSURE_AUTOMATION".equals(actor)) {
                violations.add("APPROVAL_ACTOR_INVALID");
            }
            String keyId = string(value, "key_id");
            String publicKeyFile = string(value, "public_key_file");
            if (!"Ed25519".equals(value.get("signature_algorithm"))) {
                violations.add("APPROVAL_SIGNATURE_ALGORITHM_INVALID");
            }
            if (string(value, "signature").isBlank()) violations.add("APPROVAL_SIGNATURE_MISSING");
            Instant approvedAt;
            try {
                approvedAt = Instant.parse(string(value, "approved_at"));
                if (approvedAt.isAfter(Instant.now().plusSeconds(300))) {
                    violations.add("APPROVAL_TIMESTAMP_IN_FUTURE");
                }
            } catch (Exception invalidTime) {
                approvedAt = null;
                violations.add("APPROVAL_TIMESTAMP_INVALID");
            }
            Path publicKey = publicKeyFile.isBlank() ? null
                    : Path.of(publicKeyFile).toAbsolutePath().normalize();
            if (publicKey == null
                    || !Files.isRegularFile(publicKey, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(publicKey)) {
                violations.add("APPROVAL_PUBLIC_KEY_INVALID");
            } else {
                String expectedKeyId = "ed25519:" + sha256(Files.readAllBytes(publicKey));
                if (!expectedKeyId.equals(keyId)) violations.add("APPROVAL_KEY_ID_MISMATCH");
                try {
                    if (!LocalReceiptCrypto.verify(
                            value, LocalReceiptCrypto.readPublicKey(publicKey))) {
                        violations.add("APPROVAL_SIGNATURE_INVALID");
                    }
                } catch (Exception verificationFailure) {
                    violations.add("APPROVAL_SIGNATURE_UNREADABLE");
                }
            }
        } catch (Exception unreadable) {
            violations.add("APPROVAL_RECEIPT_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public void requireValid(Path receiptFile, String expectedContract) {
        ValidationResult result = verify(receiptFile, expectedContract);
        if (result.decision() != Decision.PASS) {
            throw new IllegalStateException("APPROVAL_RECEIPT_INVALID:" + String.join(",", result.violations()));
        }
    }

    private static String string(Map<String, Object> value, String key) {
        Object item = value.get(key);
        return item instanceof String text ? text : "";
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
