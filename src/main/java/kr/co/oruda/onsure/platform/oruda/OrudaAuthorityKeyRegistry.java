package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Read-only trusted key registry for independent human/operator authority receipts. */
public final class OrudaAuthorityKeyRegistry {
    public static final String CONTRACT = "ONSURE_ORUDA_AUTHORITY_KEY_REGISTRY_V1";

    public record KeyRecord(
            String keyId,
            String authority,
            String principalId,
            String publicKeyFile,
            Instant validFrom,
            Instant validUntil,
            boolean revoked,
            Instant revokedAt,
            String replacedBy) {}

    public record Registry(String contract, String registryId, List<KeyRecord> keys) {
        public Registry { keys = List.copyOf(keys); }
    }

    public record ResolvedKey(KeyRecord record, PublicKey publicKey) {}

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE);
    private final Path registryFile;

    public OrudaAuthorityKeyRegistry(Path registryFile) {
        this.registryFile = Objects.requireNonNull(registryFile, "registryFile").toAbsolutePath().normalize();
    }

    public Registry load() throws Exception {
        if (!Files.isRegularFile(registryFile) || Files.isSymbolicLink(registryFile)) {
            throw new IllegalArgumentException("ORUDA_AUTHORITY_KEY_REGISTRY_MISSING");
        }
        Registry registry = mapper.readValue(registryFile.toFile(), Registry.class);
        if (!CONTRACT.equals(registry.contract())) {
            throw new IllegalArgumentException("ORUDA_AUTHORITY_KEY_REGISTRY_CONTRACT_MISMATCH");
        }
        Map<String, KeyRecord> keyIds = new HashMap<>();
        for (KeyRecord record : registry.keys()) {
            if (record.keyId() == null || record.keyId().isBlank()
                    || record.authority() == null || record.authority().isBlank()
                    || record.principalId() == null || record.principalId().isBlank()
                    || record.publicKeyFile() == null || record.publicKeyFile().isBlank()
                    || record.validFrom() == null || record.validUntil() == null
                    || !record.validFrom().isBefore(record.validUntil())
                    || (record.revoked() && record.revokedAt() == null)
                    || (!record.revoked() && record.revokedAt() != null)) {
                throw new IllegalArgumentException("ORUDA_AUTHORITY_KEY_RECORD_INVALID");
            }
            if (keyIds.put(record.keyId(), record) != null) {
                throw new IllegalArgumentException("ORUDA_AUTHORITY_KEY_ID_DUPLICATE:" + record.keyId());
            }
        }
        return registry;
    }

    public ValidationResult validate(String keyId, String authority, String principalId, Instant signedAt) {
        List<String> violations = new ArrayList<>();
        try {
            if (signedAt == null) return ValidationResult.fail(List.of("ORUDA_AUTHORITY_SIGNING_TIME_INVALID"));
            KeyRecord record = load().keys().stream()
                    .filter(value -> Objects.equals(value.keyId(), keyId))
                    .findFirst().orElse(null);
            if (record == null) return ValidationResult.fail(List.of("ORUDA_AUTHORITY_KEY_UNKNOWN"));
            if (!Objects.equals(record.authority(), authority)) violations.add("ORUDA_AUTHORITY_ROLE_MISMATCH");
            if (!Objects.equals(record.principalId(), principalId)) violations.add("ORUDA_AUTHORITY_PRINCIPAL_MISMATCH");
            if (signedAt.isBefore(record.validFrom()) || !signedAt.isBefore(record.validUntil())) {
                violations.add("ORUDA_AUTHORITY_KEY_OUTSIDE_VALIDITY");
            }
            if (record.revoked() && (record.revokedAt() == null || !signedAt.isBefore(record.revokedAt()))) {
                violations.add("ORUDA_AUTHORITY_KEY_REVOKED");
            }
            Path keyFile = resolvePublicKeyFile(record.publicKeyFile());
            if (!Files.isRegularFile(keyFile) || Files.isSymbolicLink(keyFile)) {
                violations.add("ORUDA_AUTHORITY_PUBLIC_KEY_MISSING");
            }
        } catch (Exception e) {
            violations.add("ORUDA_AUTHORITY_KEY_REGISTRY_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public ResolvedKey resolve(String keyId) throws Exception {
        KeyRecord record = load().keys().stream()
                .filter(value -> Objects.equals(value.keyId(), keyId))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("ORUDA_AUTHORITY_KEY_UNKNOWN"));
        return new ResolvedKey(record, readPublicKey(resolvePublicKeyFile(record.publicKeyFile())));
    }

    private Path resolvePublicKeyFile(String relativePath) {
        if (relativePath == null || relativePath.isBlank() || Path.of(relativePath).isAbsolute()) {
            throw new IllegalArgumentException("ORUDA_AUTHORITY_PUBLIC_KEY_PATH_INVALID");
        }
        Path root = registryFile.getParent();
        if (root == null) throw new IllegalArgumentException("ORUDA_AUTHORITY_REGISTRY_PARENT_MISSING");
        Path resolved = root.resolve(relativePath).normalize();
        if (!resolved.startsWith(root)) throw new IllegalArgumentException("ORUDA_AUTHORITY_PUBLIC_KEY_PATH_ESCAPE");
        return resolved;
    }

    private static PublicKey readPublicKey(Path file) throws Exception {
        String text = Files.readString(file).replaceAll("-----[^-]+-----", "").replaceAll("\\s+", "");
        byte[] encoded;
        try { encoded = Base64.getDecoder().decode(text); }
        catch (IllegalArgumentException e) { encoded = Files.readAllBytes(file); }
        return KeyFactory.getInstance("Ed25519").generatePublic(new X509EncodedKeySpec(encoded));
    }
}
