package io.onsure.platform.oruda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Verifies, but never self-issues, independent human blind-review receipts. */
public final class BlindReviewReceiptVerifier {
    public static final String CONTRACT = "ONSURE_ORUDA_BLIND_REVIEW_RECEIPT_V1";
    public static final String FILE_NAME = "blind-review-receipt.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public ValidationResult verify(Path file, String expectedTargetId, String expectedJobId,
            Set<String> requiredFixtureIds) {
        List<String> violations = new ArrayList<>();
        try {
            if (!Files.isRegularFile(file)) return ValidationResult.fail(List.of("ORUDA_BLIND_REVIEW_RECEIPT_MISSING"));
            Map<String, Object> value = MAPPER.readValue(file.toFile(), new TypeReference<>() {});
            Object storedDigest = value.remove("receipt_sha256");
            if (!CONTRACT.equals(value.get("contract"))) violations.add("ORUDA_BLIND_REVIEW_CONTRACT_MISMATCH");
            if (!Objects.equals(expectedTargetId, value.get("target_id"))) violations.add("ORUDA_BLIND_REVIEW_TARGET_MISMATCH");
            if (!Objects.equals(expectedJobId, value.get("job_id"))) violations.add("ORUDA_BLIND_REVIEW_JOB_MISMATCH");
            if (!"HUMAN_INDEPENDENT_REVIEWER".equals(value.get("reviewer_authority"))) {
                violations.add("ORUDA_BLIND_REVIEW_AUTHORITY_INVALID");
            }
            if (!(value.get("reviewer_id") instanceof String reviewer) || reviewer.isBlank()) {
                violations.add("ORUDA_BLIND_REVIEWER_ID_MISSING");
            }
            if (!"PASS".equals(value.get("decision"))) violations.add("ORUDA_BLIND_REVIEW_NON_PASS");
            try { Instant.parse(String.valueOf(value.get("reviewed_at"))); }
            catch (Exception e) { violations.add("ORUDA_BLIND_REVIEW_TIME_INVALID"); }

            Set<String> reviewed = new HashSet<>();
            Object fixtureValue = value.get("fixture_ids");
            if (fixtureValue instanceof List<?> fixtures) {
                for (Object fixture : fixtures) {
                    if (!(fixture instanceof String id) || id.isBlank() || !reviewed.add(id)) {
                        violations.add("ORUDA_BLIND_REVIEW_FIXTURE_SET_INVALID");
                    }
                }
            } else {
                violations.add("ORUDA_BLIND_REVIEW_FIXTURE_SET_INVALID");
            }
            if (!reviewed.equals(Set.copyOf(requiredFixtureIds))) {
                violations.add("ORUDA_BLIND_REVIEW_FIXTURE_COVERAGE_MISMATCH");
            }
            String expectedDigest = digestBody(value);
            if (!(storedDigest instanceof String digest) || !digest.matches("[0-9a-f]{64}")
                    || !digest.equals(expectedDigest)) {
                violations.add("ORUDA_BLIND_REVIEW_RECEIPT_HASH_MISMATCH");
            }
        } catch (Exception e) {
            violations.add("ORUDA_BLIND_REVIEW_RECEIPT_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public static String digestBody(Map<String, Object> bodyWithoutDigest) throws Exception {
        byte[] canonical = MAPPER.writeValueAsBytes(new TreeMap<>(bodyWithoutDigest));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }
}
