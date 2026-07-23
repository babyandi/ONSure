package io.onsure.platform.oruda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationEngine;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

final class OrudaCandidateTestSupport {
    static final Path TARGET_ROOT = Path.of("fixtures/oruda/mvf-001");
    static final Path PACKAGE_CATALOG = Path.of("contracts/oruda-execution-packages.v1.json");
    static final Set<String> QUALITY_FIXTURES = Set.of(
            "MVF-QUALITY-001", "MVF-QUALITY-002", "MVF-QUALITY-003");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private OrudaCandidateTestSupport() {}

    static void addCompleteCandidateEvidence(ValidationEngine.RunResult run, String operatorId,
            String reviewerId, String environmentDigest) throws Exception {
        sealAllPackages(run);
        writeBlindReviewReceipt(run, reviewerId);
        writeIndependentRunReceipt(run, operatorId, environmentDigest);
    }

    static void sealAllPackages(ValidationEngine.RunResult run) throws Exception {
        var catalog = new OrudaExecutionPackageCatalog().load(PACKAGE_CATALOG);
        for (var executionPackage : catalog.packages()) {
            for (String outputId : executionPackage.requiredOutputs()) {
                writePackageOutputReceipt(
                        run.runRoot(), run.report().target().targetId(), run.report().jobId(),
                        executionPackage.packageId(), outputId, "PASS");
            }
        }
        new OrudaPackageExecutionRegistry().seal(
                run.runRoot(), PACKAGE_CATALOG,
                run.report().target().targetId(), run.report().jobId());
    }

    static void writePackageOutputReceipt(Path runRoot, String targetId, String jobId,
            String packageId, String outputId, String decision) throws Exception {
        Map<String, Object> semanticPayload = new LinkedHashMap<>();
        semanticPayload.put("package_id", packageId);
        semanticPayload.put("output_id", outputId);
        semanticPayload.put("target_id", targetId);
        semanticPayload.put("decision", decision);

        Path evidenceFile = OrudaPackageOutputReceiptVerifier.expectedEvidencePath(
                runRoot, packageId, outputId);
        Files.createDirectories(evidenceFile.getParent());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("contract", "ONSURE_ORUDA_PACKAGE_OUTPUT_EVIDENCE_V1");
        evidence.put("semantic_payload", semanticPayload);
        evidence.put("run_context", Map.of("job_id", jobId));
        MAPPER.writeValue(evidenceFile.toFile(), evidence);

        Path receiptFile = OrudaPackageExecutionRegistry.expectedOutputPath(runRoot, packageId, outputId);
        Files.createDirectories(receiptFile.getParent());
        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", OrudaPackageOutputReceiptVerifier.CONTRACT);
        receipt.put("package_id", packageId);
        receipt.put("output_id", outputId);
        receipt.put("target_id", targetId);
        receipt.put("job_id", jobId);
        receipt.put("decision", decision);
        receipt.put("evidence_path", runRoot.toAbsolutePath().normalize()
                .relativize(evidenceFile.toAbsolutePath().normalize()).toString().replace('\\', '/'));
        receipt.put("evidence_sha256", OrudaPackageOutputReceiptVerifier.sha256(evidenceFile));
        receipt.put("semantic_digest", OrudaPackageOutputReceiptVerifier.semanticDigest(semanticPayload));
        receipt.put("produced_at", Instant.now().toString());
        receipt.put("receipt_sha256", OrudaPackageOutputReceiptVerifier.digestBody(receipt));
        MAPPER.writeValue(receiptFile.toFile(), receipt);
    }

    static void writeBlindReviewReceipt(ValidationEngine.RunResult run, String reviewerId) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", BlindReviewReceiptVerifier.CONTRACT);
        body.put("review_id", "BR-" + run.report().jobId());
        body.put("target_id", run.report().target().targetId());
        body.put("job_id", run.report().jobId());
        body.put("reviewer_id", reviewerId);
        body.put("reviewer_authority", "HUMAN_INDEPENDENT_REVIEWER");
        body.put("decision", "PASS");
        body.put("fixture_ids", new ArrayList<>(QUALITY_FIXTURES).stream().sorted().toList());
        body.put("reviewed_at", Instant.now().toString());
        body.put("receipt_sha256", BlindReviewReceiptVerifier.digestBody(body));
        MAPPER.writeValue(run.runRoot().resolve(BlindReviewReceiptVerifier.FILE_NAME).toFile(), body);
    }

    static void writeIndependentRunReceipt(ValidationEngine.RunResult run, String operatorId,
            String environmentDigest) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", IndependentRunReceiptVerifier.CONTRACT);
        body.put("run_receipt_id", "IRR-" + run.report().jobId());
        body.put("target_id", run.report().target().targetId());
        body.put("job_id", run.report().jobId());
        body.put("operator_id", operatorId);
        body.put("operator_authority", "INDEPENDENT_EXECUTION_OPERATOR");
        body.put("environment_digest", environmentDigest);
        body.put("source_digest", run.report().regressionLock().sourceDigest());
        body.put("decision", "PASS");
        body.put("executed_at", Instant.now().toString());
        body.put("receipt_sha256", IndependentRunReceiptVerifier.digestBody(body));
        MAPPER.writeValue(run.runRoot().resolve(IndependentRunReceiptVerifier.FILE_NAME).toFile(), body);
    }

    static void writeFinalApproval(FinalCandidateGate.GateResult candidate, Path file,
            String approverId) throws Exception {
        Files.createDirectories(file.getParent());
        Map<String, Object> approval = new LinkedHashMap<>();
        approval.put("contract", FinalApprovalReceiptVerifier.CONTRACT);
        approval.put("approval_id", "APPROVAL-" + candidate.candidateId());
        approval.put("target_id", candidate.targetId());
        approval.put("candidate_id", candidate.candidateId());
        approval.put("candidate_digest", candidate.candidateDigest());
        approval.put("approver_id", approverId);
        approval.put("approver_authority", "HUMAN_FINAL_AUTHORITY");
        approval.put("decision", "APPROVE");
        approval.put("approved_at", Instant.now().toString());
        approval.put("receipt_sha256", FinalApprovalReceiptVerifier.digestBody(approval));
        MAPPER.writeValue(file.toFile(), approval);
    }
}
