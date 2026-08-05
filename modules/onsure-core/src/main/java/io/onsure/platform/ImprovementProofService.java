package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FindingStatus;
import io.onsure.platform.ValidationModel.Severity;
import io.onsure.platform.ValidationModel.ValidationReport;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/** Produces a source-bound before/after proof before Git commit is allowed. */
public final class ImprovementProofService {
    public static final String CONTRACT = "ONSURE_IMPROVEMENT_PROOF_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> prove(
            Path baselineReportFile,
            Path currentReportFile,
            Path patchApplyReceiptFile,
            Path outputFile) throws Exception {
        requireRegular(baselineReportFile, "BASELINE_REPORT_FILE_INVALID");
        requireRegular(currentReportFile, "CURRENT_REPORT_FILE_INVALID");
        JsonNode baselineNode = mapper.readTree(baselineReportFile.toFile());
        JsonNode currentNode = mapper.readTree(currentReportFile.toFile());
        if (LocalProgramManagementService.CONTRACT.equals(baselineNode.path("contract").asText())
                || LocalProgramManagementService.CONTRACT.equals(currentNode.path("contract").asText())) {
            return proveScorecardReports(
                    baselineReportFile, baselineNode, currentReportFile, currentNode,
                    patchApplyReceiptFile, outputFile);
        }
        ValidationReport baseline = readReport(baselineReportFile, "BASELINE_REPORT");
        ValidationReport current = readReport(currentReportFile, "CURRENT_REPORT");
        Map<String, Object> patch = readMap(patchApplyReceiptFile, "PATCH_APPLY_RECEIPT");
        if (!ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT.equals(patch.get("contract"))) {
            throw new IllegalArgumentException("PATCH_APPLY_RECEIPT_CONTRACT_MISMATCH");
        }
        if (!baseline.target().targetId().equals(current.target().targetId())) {
            throw new IllegalStateException("IMPROVEMENT_TARGET_MISMATCH");
        }
        String baselineContext = contextDigest(baseline);
        String currentContext = contextDigest(current);
        boolean contextSame = baselineContext.equals(currentContext);
        boolean sourceChanged = baseline.regressionLock() != null && current.regressionLock() != null
                && !baseline.regressionLock().sourceDigest().equals(current.regressionLock().sourceDigest());
        Set<String> before = openFingerprints(baseline);
        Set<String> after = openFingerprints(current);
        List<String> resolved = before.stream().filter(value -> !after.contains(value)).sorted().toList();
        List<String> introduced = after.stream().filter(value -> !before.contains(value)).sorted().toList();
        List<String> unchanged = after.stream().filter(before::contains).sorted().toList();
        long newBlocking = current.findings().stream()
                .filter(value -> value.status() == FindingStatus.OPEN)
                .filter(value -> !before.contains(value.fingerprint()))
                .filter(value -> value.severity() == Severity.CRITICAL || value.severity() == Severity.HIGH)
                .count();
        boolean fixturesPass = !current.fixtureResults().isEmpty()
                && current.fixtureResults().stream().allMatch(value -> value.decision() == Decision.PASS);
        boolean stagesNoFail = current.stages().stream().noneMatch(value -> value.decision() == Decision.FAIL);
        boolean currentPass = current.decision() == Decision.PASS;

        String decision;
        List<String> reasons = new ArrayList<>();
        if (!contextSame) {
            decision = "INCONCLUSIVE";
            reasons.add("VALIDATION_CONTEXT_DRIFT");
        } else if (!sourceChanged) {
            decision = "NO_MEANINGFUL_IMPROVEMENT";
            reasons.add("SOURCE_NOT_CHANGED");
        } else if (newBlocking > 0 || !introduced.isEmpty() || !fixturesPass || !stagesNoFail) {
            decision = "REGRESSION_DETECTED";
            if (newBlocking > 0) reasons.add("NEW_BLOCKING_FINDING");
            if (!introduced.isEmpty()) reasons.add("NEW_FINDINGS_PRESENT");
            if (!fixturesPass) reasons.add("FIXTURE_REGRESSION");
            if (!stagesNoFail) reasons.add("STAGE_FAILURE_PRESENT");
        } else if (resolved.isEmpty() || !currentPass) {
            decision = "NO_MEANINGFUL_IMPROVEMENT";
            if (resolved.isEmpty()) reasons.add("NO_FINDING_RESOLVED");
            if (!currentPass) reasons.add("CURRENT_VALIDATION_NOT_PASS");
        } else {
            decision = "IMPROVEMENT_PROVEN";
            reasons.add("SAME_CONTEXT_SOURCE_CHANGED_FINDINGS_RESOLVED_REGRESSION_CLEAN");
        }

        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("contract", CONTRACT);
        proof.put("proof_id", "PROOF-" + current.jobId());
        proof.put("target_id", current.target().targetId());
        proof.put("patch_apply_receipt_sha256", fileSha(patchApplyReceiptFile));
        proof.put("baseline_report_sha256", fileSha(baselineReportFile));
        proof.put("current_report_sha256", fileSha(currentReportFile));
        proof.put("baseline_job_id", baseline.jobId());
        proof.put("current_job_id", current.jobId());
        proof.put("baseline_context_sha256", baselineContext);
        proof.put("current_context_sha256", currentContext);
        proof.put("context_same", contextSame);
        proof.put("source_changed", sourceChanged);
        proof.put("resolved_finding_fingerprints", resolved);
        proof.put("new_finding_fingerprints", introduced);
        proof.put("unchanged_finding_fingerprints", unchanged);
        proof.put("focused_fixture_validation", fixturesPass ? "PASS" : "FAIL");
        proof.put("full_regression", stagesNoFail && currentPass ? "PASS" : "FAIL");
        proof.put("decision", decision);
        proof.put("reasons", List.copyOf(reasons));
        proof.put("independent_otester", "NOT_RUN");
        proof.put("independent_oaudit", "NOT_RUN");
        proof.put("commit_allowed", "IMPROVEMENT_PROVEN".equals(decision));
        proof.put("created_at", Instant.now().toString());
        proof.put("final_claim_allowed", false);
        proof.put("proof_sha256", sha256(mapper.writeValueAsBytes(proof)));
        writeAtomic(outputFile, proof);
        return Map.copyOf(proof);
    }

    private Map<String, Object> proveScorecardReports(
            Path baselineFile, JsonNode baseline, Path currentFile, JsonNode current,
            Path patchFile, Path outputFile) throws Exception {
        if (!LocalProgramManagementService.CONTRACT.equals(baseline.path("contract").asText())
                || !LocalProgramManagementService.CONTRACT.equals(current.path("contract").asText())) {
            throw new IllegalArgumentException("IMPROVEMENT_REPORT_CONTRACT_MISMATCH");
        }
        Map<String, Object> patch = readMap(patchFile, "PATCH_APPLY_RECEIPT");
        if (!ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT.equals(patch.get("contract"))) {
            throw new IllegalArgumentException("PATCH_APPLY_RECEIPT_CONTRACT_MISMATCH");
        }
        requireSame(baseline, current, "projectId", "IMPROVEMENT_PROJECT_MISMATCH");
        requireSame(baseline, current, "targetId", "IMPROVEMENT_TARGET_MISMATCH");
        String baselineSource = digest(baseline, "sourceDigestBefore");
        String currentSource = digest(current, "sourceDigestBefore");
        if (!baselineSource.equals(String.valueOf(patch.get("source_tree_sha256")))
                || !currentSource.equals(String.valueOf(patch.get("postimage_source_tree_sha256")))) {
            throw new IllegalStateException("IMPROVEMENT_PATCH_SOURCE_LINEAGE_MISMATCH");
        }
        String baselineContext = scoreContextDigest(baseline);
        String currentContext = scoreContextDigest(current);
        boolean contextSame = baselineContext.equals(currentContext);
        boolean sourceChanged = !baselineSource.equals(currentSource);
        Map<String, Object> comparison = ValidationScorecardComparison.compare(
                baseline.path("jobId").asText(), baseline.path("scorecard"),
                current.path("jobId").asText(), current.path("scorecard"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> changes = (List<Map<String, Object>>) comparison.get("changes");
        boolean noRegression = changes.stream().noneMatch(value -> "REGRESSED".equals(value.get("state")));
        boolean scoreImproved = "IMPROVED".equals(comparison.get("state"));
        boolean currentPass = "PASS_NONFINAL".equals(current.path("decision").asText());
        List<String> before = findingFingerprints(baseline.path("findings"));
        List<String> after = findingFingerprints(current.path("findings"));
        List<String> resolved = before.stream().filter(value -> !after.contains(value)).sorted().toList();
        List<String> introduced = after.stream().filter(value -> !before.contains(value)).sorted().toList();
        List<String> unchanged = after.stream().filter(before::contains).sorted().toList();
        String decision = contextSame && sourceChanged && scoreImproved && noRegression
                && introduced.isEmpty() && currentPass ? "IMPROVEMENT_PROVEN"
                : !noRegression || !introduced.isEmpty() ? "REGRESSION_DETECTED"
                : "NO_MEANINGFUL_IMPROVEMENT";
        List<String> reasons = "IMPROVEMENT_PROVEN".equals(decision)
                ? List.of("SAME_CONTEXT_APPROVED_PATCH_SCORE_IMPROVED_REGRESSION_CLEAN")
                : List.of(!contextSame ? "VALIDATION_CONTEXT_DRIFT"
                        : !sourceChanged ? "SOURCE_NOT_CHANGED"
                        : !noRegression || !introduced.isEmpty() ? "REGRESSION_PRESENT"
                        : "SCORE_NOT_IMPROVED");
        Map<String, Object> proof = new LinkedHashMap<>();
        proof.put("contract", CONTRACT);
        proof.put("proof_id", "PROOF-" + current.path("jobId").asText());
        proof.put("target_id", current.path("targetId").asText());
        proof.put("patch_apply_receipt_sha256", fileSha(patchFile));
        proof.put("baseline_report_sha256", fileSha(baselineFile));
        proof.put("current_report_sha256", fileSha(currentFile));
        proof.put("baseline_job_id", baseline.path("jobId").asText());
        proof.put("current_job_id", current.path("jobId").asText());
        proof.put("baseline_context_sha256", baselineContext);
        proof.put("current_context_sha256", currentContext);
        proof.put("context_same", contextSame);
        proof.put("source_changed", sourceChanged);
        proof.put("resolved_finding_fingerprints", resolved);
        proof.put("new_finding_fingerprints", introduced);
        proof.put("unchanged_finding_fingerprints", unchanged);
        proof.put("focused_fixture_validation", noRegression ? "PASS" : "FAIL");
        proof.put("full_regression", noRegression && currentPass ? "PASS" : "FAIL");
        proof.put("decision", decision);
        proof.put("reasons", reasons);
        proof.put("independent_otester", "NOT_RUN");
        proof.put("independent_oaudit", "NOT_RUN");
        proof.put("commit_allowed", "IMPROVEMENT_PROVEN".equals(decision));
        proof.put("created_at", Instant.now().toString());
        proof.put("final_claim_allowed", false);
        proof.put("proof_sha256", sha256(mapper.writeValueAsBytes(proof)));
        writeAtomic(outputFile, proof);
        return Map.copyOf(proof);
    }

    private String scoreContextDigest(JsonNode report) throws Exception {
        return sha256(mapper.writeValueAsBytes(Map.of(
                "project_id", report.path("projectId").asText(),
                "target_id", report.path("targetId").asText(),
                "target_type", report.path("targetType").asText(),
                "profile", report.path("profile").asText(),
                "max_points", report.path("scorecard").path("max_points").decimalValue())));
    }

    private static void requireSame(JsonNode baseline, JsonNode current, String field, String error) {
        if (baseline.path(field).asText().isBlank()
                || !baseline.path(field).asText().equals(current.path(field).asText())) {
            throw new IllegalStateException(error);
        }
    }

    private static String digest(JsonNode report, String field) {
        String value = report.path(field).asText();
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(field + "_INVALID");
        return value;
    }

    private static List<String> findingFingerprints(JsonNode findings) {
        if (!findings.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        findings.forEach(value -> values.add(Hashing.sha256(value.toString())));
        return values.stream().distinct().sorted().toList();
    }

    private ValidationReport readReport(Path file, String label) throws Exception {
        requireRegular(file, label + "_FILE_INVALID");
        return mapper.readValue(file.toFile(), ValidationReport.class);
    }

    private Map<String, Object> readMap(Path file, String label) throws Exception {
        requireRegular(file, label + "_FILE_INVALID");
        return mapper.readValue(file.toFile(), new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private static void requireRegular(Path file, String error) {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException(error);
        }
    }

    private String contextDigest(ValidationReport report) throws Exception {
        Map<String, Object> context = new TreeMap<>();
        context.put("target_type", report.target().targetType().name());
        context.put("adapter_id", report.target().adapterId());
        context.put("policy_profile", report.target().policyProfile());
        context.put("execution_profile", report.target().executionProfile());
        context.put("fixtures", report.fixtureResults().stream()
                .sorted(Comparator.comparing(ValidationModel.FixtureResult::fixtureId))
                .map(value -> Map.of(
                        "fixture_id", value.fixtureId(),
                        "harness_id", value.harnessId(),
                        "oracle_id", value.oracleId(),
                        "expected", value.expected()))
                .toList());
        context.put("stage_ids", report.stages().stream()
                .map(ValidationModel.StageResult::stageId).sorted().toList());
        return sha256(mapper.writeValueAsBytes(context));
    }

    private static Set<String> openFingerprints(ValidationReport report) {
        return report.findings().stream()
                .filter(value -> value.status() == FindingStatus.OPEN)
                .map(Finding::fingerprint)
                .collect(Collectors.toSet());
    }

    private void writeAtomic(Path outputFile, Object value) throws Exception {
        Path output = outputFile.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String fileSha(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }
}
