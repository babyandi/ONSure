package io.onsure.platform;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.ConsumedApprovalReceiptVerifier;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** Binds an approved patch and successful before/after proof to authoritative score runs. */
public final class ValidationImprovementLineageService {
    public static final String CONTRACT = "ONSURE_IMPROVEMENT_LINEAGE_V1";
    private static final long MAX_JSON_BYTES = 16L * 1024L * 1024L;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> bind(
            Path baselineReportFile, Path currentReportFile, Path patchApplyReceiptFile,
            Path improvementProofFile, Path hunkApprovalReceiptFile,
            Path trustedKeyRegistry, Path replayLedger,
            Path outputFile, Map<String, String> environment) throws Exception {
        JsonNode patch = read(patchApplyReceiptFile, "PATCH_APPLY_RECEIPT");
        requireEqual(sha256(Files.readAllBytes(hunkApprovalReceiptFile)),
                patch.path("approval_receipt_sha256").asText(), "PATCH_APPROVAL_RECEIPT_MISMATCH");
        ConsumedApprovalReceiptVerifier.requireTrustedConsumed(
                hunkApprovalReceiptFile, trustedKeyRegistry, replayLedger,
                ImprovementWorkflowService.APPROVAL_CONTRACT,
                ImprovementWorkflowService.APPROVAL_PURPOSE, Instant.now(),
                "PATCH_CONSUMED_APPROVAL_INVALID");
        return bindValidated(baselineReportFile, currentReportFile, patchApplyReceiptFile,
                improvementProofFile, outputFile, environment, true);
    }

    Map<String, Object> bindValidated(
            Path baselineReportFile, Path currentReportFile, Path patchApplyReceiptFile,
            Path improvementProofFile, Path outputFile, Map<String, String> environment,
            boolean trustedConsumedApproval) throws Exception {
        if (!trustedConsumedApproval) throw new SecurityException("TRUSTED_CONSUMED_PATCH_APPROVAL_REQUIRED");
        JsonNode baseline = read(baselineReportFile, "BASELINE_REPORT");
        JsonNode current = read(currentReportFile, "CURRENT_REPORT");
        JsonNode patch = read(patchApplyReceiptFile, "PATCH_APPLY_RECEIPT");
        JsonNode proof = read(improvementProofFile, "IMPROVEMENT_PROOF");
        requireContract(baseline, LocalProgramManagementService.CONTRACT, "BASELINE_REPORT");
        requireContract(current, LocalProgramManagementService.CONTRACT, "CURRENT_REPORT");
        requireContract(patch, ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT, "PATCH_APPLY_RECEIPT");
        requireContract(proof, ImprovementProofService.CONTRACT, "IMPROVEMENT_PROOF");
        requireCanonicalDigest(patch, "receipt_sha256", "PATCH_APPLY_RECEIPT_DIGEST_INVALID");
        requireCanonicalDigest(proof, "proof_sha256", "IMPROVEMENT_PROOF_DIGEST_INVALID");
        if (!"IMPROVEMENT_PROVEN".equals(proof.path("decision").asText())
                || !proof.path("commit_allowed").asBoolean(false)
                || !"PASS".equals(proof.path("focused_fixture_validation").asText())
                || !"PASS".equals(proof.path("full_regression").asText())
                || !proof.path("context_same").asBoolean(false)
                || !proof.path("source_changed").asBoolean(false)) {
            throw new IllegalStateException("IMPROVEMENT_PROOF_NOT_APPROVED_FOR_COMPARISON");
        }
        String baselineRun = text(baseline, "jobId", "BASELINE_RUN_ID");
        String currentRun = text(current, "jobId", "CURRENT_RUN_ID");
        String projectId = text(current, "projectId", "PROJECT_ID");
        String targetId = text(current, "targetId", "TARGET_ID");
        requireEqual(projectId, text(baseline, "projectId", "BASELINE_PROJECT_ID"), "PROJECT_MISMATCH");
        requireEqual(targetId, text(baseline, "targetId", "BASELINE_TARGET_ID"), "TARGET_MISMATCH");
        requireEqual(targetId, proof.path("target_id").asText(), "PROOF_TARGET_MISMATCH");
        requireEqual(baselineRun, proof.path("baseline_job_id").asText(), "PROOF_BASELINE_RUN_MISMATCH");
        requireEqual(currentRun, proof.path("current_job_id").asText(), "PROOF_CURRENT_RUN_MISMATCH");
        requireEqual(sha256(Files.readAllBytes(baselineReportFile)),
                proof.path("baseline_report_sha256").asText(), "PROOF_BASELINE_REPORT_MISMATCH");
        requireEqual(sha256(Files.readAllBytes(currentReportFile)),
                proof.path("current_report_sha256").asText(), "PROOF_CURRENT_REPORT_MISMATCH");
        String patchDigest = sha256(Files.readAllBytes(patchApplyReceiptFile));
        requireEqual(patchDigest, proof.path("patch_apply_receipt_sha256").asText(),
                "PROOF_PATCH_RECEIPT_MISMATCH");
        String baselineSource = digest(baseline, "sourceDigestBefore", "BASELINE_SOURCE");
        String currentSource = digest(current, "sourceDigestBefore", "CURRENT_SOURCE");
        requireEqual(baselineSource, patch.path("source_tree_sha256").asText(),
                "PATCH_BASELINE_SOURCE_MISMATCH");
        requireEqual(currentSource, patch.path("postimage_source_tree_sha256").asText(),
                "PATCH_CURRENT_SOURCE_MISMATCH");
        if (patch.path("approval_receipt_sha256").asText().isBlank()
                || patch.path("approval_actor").asText().isBlank()
                || patch.path("approval_key_id").asText().isBlank()) {
            throw new IllegalStateException("PATCH_APPROVAL_IDENTITY_MISSING");
        }
        String proofDigest = sha256(Files.readAllBytes(improvementProofFile));
        PostgresqlValidationScoreStore.ChangeLineage lineage =
                new PostgresqlValidationScoreStore.ChangeLineage(
                        true, baselineRun, currentRun, baselineSource, currentSource,
                        patchDigest, proofDigest);
        Map<String, Object> value = new LinkedHashMap<>(lineage.asMap());
        value.put("project_id", projectId);
        value.put("target_id", targetId);
        value.put("baseline_report_sha256", sha256(Files.readAllBytes(baselineReportFile)));
        value.put("current_report_sha256", sha256(Files.readAllBytes(currentReportFile)));
        value.put("patch_approval_receipt_sha256", patch.path("approval_receipt_sha256").asText());
        value.put("patch_approval_actor", patch.path("approval_actor").asText());
        value.put("patch_approval_key_id", patch.path("approval_key_id").asText());
        value.put("final_claim_allowed", false);
        Map<String, Object> stored = new PostgresqlValidationScoreStore(environment)
                .authorizeImprovementComparison(projectId, targetId, lineage);
        value.put("score_store_state", stored.getOrDefault("state", "AUTHORITATIVE_VERIFIED"));
        value.put("lineage_sha256", canonicalDigest(value, "lineage_sha256"));
        writeAtomic(outputFile, value);
        return Map.copyOf(value);
    }

