package kr.co.oruda.onsure.assurance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Cross-process-safe registry whose public keys must remain inside the authority root. */
public final class LocalKeyRegistry {
    public record KeyRecord(String keyId, String authority, String publicKeyFile,
            Instant validFrom, Instant validUntil, boolean revoked, Instant revokedAt, String replacedBy) {
        public KeyRecord(String keyId, String authority, String publicKeyFile,
                Instant validFrom, Instant validUntil, boolean revoked, String replacedBy) {
            this(keyId, authority, publicKeyFile, validFrom, validUntil, revoked, null, replacedBy);
        }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    private final Path registryFile;
    private final Path authorityRoot;
    private final Path lockFile;

    public LocalKeyRegistry(Path registryFile) {
        if (registryFile == null) throw new IllegalArgumentException("registryFile");
        this.registryFile = registryFile.toAbsolutePath().normalize();
        this.authorityRoot = Objects.requireNonNull(this.registryFile.getParent(), "registryFile parent");
        this.lockFile = this.registryFile.resolveSibling(this.registryFile.getFileName() + ".lock");
        requireNoSymlink(this.authorityRoot, "KEY_REGISTRY_AUTHORITY_ROOT_SYMLINK");
        requireNoSymlink(this.registryFile, "KEY_REGISTRY_FILE_SYMLINK");
    }

    public ValidationResult register(KeyRecord record) {
        List<String> structural = validateRecord(record);
        if (!structural.isEmpty()) return ValidationResult.fail(structural);
        try {
            return ExclusiveFileLock.call(lockFile, () -> {
                List<KeyRecord> records = loadUnlocked();
                if (records.stream().anyMatch(r -> Objects.equals(r.keyId(), record.keyId()))) {
                    return ValidationResult.fail(List.of("KEY_ID_ALREADY_REGISTERED"));
                }
                records.add(record);
                saveUnlocked(records);
                return ValidationResult.pass();
            });
        } catch (Exception e) {
            return ValidationResult.fail(List.of("KEY_REGISTRY_WRITE_FAILED"));
        }
    }

    public ValidationResult revoke(String keyId, String replacementKeyId) {
        return revoke(keyId, replacementKeyId, Instant.now());
    }

    ValidationResult revoke(String keyId, String replacementKeyId, Instant revokedAt) {
        if (keyId == null || keyId.isBlank()) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
        if (revokedAt == null) return ValidationResult.fail(List.of("REVOCATION_TIME_INVALID"));
        try {
            return ExclusiveFileLock.call(lockFile, () -> {
                List<KeyRecord> records = loadUnlocked();
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
                    } else {
                        updated.add(record);
                    }
                }
                if (!found) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
                saveUnlocked(updated);
                return ValidationResult.pass();
            });
        } catch (Exception e) {
            return ValidationResult.fail(List.of("KEY_REGISTRY_WRITE_FAILED"));
        }
    }

    public ValidationResult validate(String keyId, String authority, Instant at) {
        if (keyId == null || keyId.isBlank()) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
        if (at == null) return ValidationResult.fail(List.of("SIGNING_TIME_INVALID"));
        try {
            return ExclusiveFileLock.call(lockFile, () -> {
                KeyRecord record = loadUnlocked().stream()
                        .filter(r -> Objects.equals(r.keyId(), keyId)).findFirst().orElse(null);
                if (record == null) return ValidationResult.fail(List.of("UNKNOWN_SIGNING_KEY"));
                List<String> violations = new ArrayList<>(validatePublicKeyReference(record.publicKeyFile()));
                if (!Objects.equals(record.authority(), authority)) violations.add("KEY_AUTHORITY_MISMATCH");
                if (record.revoked() && (record.revokedAt() == null || !at.isBefore(record.revokedAt()))) {
                    violations.add("REVOKED_SIGNING_KEY");
                }
                if (at.isBefore(record.validFrom()) || !at.isBefore(record.validUntil())) {
                    violations.add("SIGNING_KEY_OUTSIDE_VALIDITY");
                }
                return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
            });
        } catch (Exception e) {
            return ValidationResult.fail(List.of("KEY_REGISTRY_READ_FAILED"));
        }
    }

    public List<KeyRecord> load() throws Exception {
        return ExclusiveFileLock.call(lockFile, this::loadUnlocked);
    }

    private List<String> validateRecord(KeyRecord record) {
        List<String> violations = new ArrayList<>();
        if (record == null || record.keyId() == null || record.keyId().isBlank()
                || record.authority() == null || record.authority().isBlank()
                || record.publicKeyFile() == null || record.publicKeyFile().isBlank()
                || record.validFrom() == null || record.validUntil() == null
                || !record.validFrom().isBefore(record.validUntil())
                || (record.revoked() && record.revokedAt() == null)
                || (!record.revoked() && record.revokedAt() != null)) {
            violations.add("INVALID_KEY_RECORD");
            return violations;
        }
        violations.addAll(validatePublicKeyReference(record.publicKeyFile()));
        return violations;
    }

    private List<String> validatePublicKeyReference(String value) {
        List<String> violations = new ArrayList<>();
        try {
            Path publicKey = Path.of(value).toAbsolutePath().normalize();
            if (!publicKey.startsWith(authorityRoot)) {
                violations.add("PUBLIC_KEY_OUTSIDE_AUTHORITY_ROOT");
                return violations;
            }
            requireNoSymlink(publicKey, "PUBLIC_KEY_SYMLINK_PROHIBITED");
            if (!Files.isRegularFile(publicKey, LinkOption.NOFOLLOW_LINKS)) {
                violations.add("PUBLIC_KEY_FILE_MISSING");
            }
        } catch (IllegalArgumentException invalid) {
            violations.add(invalid.getMessage());
        } catch (Exception invalid) {
            violations.add("PUBLIC_KEY_REFERENCE_INVALID");
        }
        return violations;
    }

    private List<KeyRecord> loadUnlocked() throws Exception {
        if (!Files.exists(registryFile)) return new ArrayList<>();
        requireNoSymlink(registryFile, "KEY_REGISTRY_FILE_SYMLINK");
        return new ArrayList<>(mapper.readValue(registryFile.toFile(), new TypeReference<List<KeyRecord>>() {}));
    }

    private void saveUnlocked(List<KeyRecord> records) throws Exception {
        Files.createDirectories(authorityRoot);
        requireNoSymlink(authorityRoot, "KEY_REGISTRY_AUTHORITY_ROOT_SYMLINK");
        Path temporary = registryFile.resolveSibling(registryFile.getFileName() + ".tmp");
        mapper.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), records);
        try {
            Files.move(temporary, registryFile, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, registryFile, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireNoSymlink(Path path, String code) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
            current = current.getParent();
        }
    }
}
