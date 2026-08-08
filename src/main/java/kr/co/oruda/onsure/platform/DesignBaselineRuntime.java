package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Set;

/** Recalculates the normative 13-document and 22-requirement authorities. */
public final class DesignBaselineRuntime {
    private static final ObjectMapper JSON = new ObjectMapper();

    private DesignBaselineRuntime() {}

    public static Result verify(Path root) throws IOException {
        Path registryPath = root.resolve(
            "docs/official-baseline/v2026.07.29/onsure-design-baseline-registry.v1.json");
        JsonNode registry = JSON.readTree(registryPath.toFile());
        int documents = 0;
        for (JsonNode document : registry.required("documents")) {
            Path path = root.resolve(document.required("path").asText());
            String actual = sha256(Files.readAllBytes(path));
            if (!actual.equals(document.required("sha256").asText())) {
                throw new IOException("BASELINE_DOCUMENT_HASH_MISMATCH:"
                    + document.required("document_key").asText());
            }
            documents++;
        }
        if (documents != 13) {
            throw new IOException("BASELINE_DOCUMENT_COUNT_NOT_13:" + documents);
        }
        JsonNode coverage = JSON.readTree(root.resolve(
            "status/final-product-requirement-coverage.v1.json").toFile());
        Set<String> ids = new HashSet<>();
        for (JsonNode requirement : coverage.required("requirements")) {
            ids.add(requirement.required("id").asText());
        }
        if (ids.size() != 22) {
            throw new IOException("FINAL_REQUIREMENT_AUTHORITY_NOT_22_UNIQUE:" + ids.size());
        }
        JsonNode acceptance = JSON.readTree(root.resolve(
            "contracts/final-acceptance-source-registry.v1.json").toFile());
        int total = acceptance.required("total_expected_items").asInt();
        int sum = 0;
        for (JsonNode source : acceptance.required("sources")) {
            sum += source.required("expected_count").asInt();
        }
        if (total != 62 || sum != 62) {
            throw new IOException("FINAL_ACCEPTANCE_AUTHORITY_NOT_62:" + total + ":" + sum);
        }
        return new Result(documents, ids.size(), total, "SELF_VALIDATION_NONFINAL", false);
    }

    private static String sha256(byte[] value) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
        } catch (NoSuchAlgorithmException error) {
            throw new IOException("SHA256_UNAVAILABLE", error);
        }
    }

    public record Result(
        int documentCount,
        int requirementCount,
        int acceptanceCount,
        String decisionCeiling,
        boolean finalClaimAllowed) {}
}

