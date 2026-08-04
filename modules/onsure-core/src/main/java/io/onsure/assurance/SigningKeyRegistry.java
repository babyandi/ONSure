package io.onsure.assurance;

import java.security.PublicKey;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public final class SigningKeyRegistry {
    public record KeyEntry(String keyId, String authority, PublicKey publicKey,
            Instant notBefore, Instant expiresAt, boolean revoked, Instant revokedAt) {
        public KeyEntry(String keyId, String authority, PublicKey publicKey,
                Instant notBefore, Instant expiresAt, boolean revoked) {
            this(keyId, authority, publicKey, notBefore, expiresAt, revoked, null);
        }
    }

    private final Map<String, KeyEntry> keys = new ConcurrentHashMap<>();

    public ValidationResult register(KeyEntry entry) {
        if (entry == null || blank(entry.keyId()) || blank(entry.authority()) || entry.publicKey() == null
                || entry.notBefore() == null || entry.expiresAt() == null
                || !entry.notBefore().isBefore(entry.expiresAt())
                || (entry.revoked() && entry.revokedAt() == null)
                || (!entry.revoked() && entry.revokedAt() != null)) {
            return ValidationResult.fail(java.util.List.of("INVALID_KEY_ENTRY"));
        }
        if (keys.putIfAbsent(entry.keyId(), entry) != null) {
            return ValidationResult.fail(java.util.List.of("DUPLICATE_KEY_ID"));
        }
        return ValidationResult.pass();
    }

    public ValidationResult revoke(String keyId, Instant revokedAt) {
        if (blank(keyId) || revokedAt == null) {
            return ValidationResult.fail(java.util.List.of("INVALID_KEY_REVOCATION"));
        }
        KeyEntry current = keys.get(keyId);
        if (current == null) return ValidationResult.fail(java.util.List.of("UNKNOWN_KEY_ID"));
        if (current.revoked()) return ValidationResult.fail(java.util.List.of("KEY_ALREADY_REVOKED"));
        if (revokedAt.isBefore(current.notBefore())) {
            return ValidationResult.fail(java.util.List.of("REVOCATION_BEFORE_KEY_VALIDITY"));
        }
        keys.put(keyId, new KeyEntry(current.keyId(), current.authority(), current.publicKey(),
                current.notBefore(), current.expiresAt(), true, revokedAt));
        return ValidationResult.pass();
    }

    public ValidationResult validateForReceipt(ReceiptEnvelope receipt, Instant now) {
        Objects.requireNonNull(now, "now");
        if (receipt == null || blank(receipt.keyId())) {
            return ValidationResult.fail(java.util.List.of("MISSING_KEY_ID"));
        }
        KeyEntry entry = keys.get(receipt.keyId());
        if (entry == null) {
            return ValidationResult.fail(java.util.List.of("UNKNOWN_KEY_ID"));
        }
        java.util.List<String> violations = new java.util.ArrayList<>();
        Instant issuedAt = receipt.issuedAt();
        if (!Objects.equals(entry.authority(), receipt.authority())) violations.add("KEY_AUTHORITY_MISMATCH");
        if (issuedAt == null) {
            violations.add("MISSING_ISSUED_AT");
        } else {
            if (issuedAt.isAfter(now)) violations.add("RECEIPT_ISSUED_IN_FUTURE");
            if (issuedAt.isBefore(entry.notBefore())) violations.add("KEY_NOT_YET_VALID");
            if (!issuedAt.isBefore(entry.expiresAt())) violations.add("KEY_EXPIRED");
            if (entry.revoked() && (entry.revokedAt() == null || !issuedAt.isBefore(entry.revokedAt()))) {
                violations.add("KEY_REVOKED");
            }
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public PublicKey getPublicKey(String keyId) {
        KeyEntry entry = keys.get(keyId);
        return entry == null ? null : entry.publicKey();
    }

    private static boolean blank(String value) { return value == null || value.isBlank(); }
}
