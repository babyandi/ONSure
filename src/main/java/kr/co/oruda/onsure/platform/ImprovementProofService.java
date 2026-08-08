package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.FindingStatus;
import kr.co.oruda.onsure.platform.ValidationModel.Severity;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationReport;
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