    private JsonNode read(Path file, String label) throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)
                || Files.size(file) > MAX_JSON_BYTES)
            throw new IllegalArgumentException(label + "_FILE_INVALID");
        return mapper.readTree(file.toFile());
    }

    private static void requireContract(JsonNode node, String contract, String label) {
        if (!contract.equals(node.path("contract").asText()))
            throw new IllegalArgumentException(label + "_CONTRACT_MISMATCH");
    }

    private void requireCanonicalDigest(JsonNode node, String field, String error) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> value = mapper.convertValue(node, Map.class);
        if (!canonicalDigest(value, field).equals(node.path(field).asText()))
            throw new IllegalStateException(error);
    }

    private String canonicalDigest(Map<String, Object> value, String field) throws Exception {
        Map<String, Object> copy = new java.util.TreeMap<>(value);
        copy.remove(field);
        return sha256(mapper.writeValueAsBytes(copy));
    }

    private static String text(JsonNode node, String field, String label) {
        String value = node.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException(label + "_INVALID");
        return value;
    }

    private static String digest(JsonNode node, String field, String label) {
        String value = text(node, field, label);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(label + "_INVALID");
        return value;
    }

    private static void requireEqual(String expected, String actual, String error) {
        if (!expected.equals(actual)) throw new IllegalStateException(error);
    }

    private void writeAtomic(Path file, Object value) throws Exception {
        Path output = file.toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output))
            throw new IllegalArgumentException("IMPROVEMENT_LINEAGE_OUTPUT_SYMLINK_FORBIDDEN");
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, output,
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
