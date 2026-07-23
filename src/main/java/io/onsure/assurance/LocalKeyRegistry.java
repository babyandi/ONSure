package io.onsure.assurance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class LocalKeyRegistry {
    public record KeyRecord(String keyId, String authority, String publicKeyFile,
            Instant validFrom, Instant validUntil, boolean revoked, Instant revokedAt, String replacedBy) {
        public KeyRecord(String keyId, String authority, String publicKeyFile,
                Instant validFrom, Instant validUntil, boolean revoked, String replacedBy) {
            this(keyId, authority, publicKeyFile, validFrom, validUntil, revoked, null, replacedBy);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final Path registryFile;

    public LocalKeyRegistry(Path registryFile) {
        if (registryFile == null) throw new IllegalArgumentException("registryFile");
        this.registryFile = registryFile;
    }

    public synchronized ValidationResult register(KeyRecord record) {
        if (record == null || record.keyId() == null || record.keyId().isBlank()
                || record.authority() == null || record.authority().isBlank()
                || record.publicKeyFile() == null || record.publicKeyFile().isBlank()
                || record.validFrom() == null || record.validUntil() == null
                || !record.validFrom().isBefore(record.validUntil())
                || (record.revoked() && record.revokedAt() == null)
                || (!record.revoked() && record.revokedAt() != null)) {
            return ValidationResult.fail(List.of("INVALID_KEY_RECORD"));
        }
        try {
            List<KeyRecord> records = load();
            if (records.stream().anyMatch(r -> Objects.equals(r.keyId(), record.keyId()))) {
                return ValidationResult.fail(List.of("KEY_ID_ALREADY_REGISTERED"));
            }
            records.add(record);
            save(records);
            return ValidationResult.pass();
        } catch (Exception e) {
            return ValidationResult.fail(List.of("KEY_REGISTRY_WRITE_FAILED"));
        }
    }

    public synchronized ValidationResult revoke(String keyId, String replacementKeyId) {
        return revoke(keyId, replacementKeyId, Instant.now());
    }

    synchronized ValidationResult revoke(String keyId, String replacementKeyId, Instant revokedAt) {
        if (keyId == null || keyId.isBlank()) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
        if (revokedAt == null) return ValidationResult.fail(List.of("REVOCATION_TIME_INVALID"));
        try {
            List<KeyRecord> records = load();
            boolean found = false;
            List<KeyRecord> updated = new ArrayList<>();
            for (KeyRecord record : records) {
                if (Objects.equals(record.keyId(), keyId)) {
                    if (record.revoked()) return ValidationResult.fail(List.of("SIGNING_KEY_ALREADY_REVOKED"));
                    if (revokedAt.isBefore(record.validFrom())) {
                        return ValidationResult.fail(List.of("REVOCATION_BEFORE_KEY_VALIDITY"));
                    }
                    updated.add(new KeyRecord(record.keyId(), record.authority(), record.publicKeyFile(),
                            record.validFrom(), record.validUntil(), true, revokedAt, replacementKeyId));
                    found = true;
                } else updated.add(record);
            }
            if (!found) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
            save(updated);
            return ValidationResult.pass();
        } catch (Exception e) {
            return ValidationResult.fail(List.of("KEY_REGISTRY_WRITE_FAILED"));
        }
    }

    public synchronized ValidationResult validate(String keyId, String authority, Instant at) {
        if (keyId == null || keyId.isBlank()) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
        if (at == null) return ValidationResult.fail(List.of("SIGNING_TIME_INVALID"));
        try {
            KeyRecord record = load().stream().filter(r -> Objects.equals(r.keyId(), keyId)).findFirst().orElse(null);
            if (record == null) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
            List<String> violations = new ArrayList<>();
            if (!Objects.equals(record.authority(), authority)) violations.add("KEY_AUTHORITY_MISMATCH");
            if (record.revoked() && (record.revokedAt() == null || !at.isBefore(record.revokedAt()))) {
                violations.add("REVOKED_SIGNING_KEY");
            }
            if (at.isBefore(record.validFrom()) || !at.isBefore(record.validUntil())) {
                violations.add("SIGNING_KEY_OUTSIDE_VALIDITY");
            }
            if (!Files.isRegularFile(Path.of(record.publicKeyFile()))) violations.add("PUBLIC_KEY_FILE_MISSING");
            return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
        } catch (Exception e) {
            return ValidationResult.fail(List.of("KEY_REGISTRY_READ_FAILED"));
        }
    }

    public synchronized List<KeyRecord> load() throws Exception {
        if (!Files.exists(registryFile)) return new ArrayList<>();
        return new ArrayList<>(mapper.readValue(registryFile.toFile(), new TypeReference<List<KeyRecord>>() {}));
    }

    private void save(List<KeyRecord> records) throws Exception {
        Path parent = registryFile.toAbsolutePath().normalize().getParent();
        if (parent != null) Files.createDirectories(parent);
        mapper.writerWithDefaultPrettyPrinter().writeValue(registryFile.toFile(), records);
    }
}
