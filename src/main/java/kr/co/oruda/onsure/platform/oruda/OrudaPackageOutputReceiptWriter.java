package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Creates a package output receipt only from an already-written canonical evidence artifact. */
public final class OrudaPackageOutputReceiptWriter {
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Path write(Path runRoot, String packageId, String outputId, String targetId,
            String jobId, String decision) throws Exception {
        if (!java.util.Set.of("PASS", "FAIL", "BLOCKED", "NOT_RUN").contains(decision)) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_OUTPUT_DECISION_INVALID");
        }
        Path normalizedRun = runRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRun)) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_RUN_ROOT_MISSING");
        }
        Path evidenceFile = OrudaPackageOutputReceiptVerifier.expectedEvidencePath(
                normalizedRun, packageId, outputId);
        if (!Files.isRegularFile(evidenceFile) || Files.isSymbolicLink(evidenceFile)) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_OUTPUT_EVIDENCE_MISSING");
        }
        JsonNode evidence = mapper.readTree(evidenceFile.toFile());
        if (!"ONSURE_ORUDA_PACKAGE_OUTPUT_EVIDENCE_V1".equals(evidence.path("contract").asText())) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_OUTPUT_EVIDENCE_CONTRACT_MISMATCH");
        }
        JsonNode semantic = evidence.path("semantic_payload");
        if (!packageId.equals(semantic.path("package_id").asText())
                || !outputId.equals(semantic.path("output_id").asText())
                || !targetId.equals(semantic.path("target_id").asText())
                || !decision.equals(semantic.path("decision").asText())) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_OUTPUT_SEMANTIC_IDENTITY_MISMATCH");
        }

        Path receiptFile = OrudaPackageExecutionRegistry.expectedOutputPath(
                normalizedRun, packageId, outputId);
        Files.createDirectories(receiptFile.getParent());
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", OrudaPackageOutputReceiptVerifier.CONTRACT);
        receipt.put("package_id", packageId);
        receipt.put("output_id", outputId);
        receipt.put("target_id", targetId);
        receipt.put("job_id", jobId);
        receipt.put("decision", decision);
        receipt.put("evidence_path", normalizedRun.relativize(evidenceFile).toString().replace('\\', '/'));
        receipt.put("evidence_sha256", OrudaPackageOutputReceiptVerifier.sha256(evidenceFile));
        receipt.put("semantic_digest", OrudaPackageOutputReceiptVerifier.semanticDigest(semantic));
        receipt.put("produced_at", Instant.now().toString());
        receipt.put("receipt_sha256", OrudaPackageOutputReceiptVerifier.digestBody(receipt));

        Path temporary = receiptFile.resolveSibling(receiptFile.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), receipt);
        try {
            Files.move(temporary, receiptFile,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, receiptFile, StandardCopyOption.REPLACE_EXISTING);
        }

        ValidationResult verification = new OrudaPackageOutputReceiptVerifier().verify(
                normalizedRun, receiptFile, targetId, jobId, packageId, outputId).result();
        if (verification.decision() != Decision.PASS) {
            Files.deleteIfExists(receiptFile);
            throw new IllegalStateException("ORUDA_PACKAGE_OUTPUT_RECEIPT_VERIFY_FAIL "
                    + verification.violations());
        }
        return receiptFile;
    }
}
