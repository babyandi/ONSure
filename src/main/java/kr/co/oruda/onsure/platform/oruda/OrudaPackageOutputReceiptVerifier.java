package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
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

/** Recalculates a package output receipt, referenced evidence bytes and normalized semantic digest. */
public final class OrudaPackageOutputReceiptVerifier {
    public static final String CONTRACT = "ONSURE_ORUDA_PACKAGE_OUTPUT_RECEIPT_V1";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public record VerifiedReceipt(
            String decision,
            String evidencePath,
            String evidenceSha256,
            String semanticDigest,
            String receiptSha256) {}
    public record Verification(ValidationResult result, VerifiedReceipt receipt) {}

    public Verification verify(Path runRoot, Path receiptFile, String targetId, String jobId,
            String packageId, String outputId) {
        List<String> violations = new ArrayList<>();
        VerifiedReceipt verified = null;
        try {
            Path normalizedRun = runRoot.toAbsolutePath().normalize();
            if (!Files.isRegularFile(receiptFile) || Files.isSymbolicLink(receiptFile)) {
                return new Verification(ValidationResult.fail(List.of(
                        "ORUDA_PACKAGE_OUTPUT_RECEIPT_MISSING:" + packageId + ":" + outputId)), null);
            }
            Map<String, Object> body = MAPPER.readValue(receiptFile.toFile(), new TypeReference<>() {});
            Object storedReceiptDigest = body.remove("receipt_sha256");
            if (!CONTRACT.equals(body.get("contract"))) violations.add("ORUDA_PACKAGE_OUTPUT_CONTRACT_MISMATCH");
            if (!Objects.equals(packageId, body.get("package_id"))) violations.add("ORUDA_PACKAGE_OUTPUT_PACKAGE_MISMATCH");
            if (!Objects.equals(outputId, body.get("output_id"))) violations.add("ORUDA_PACKAGE_OUTPUT_ID_MISMATCH");
            if (!Objects.equals(targetId, body.get("target_id"))) violations.add("ORUDA_PACKAGE_OUTPUT_TARGET_MISMATCH");
            if (!Objects.equals(jobId, body.get("job_id"))) violations.add("ORUDA_PACKAGE_OUTPUT_JOB_MISMATCH");
            String decision = text(body.get("decision"));
            if (!List.of("PASS", "FAIL", "BLOCKED", "NOT_RUN").contains(decision)) {
                violations.add("ORUDA_PACKAGE_OUTPUT_DECISION_INVALID");
            }
            try { Instant.parse(text(body.get("produced_at"))); }
            catch (Exception e) { violations.add("ORUDA_PACKAGE_OUTPUT_TIME_INVALID"); }

            Path expectedEvidence = expectedEvidencePath(normalizedRun, packageId, outputId);
            Path declaredEvidence = normalizedRun.resolve(text(body.get("evidence_path"))).normalize();
            if (!declaredEvidence.equals(expectedEvidence) || !declaredEvidence.startsWith(normalizedRun)) {
                violations.add("ORUDA_PACKAGE_OUTPUT_EVIDENCE_PATH_MISMATCH");
            }
            String evidenceDigest = text(body.get("evidence_sha256"));
            String semanticDigest = text(body.get("semantic_digest"));
            if (!Files.isRegularFile(declaredEvidence) || Files.isSymbolicLink(declaredEvidence)) {
                violations.add("ORUDA_PACKAGE_OUTPUT_EVIDENCE_MISSING");
            } else {
                String actualEvidenceDigest = sha256(Files.readAllBytes(declaredEvidence));
                if (!Objects.equals(evidenceDigest, actualEvidenceDigest)) {
                    violations.add("ORUDA_PACKAGE_OUTPUT_EVIDENCE_HASH_MISMATCH");
                }
                JsonNode evidence = MAPPER.readTree(declaredEvidence.toFile());
                JsonNode semanticPayload = evidence.path("semantic_payload");
                if (semanticPayload.isMissingNode() || semanticPayload.isNull()) {
                    violations.add("ORUDA_PACKAGE_OUTPUT_SEMANTIC_PAYLOAD_MISSING");
                } else {
                    String actualSemanticDigest = sha256(MAPPER.writeValueAsBytes(semanticPayload));
                    if (!Objects.equals(semanticDigest, actualSemanticDigest)) {
                        violations.add("ORUDA_PACKAGE_OUTPUT_SEMANTIC_DIGEST_MISMATCH");
                    }
                }
            }
            String expectedReceiptDigest = digestBody(body);
            if (!(storedReceiptDigest instanceof String receiptDigest)
                    || !receiptDigest.matches("[0-9a-f]{64}")
                    || !receiptDigest.equals(expectedReceiptDigest)) {
                violations.add("ORUDA_PACKAGE_OUTPUT_RECEIPT_HASH_MISMATCH");
            } else if (violations.isEmpty()) {
                verified = new VerifiedReceipt(
                        decision,
                        text(body.get("evidence_path")),
                        evidenceDigest,
                        semanticDigest,
                        receiptDigest);
            }
        } catch (Exception e) {
            violations.add("ORUDA_PACKAGE_OUTPUT_RECEIPT_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return new Verification(
                violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations), verified);
    }

    public static Path expectedEvidencePath(Path runRoot, String packageId, String outputId) {
        Path normalizedRun = runRoot.toAbsolutePath().normalize();
        Path file = normalizedRun.resolve(OrudaPackageExecutionRegistry.EVIDENCE_DIRECTORY)
                .resolve(packageId).resolve("artifacts").resolve(outputId + ".json").normalize();
        if (!file.startsWith(normalizedRun)) throw new IllegalArgumentException("ORUDA_PACKAGE_EVIDENCE_PATH_ESCAPE");
        return file;
    }

    public static String digestBody(Map<String, Object> bodyWithoutDigest) throws Exception {
        return sha256(MAPPER.writeValueAsBytes(new TreeMap<>(bodyWithoutDigest)));
    }

    public static String semanticDigest(Object semanticPayload) throws Exception {
        return sha256(MAPPER.writeValueAsBytes(semanticPayload));
    }

    public static String sha256(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }

    private static String text(Object value) { return value == null ? "" : value.toString(); }
}
