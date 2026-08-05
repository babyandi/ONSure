package io.onsure.platform;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Verifies that a validation receipt, report and evidence bind the same immutable target. */
final class TargetProvenanceRunVerifier {
    private static final long MAX_JSON_BYTES = 16L * 1024L * 1024L;
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);

    Verification verify(Path runRoot) {
        List<String> reasons = new ArrayList<>();
        Path root = runRoot == null ? null : runRoot.toAbsolutePath().normalize();
        if (root == null || !Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(root)) {
            return new Verification(false, List.of("RUN_ROOT_INVALID"), false);
        }
        try {
            Path receiptFile = safeJson(root, UniversalValidationRunner.RECEIPT_FILE);
            Path reportFile = safeJson(root, "validation-report.json");
            Path evidenceFile = safeJson(root, "evidence.json");
            JsonNode receipt = MAPPER.readTree(receiptFile.toFile());
            JsonNode report = MAPPER.readTree(reportFile.toFile());
            JsonNode evidence = MAPPER.readTree(evidenceFile.toFile());
            if (!UniversalValidationRunner.CONTRACT.equals(receipt.path("contract").asText())) {
                reasons.add("RECEIPT_CONTRACT_INVALID");
            }

            Map<String, Object> provenance = MAPPER.convertValue(
                    receipt.path("target_provenance"), new TypeReference<>() {});
            try {
                TargetProvenanceService.validate(provenance);
            } catch (RuntimeException invalid) {
                reasons.add(safeReason(invalid));
            }
            String provenanceSha = text(provenance.get("provenance_sha256"));
            String classification = text(provenance.get("target_classification"));
            String registrationSha = text(provenance.get("registration_source_sha256"));
            String snapshotSha = text(provenance.get("snapshot_source_sha256"));
            String manifestSha = text(provenance.get("snapshot_manifest_sha256"));
            JsonNode binding = receipt.path("target_provenance_binding");
            equal("BINDING_CONTRACT_INVALID", "ONSURE_TARGET_PROVENANCE_RUN_BINDING_V1",
                    binding.path("contract").asText(), reasons);
            equal("BINDING_PROVENANCE_MISMATCH", provenanceSha,
                    binding.path("provenance_sha256").asText(), reasons);
            equal("BINDING_SOURCE_MISMATCH", snapshotSha,
                    binding.path("source_sha256").asText(), reasons);
            equal("BINDING_SNAPSHOT_MISMATCH", snapshotSha,
                    binding.path("snapshot_sha256").asText(), reasons);
            equal("BINDING_MANIFEST_MISMATCH", manifestSha,
                    binding.path("snapshot_manifest_sha256").asText(), reasons);
            equal("RECEIPT_SOURCE_MISMATCH", snapshotSha,
                    receipt.path("source_digest").asText(), reasons);
            equal("RECEIPT_SNAPSHOT_MISMATCH", snapshotSha,
                    receipt.path("snapshot_digest").asText(), reasons);
            if (binding.path("final_claim_allowed").asBoolean(true)) {
                reasons.add("BINDING_FINAL_CLAIM_FORBIDDEN");
            }

            if (!report.path("targetProvenance").equals(receipt.path("target_provenance"))) {
                reasons.add("REPORT_RECEIPT_PROVENANCE_MISMATCH");
            }
            equal("REPORT_PROVENANCE_DIGEST_MISMATCH", provenanceSha,
                    report.path("targetProvenanceSha256").asText(), reasons);
            equal("REPORT_CLASSIFICATION_MISMATCH", classification,
                    report.path("targetClassification").asText(), reasons);
            equalNullable("REPORT_COMMIT_MISMATCH", provenance.get("repository_commit_sha"),
                    report.get("targetRepositoryCommit"), reasons);
            equal("REPORT_MANIFEST_MISMATCH", manifestSha,
                    report.path("targetSnapshotManifestSha256").asText(), reasons);
            equal("REPORT_REGISTRATION_SOURCE_MISMATCH", registrationSha,
                    report.path("sourceDigestBefore").asText(), reasons);
            equal("REPORT_SNAPSHOT_SOURCE_MISMATCH", snapshotSha,
                    report.path("snapshotSourceDigest").asText(), reasons);
            equal("REPORT_SNAPSHOT_MISMATCH", snapshotSha,
                    report.path("snapshotDigest").asText(), reasons);
            equal("REPORT_RECEIPT_SHA256_MISMATCH", Hashing.file(receiptFile),
                    report.path("universalReceiptSha256").asText(), reasons);
            if (report.path("provenanceAloneIsPassEvidence").asBoolean(true)) {
                reasons.add("REPORT_PROVENANCE_ALONE_PASS_FORBIDDEN");
            }

            JsonNode provenanceEvidence = null;
            int provenanceEvidenceCount = 0;
            if (evidence.isArray()) {
                for (JsonNode item : evidence) {
                    if ("target-provenance".equals(item.path("evidence_id").asText())) {
                        provenanceEvidence = item;
                        provenanceEvidenceCount++;
                    }
                }
            }
            if (provenanceEvidenceCount != 1) {
                reasons.add("TARGET_PROVENANCE_EVIDENCE_COUNT_INVALID");
            } else {
                equal("EVIDENCE_PROVENANCE_DIGEST_MISMATCH", provenanceSha,
                        provenanceEvidence.path("sha256").asText(), reasons);
                equal("EVIDENCE_CLASSIFICATION_MISMATCH", classification,
                        provenanceEvidence.path("target_classification").asText(), reasons);
                equal("EVIDENCE_MANIFEST_MISMATCH", manifestSha,
                        provenanceEvidence.path("snapshot_manifest_sha256").asText(), reasons);
                if (provenanceEvidence.path("provenance_alone_is_pass_evidence").asBoolean(true)) {
                    reasons.add("EVIDENCE_PROVENANCE_ALONE_PASS_FORBIDDEN");
                }
            }

            boolean eligible = Boolean.TRUE.equals(provenance.get("real_target_universality_eligible"))
                    && "REAL_REPOSITORY".equals(classification)
                    && "VERIFIED_BEFORE_AND_AFTER".equals(binding.path("state").asText())
                    && "PASS_NONFINAL".equals(receipt.path("overall_outcome").asText())
                    && "PASS_NONFINAL".equals(receipt.path("final_evidence_integrity")
                            .path("outcome").asText())
                    && !receipt.path("source_mutation_detected").asBoolean(true);
            boolean receiptEligible = receipt.path(
                    "real_target_universality_evidence_eligible").asBoolean(false);
            if (eligible != receiptEligible
                    || eligible != report.path("realTargetUniversalityEvidenceEligible").asBoolean(false)
                    || provenanceEvidence != null && eligible != provenanceEvidence.path(
                            "real_target_universality_evidence_eligible").asBoolean(false)) {
                reasons.add("REAL_TARGET_EVIDENCE_ELIGIBILITY_MISMATCH");
            }
            return new Verification(reasons.isEmpty(), List.copyOf(reasons), eligible);
        } catch (Exception invalid) {
            reasons.add(safeReason(invalid));
            return new Verification(false, List.copyOf(reasons), false);
        }
    }

    private static Path safeJson(Path root, String name) throws Exception {
        Path file = root.resolve(name).normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file) || Files.size(file) > MAX_JSON_BYTES) {
            throw new IllegalArgumentException("RUN_EVIDENCE_FILE_UNSAFE:" + name);
        }
        Path current = root;
        for (Path component : root.relativize(file)) {
            current = current.resolve(component);
            if (Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("RUN_EVIDENCE_SYMLINK_FORBIDDEN:" + name);
            }
        }
        return file;
    }

    private static void equal(String reason, String expected, String actual, List<String> reasons) {
        if (expected == null || !expected.equals(actual)) reasons.add(reason);
    }

    private static void equalNullable(
            String reason, Object expected, JsonNode actual, List<String> reasons) {
        if (expected == null ? actual == null || !actual.isNull() : actual == null
                || !expected.toString().equals(actual.asText())) reasons.add(reason);
    }

    private static String text(Object value) {
        return value == null ? null : value.toString();
    }

    private static String safeReason(Exception error) {
        String message = error.getMessage();
        return message != null && message.matches("[A-Z0-9_.:-]{1,240}")
                ? message : "TARGET_PROVENANCE_RUN_EVIDENCE_INVALID";
    }

    record Verification(boolean valid, List<String> reasons, boolean realTargetEvidenceEligible) {
        Verification {
            reasons = List.copyOf(reasons);
        }
    }
}
