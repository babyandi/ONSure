package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.FailureMode;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FindingStatus;
import io.onsure.platform.ValidationModel.FixtureResult;
import io.onsure.platform.ValidationModel.RcaRecord;
import io.onsure.platform.ValidationModel.RegressionLock;
import io.onsure.platform.ValidationModel.Severity;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationReport;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ImprovementProofServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sameContextResolvedFindingAndCleanRegressionProvesImprovement() throws Exception {
        Finding finding = finding("a".repeat(64), "CORRECTNESS", Severity.HIGH);
        Path baseline = report("baseline", "policy-v1", Decision.FAIL,
                List.of(finding), "b".repeat(64), false);
        Path current = report("current", "policy-v1", Decision.PASS,
                List.of(), "c".repeat(64), false);
        Path patch = patchReceipt();
        Map<String, Object> proof = new ImprovementProofService().prove(
                baseline, current, patch, temp.resolve("proof.json"));
        assertEquals("IMPROVEMENT_PROVEN", proof.get("decision"));
        assertEquals(true, proof.get("commit_allowed"));
    }

    @Test
    void policyContextDriftIsInconclusive() throws Exception {
        Path baseline = report("baseline-drift", "policy-v1", Decision.FAIL,
                List.of(finding("d".repeat(64), "CORRECTNESS", Severity.HIGH)),
                "e".repeat(64), false);
        Path current = report("current-drift", "policy-v2", Decision.PASS,
                List.of(), "f".repeat(64), false);
        Map<String, Object> proof = new ImprovementProofService().prove(
                baseline, current, patchReceipt(), temp.resolve("proof-drift.json"));
        assertEquals("INCONCLUSIVE", proof.get("decision"));
        assertEquals(false, proof.get("commit_allowed"));
    }

    @Test
    void newFindingOrFixtureFailureIsRegression() throws Exception {
        Path baseline = report("baseline-regression", "policy-v1", Decision.FAIL,
                List.of(finding("1".repeat(64), "CORRECTNESS", Severity.HIGH)),
                "2".repeat(64), false);
        Path current = report("current-regression", "policy-v1", Decision.FAIL,
                List.of(finding("3".repeat(64), "SECRET_EXPOSURE", Severity.CRITICAL)),
                "4".repeat(64), true);
        Map<String, Object> proof = new ImprovementProofService().prove(
                baseline, current, patchReceipt(), temp.resolve("proof-regression.json"));
        assertEquals("REGRESSION_DETECTED", proof.get("decision"));
        assertEquals(false, proof.get("commit_allowed"));
    }

    private Path report(
            String jobId,
            String policy,
            Decision decision,
            List<Finding> findings,
            String sourceDigest,
            boolean fixtureFail) throws Exception {
        Instant now = Instant.parse("2026-07-26T00:00:00Z");
        ValidationTarget target = new ValidationTarget(
                "target-001", "Target", TargetType.GENERAL_SOFTWARE, temp,
                "sha256:" + sourceDigest, GenericManifestTargetAdapter.ID,
                policy, "LOCAL_DEVELOPMENT");
        FixtureResult fixture = new FixtureResult(
                "fixture-001", "harness-001", "EQUALS", "PASS",
                fixtureFail ? "FAIL" : "PASS", fixtureFail ? Decision.FAIL : Decision.PASS, now);
        StageResult stage = new StageResult(
                "FIXTURE_HARNESS_ORACLE", fixtureFail ? Decision.FAIL : Decision.PASS,
                now, now, List.of(), Map.of());
        RegressionLock lock = new RegressionLock(
                "LOCK", "target-001", jobId, sourceDigest,
                "5".repeat(64), "6".repeat(64), now);
        ValidationReport report = new ValidationReport(
                ValidationEngine.REPORT_CONTRACT, "REPORT-" + jobId, jobId,
                target, decision, now, List.of(stage), findings,
                List.<FailureMode>of(), List.<RcaRecord>of(), List.of(fixture), lock,
                Map.of("assurance_class", "SELF_VALIDATION_NONFINAL"));
        Path file = temp.resolve(jobId + ".json");
        mapper.writeValue(file.toFile(), report);
        return file;
    }

    private Path patchReceipt() throws Exception {
        Path file = temp.resolve("patch-apply.json");
        mapper.writeValue(file.toFile(), Map.of(
                "contract", ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT,
                "patch_plan_id", "PATCH-test"));
        return file;
    }

    private static Finding finding(String fingerprint, String category, Severity severity) {
        return new Finding(
                "FINDING-" + fingerprint.substring(0, 8), fingerprint, category,
                severity, FindingStatus.OPEN, "Title", "Description", "src/Test.java",
                List.of("EV-001"), "STATIC_ANALYSIS");
    }
}