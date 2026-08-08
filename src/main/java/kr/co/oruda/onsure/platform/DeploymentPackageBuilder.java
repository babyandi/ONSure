package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a deployable on-premises/air-gapped package (NFR-DEPLOY: signed offline install
 * artifact): every file under a source root is copied into an output directory alongside a
 * manifest naming each file's sha256, and -- when the target {@link DeploymentProfile} requires it
 * (AIR_GAPPED) -- the manifest is Ed25519-signed with the same signing primitive already used for
 * approval receipts. This is packaging + integrity/authenticity verification, not an interactive
 * installer UI or update/rollback orchestrator.
 */
public final class DeploymentPackageBuilder {
    public static final String MANIFEST_CONTRACT = "ONSURE_DEPLOYMENT_PACKAGE_MANIFEST_V1";

    public record PackageEntry(String relativePath, String sha256) {}

    public record BuildResult(int fileCount, String manifestSha256, Path manifestFile, boolean signed) {}

    public record VerifyResult(boolean integrityValid, boolean signed, boolean signatureValid, List<String> violations) {
        public VerifyResult { violations = List.copyOf(violations); }
    }

    private static final ObjectMapper WRITER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper READER = new ObjectMapper();

    private DeploymentPackageBuilder() {}

    public static BuildResult build(
            Path sourceRoot,
            Path outputDir,
            DeploymentProfile profile,
            PrivateKey signingKey,
            String keyId) throws Exception {
        Objects.requireNonNull(profile, "profile");
        Path source = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("DEPLOYMENT_PACKAGE_SOURCE_MISSING");
        }
        Path destination = outputDir.toAbsolutePath().normalize();
        requireNoSymlink(destination, "DEPLOYMENT_PACKAGE_OUTPUT_SYMLINK_PROHIBITED");
        if (hasEntries(destination)) throw new IllegalStateException("DEPLOYMENT_PACKAGE_OUTPUT_NOT_EMPTY");
        if (profile.signedOfflineSnapshotRequired() && signingKey == null) {
            throw new IllegalArgumentException("DEPLOYMENT_PACKAGE_SIGNATURE_REQUIRED_FOR_PROFILE:" + profile);
        }
        if (signingKey != null && (keyId == null || keyId.isBlank())) {
            throw new IllegalArgumentException("DEPLOYMENT_PACKAGE_KEY_ID_REQUIRED");
        }

        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(source)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .sorted(Comparator.comparing(path -> Hashing.relative(source, path)))
                    .forEach(files::add);
        }
        if (files.isEmpty()) throw new IllegalStateException("DEPLOYMENT_PACKAGE_SOURCE_EMPTY");

        Files.createDirectories(destination);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (Path file : files) {
            String relativePath = Hashing.relative(source, file);
            String digest = Hashing.file(file);
            Path copyTarget = destination.resolve(relativePath);
            Files.createDirectories(copyTarget.getParent());
            Files.copy(file, copyTarget, StandardCopyOption.COPY_ATTRIBUTES);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("relative_path", relativePath);
            entry.put("sha256", digest);
            entries.add(entry);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("contract", MANIFEST_CONTRACT);
        manifest.put("deployment_profile", profile.name());
        manifest.put("file_count", entries.size());
        manifest.put("entries", entries);
        manifest.put("built_at", Instant.now().toString());
        manifest.put("final_claim_allowed", false);

        boolean signed = signingKey != null;
        if (signed) {
            manifest.put("key_id", keyId);
            manifest.put("signature", LocalReceiptCrypto.sign(manifest, signingKey));
        }
        String manifestSha256 = Hashing.sha256(WRITER.writeValueAsBytes(entries));

        Path manifestFile = destination.resolve("deployment-package-manifest.json");
        WRITER.writeValue(manifestFile.toFile(), manifest);
        return new BuildResult(entries.size(), manifestSha256, manifestFile, signed);
    }

    public static VerifyResult verify(Path packageDir, PublicKey verificationKey) throws Exception {
        Path source = packageDir.toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        Path manifestFile = source.resolve("deployment-package-manifest.json");
        if (!Files.isRegularFile(manifestFile)) {
            return new VerifyResult(false, false, false, List.of("DEPLOYMENT_PACKAGE_MANIFEST_MISSING"));
        }
        Map<String, Object> manifest = readObject(manifestFile);
        if (!MANIFEST_CONTRACT.equals(manifest.get("contract"))) {
            return new VerifyResult(false, false, false, List.of("DEPLOYMENT_PACKAGE_MANIFEST_CONTRACT_INVALID"));
        }

        boolean signed = manifest.containsKey("signature");
        boolean signatureValid = false;
        if (signed) {
            if (verificationKey == null) {
                violations.add("DEPLOYMENT_PACKAGE_VERIFICATION_KEY_REQUIRED");
            } else {
                signatureValid = LocalReceiptCrypto.verify(manifest, verificationKey);
                if (!signatureValid) violations.add("DEPLOYMENT_PACKAGE_SIGNATURE_INVALID");
            }
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) manifest.getOrDefault("entries", List.of());
        for (Map<String, Object> entry : entries) {
            String relativePath = String.valueOf(entry.get("relative_path"));
            String expectedSha256 = String.valueOf(entry.get("sha256"));
            Path file = source.resolve(relativePath);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
                violations.add("DEPLOYMENT_PACKAGE_FILE_MISSING:" + relativePath);
                continue;
            }
            if (!expectedSha256.equals(Hashing.file(file))) {
                violations.add("DEPLOYMENT_PACKAGE_FILE_INTEGRITY_MISMATCH:" + relativePath);
            }
        }

        boolean integrityValid = violations.stream().noneMatch(value -> value.startsWith("DEPLOYMENT_PACKAGE_FILE_"));
        return new VerifyResult(integrityValid, signed, signatureValid, violations);
    }

    private static Map<String, Object> readObject(Path file) throws Exception {
        return objectMap(READER.readTree(file.toFile()));
    }

    private static Map<String, Object> objectMap(JsonNode node) {
        if (node == null || !node.isObject()) throw new IllegalArgumentException("JSON_OBJECT_REQUIRED");
        Map<String, Object> result = new LinkedHashMap<>();
        Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            result.put(field.getKey(), jsonValue(field.getValue()));
        }
        return result;
    }

    private static Object jsonValue(JsonNode node) {
        if (node == null || node.isNull()) return null;
        if (node.isObject()) return objectMap(node);
        if (node.isArray()) {
            List<Object> values = new ArrayList<>();
            node.forEach(item -> values.add(jsonValue(item)));
            return values;
        }
        if (node.isTextual()) return node.textValue();
        if (node.isBoolean()) return node.booleanValue();
        if (node.isIntegralNumber()) return node.canConvertToInt() ? node.intValue() : node.longValue();
        if (node.isFloatingPointNumber()) return node.decimalValue();
        throw new IllegalArgumentException("JSON_VALUE_TYPE_UNSUPPORTED:" + node.getNodeType());
    }

    private static boolean hasEntries(Path directory) throws Exception {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var stream = Files.list(directory)) {
            return stream.findAny().isPresent();
        }
    }

    private static void requireNoSymlink(Path path, String code) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
        }
    }
}
