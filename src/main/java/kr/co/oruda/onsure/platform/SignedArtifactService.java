package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Detached Ed25519 signature for a single opaque build artifact file (e.g. the .vsix the VS Code
 * extension's own `npx vsce package` script produces -- DEPLOYMENT:
 * VSIX_PACKAGING_HANDLED_SEPARATELY_BY_VSCODE_EXTENSION_NPX_VSCE_NOT_SIGNED_HERE). Writes a
 * companion "&lt;artifact&gt;.signature.json" beside the artifact instead of modifying it, so it
 * works for any binary format without needing to understand its internal structure.
 */
public final class SignedArtifactService {
    public static final String CONTRACT = "ONSURE_SIGNED_ARTIFACT_V1";

    public record SignResult(Path signatureFile, String sha256) {}

    public record VerifyResult(boolean integrityValid, boolean signatureValid, List<String> violations) {
        public VerifyResult { violations = List.copyOf(violations); }
    }

    private static final ObjectMapper WRITER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper READER = new ObjectMapper();

    private SignedArtifactService() {}

    public static SignResult sign(Path artifactFile, String keyId, PrivateKey key) throws Exception {
        Path normalized = requireRegularFile(artifactFile);
        if (keyId == null || keyId.isBlank()) throw new IllegalArgumentException("SIGNED_ARTIFACT_KEY_ID_REQUIRED");
        Path signatureFile = signatureFileFor(normalized);
        if (Files.exists(signatureFile, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("SIGNED_ARTIFACT_SIGNATURE_ALREADY_EXISTS:" + signatureFile);
        }

        String sha256 = Hashing.file(normalized);
        Map<String, Object> record = new LinkedHashMap<>();
        record.put("contract", CONTRACT);
        record.put("artifact_file_name", normalized.getFileName().toString());
        record.put("sha256", sha256);
        record.put("key_id", keyId);
        record.put("signed_at", Instant.now().toString());
        record.put("signature", LocalReceiptCrypto.sign(record, key));

        WRITER.writeValue(signatureFile.toFile(), record);
        return new SignResult(signatureFile, sha256);
    }

    public static VerifyResult verify(Path artifactFile, PublicKey key) throws Exception {
        Path normalized = requireRegularFile(artifactFile);
        Path signatureFile = signatureFileFor(normalized);
        if (!Files.isRegularFile(signatureFile, LinkOption.NOFOLLOW_LINKS)) {
            return new VerifyResult(false, false, List.of("SIGNED_ARTIFACT_SIGNATURE_FILE_MISSING"));
        }

        Map<String, Object> record = objectMap(READER.readTree(signatureFile.toFile()));
        List<String> violations = new ArrayList<>();
        if (!CONTRACT.equals(record.get("contract"))) {
            return new VerifyResult(false, false, List.of("SIGNED_ARTIFACT_CONTRACT_INVALID"));
        }
        if (!normalized.getFileName().toString().equals(record.get("artifact_file_name"))) {
            violations.add("SIGNED_ARTIFACT_FILE_NAME_MISMATCH");
        }
        boolean integrityValid = String.valueOf(record.get("sha256")).equals(Hashing.file(normalized));
        if (!integrityValid) violations.add("SIGNED_ARTIFACT_INTEGRITY_MISMATCH");

        boolean signatureValid = key != null && LocalReceiptCrypto.verify(record, key);
        if (key == null) violations.add("SIGNED_ARTIFACT_VERIFICATION_KEY_REQUIRED");
        else if (!signatureValid) violations.add("SIGNED_ARTIFACT_SIGNATURE_INVALID");

        return new VerifyResult(integrityValid && violations.stream().noneMatch(v -> v.startsWith("SIGNED_ARTIFACT_FILE_NAME")),
                signatureValid, violations);
    }

    private static Path signatureFileFor(Path artifactFile) {
        return artifactFile.resolveSibling(artifactFile.getFileName() + ".signature.json");
    }

    private static Path requireRegularFile(Path artifactFile) {
        Path normalized = artifactFile.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(normalized)) {
            throw new IllegalArgumentException("SIGNED_ARTIFACT_FILE_INVALID:" + normalized);
        }
        return normalized;
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
}
