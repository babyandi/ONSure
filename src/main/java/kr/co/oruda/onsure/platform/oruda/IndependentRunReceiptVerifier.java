package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

/** Verifies external proof that a run was performed by an independent execution operator. */
public final class IndependentRunReceiptVerifier {
    public static final String CONTRACT = "ONSURE_ORUDA_INDEPENDENT_RUN_RECEIPT_V1";
    public static final String FILE_NAME = "independent-run-receipt.json";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public record ReceiptData(String operatorId, String environmentDigest, String sourceDigest) {}
    public record Verification(ValidationResult result, ReceiptData receipt) {}

    public Verification verify(Path file, String targetId, String jobId, String sourceDigest) {
        List<String> violations = new ArrayList<>();
        ReceiptData receipt = null;
        try {
            if (!Files.isRegularFile(file)) {
                return new Verification(
                        ValidationResult.fail(List.of("ORUDA_INDEPENDENT_RUN_RECEIPT_MISSING")), null);
            }
            Map<String, Object> value = MAPPER.readValue(file.toFile(), new TypeReference<>() {});
            Object storedDigest = value.remove("receipt_sha256");
            if (!CONTRACT.equals(value.get("contract"))) violations.add("ORUDA_INDEPENDENT_RUN_CONTRACT_MISMATCH");
            if (!Objects.equals(targetId, value.get("target_id"))) violations.add("ORUDA_INDEPENDENT_RUN_TARGET_MISMATCH");
            if (!Objects.equals(jobId, value.get("job_id"))) violations.add("ORUDA_INDEPENDENT_RUN_JOB_MISMATCH");
            if (!Objects.equals(sourceDigest, value.get("source_digest"))) violations.add("ORUDA_INDEPENDENT_RUN_SOURCE_MISMATCH");
            if (!"INDEPENDENT_EXECUTION_OPERATOR".equals(value.get("operator_authority"))) {
                violations.add("ORUDA_INDEPENDENT_RUN_AUTHORITY_INVALID");
            }
            if (!"PASS".equals(value.get("decision"))) violations.add("ORUDA_INDEPENDENT_RUN_NON_PASS");
            try { Instant.parse(String.valueOf(value.get("executed_at"))); }
            catch (Exception e) { violations.add("ORUDA_INDEPENDENT_RUN_TIME_INVALID"); }

            String operatorId = string(value.get("operator_id"));
            String environmentDigest = string(value.get("environment_digest"));
            String declaredSource = string(value.get("source_digest"));
            if (operatorId.isBlank()) violations.add("ORUDA_INDEPENDENT_OPERATOR_ID_MISSING");
            if (!environmentDigest.matches("[0-9a-f]{64}")) violations.add("ORUDA_INDEPENDENT_ENVIRONMENT_DIGEST_INVALID");
            if (!declaredSource.matches("[0-9a-f]{64}")) violations.add("ORUDA_INDEPENDENT_SOURCE_DIGEST_INVALID");
            String expectedDigest = digestBody(value);
            if (!(storedDigest instanceof String digest) || !digest.matches("[0-9a-f]{64}")
                    || !digest.equals(expectedDigest)) {
                violations.add("ORUDA_INDEPENDENT_RUN_RECEIPT_HASH_MISMATCH");
            }
            if (violations.isEmpty()) receipt = new ReceiptData(operatorId, environmentDigest, declaredSource);
        } catch (Exception e) {
            violations.add("ORUDA_INDEPENDENT_RUN_RECEIPT_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return new Verification(
                violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations), receipt);
    }

    public static String digestBody(Map<String, Object> bodyWithoutDigest) throws Exception {
        byte[] canonical = MAPPER.writeValueAsBytes(new TreeMap<>(bodyWithoutDigest));
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(canonical));
    }

    private static String string(Object value) { return value == null ? "" : value.toString(); }
}
