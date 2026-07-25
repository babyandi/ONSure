package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EnvelopeAndBuilderValidatorsTest {
    private static final String D = "a".repeat(64);
    private static final String D2 = "b".repeat(64);
    private static final String COMMIT = "c".repeat(40);

    @Test void validReceiptPasses() {
        ReceiptEnvelope receipt = receipt("OTESTER", "OTESTER_AGENT", Map.of("result", "PASS"));
        assertEquals(Decision.PASS, new ReceiptEnvelopeValidator().validate(receipt).decision());
    }

    @Test void runtimeCannotIssueAuditReceipt() {
        ReceiptEnvelope receipt = new ReceiptEnvelope("r-2", "OAUDIT", "ONSURE_RUNTIME", "w-1", COMMIT, D,
                "permit-1", "OTESTER_VERIFIED", "OAUDIT_VERIFIED", Decision.PASS, Instant.now(),
                List.of(D), List.of(D2), Map.of(), "runtime-key", "sig", D);
        ValidationResult result = new ReceiptEnvelopeValidator().validate(receipt);
        assertEquals(Decision.FAIL, result.decision());
        assertTrue(result.violations().contains("INVALID_AUDIT_AUTHORITY"));
    }

    @Test void mutableSourceRefIsRejected() {
        ReceiptEnvelope receipt = new ReceiptEnvelope("r-3", "SOURCE", "ONSURE_RUNTIME", "w-1", "main", D,
                "permit-1", "UNINITIALIZED", "SOURCE_LOCKED", Decision.PASS, Instant.now(),
                List.of(D), List.of(D2), Map.of(), "runtime-key", "sig", D);
        assertTrue(new ReceiptEnvelopeValidator().validate(receipt).violations().contains("MUTABLE_SOURCE_REF"));
    }

    @Test void missingClaimsPermitAndStateOrUnsupportedTypeAreRejected() {
        ReceiptEnvelope receipt = new ReceiptEnvelope("r-4", "UNKNOWN", "ONSURE_RUNTIME", "w-1", COMMIT, D,
                "", "", "", Decision.FAIL, Instant.now(), List.of(D), List.of(D2), null,
                "runtime-key", "sig", D);
        ValidationResult result = new ReceiptEnvelopeValidator().validate(receipt);
        assertTrue(result.violations().contains("UNSUPPORTED_RECEIPT_TYPE"));
        assertTrue(result.violations().contains("MISSING_CLAIMS"));
        assertTrue(result.violations().contains("MISSING_PERMIT_ID"));
        assertTrue(result.violations().contains("MISSING_PREVIOUS_STATE"));
        assertTrue(result.violations().contains("MISSING_NEXT_STATE"));
    }

    @Test void omakerSelfApprovalIsRejected() {
        OMakerPlanContext context = new OMakerPlanContext("OMAKER_AGENT", "OMAKER_AGENT", D, D, D, D, D,
                List.of("MODIFY:src/A.java"), List.of("unit"), List.of("input-validation"), true, true, true);
        assertTrue(new OMakerPlanValidator().validate(context).violations().contains("SELF_APPROVAL_PROHIBITED"));
    }

    @Test void missingRequiredApprovalIsRejected() {
        OMakerPlanContext context = new OMakerPlanContext("OMAKER_AGENT", "ONSURE_APPROVER", D, D, D, D, D,
                List.of("MODIFY:src/A.java"), List.of("unit"), List.of("input-validation"), true, false, true);
        assertTrue(new OMakerPlanValidator().validate(context).violations().contains("REQUIRED_APPROVAL_MISSING"));
    }

    @Test void builderPlanSwapAndArtifactSwapAreRejected() throws Exception {
        OBuilderBuildContext context = buildContext("plan-a", "plan-b", "artifact-a", "artifact-b",
                List.of("MODIFY:src/A.java"), List.of("MODIFY:src/A.java"));
        ValidationResult result = new OBuilderBuildValidator().validate(context);
        assertTrue(result.violations().contains("PLAN_DIGEST_MISMATCH"));
        assertTrue(result.violations().contains("BUILD_RUNTIME_DIGEST_MISMATCH"));
    }

    @Test void undeclaredFileAndUnauthorizedNetworkAreRejected() throws Exception {
        OBuilderBuildContext base = buildContext("plan", "plan", "artifact", "artifact",
                List.of("MODIFY:src/A.java"), List.of("MODIFY:src/A.java", "CREATE:backdoor.sh"));
        OBuilderBuildContext context = new OBuilderBuildContext(
                base.approvedPlan(), base.consumedPlan(), base.source(), base.artifact(),
                base.runtimeArtifact(), base.dependencyLock(), base.sbom(), base.provenance(),
                base.toolchainLock(), base.buildLog(), base.declaredFileOperations(),
                base.actualFileOperations(), null, base.buildExecution());
        ValidationResult result = new OBuilderBuildValidator().validate(context);
        assertTrue(result.violations().contains("UNDECLARED_FILE_OPERATION"));
        assertTrue(result.violations().contains("UNDECLARED_FILE_OPERATION"));
    }

    private static OBuilderBuildContext buildContext(
            String approved, String consumed, String artifactValue, String runtimeValue,
            List<String> declared, List<String> actual) throws Exception {
        Path root = Files.createTempDirectory("obuilder-test-");
        var plan = evidence(root, "plan", approved);
        var consumedPlan = evidence(root, "consumed", consumed);
        var source = evidence(root, "source", "source");
        var artifact = evidence(root, "artifact", artifactValue);
        var runtime = evidence(root, "runtime", runtimeValue);
        var dependency = evidence(root, "dependency", "dependency");
        var sbom = evidence(root, "sbom", "sbom");
        var provenance = evidence(root, "provenance", "provenance");
        var toolchain = evidence(root, "toolchain", "toolchain");
        var log = evidence(root, "log", "log");
        var execution = new OBuilderBuildContext.BuildExecutionReceipt(
                "run-1", source.claimedSha256(), toolchain.claimedSha256(), log.claimedSha256(),
                true, true, true, true, artifact.claimedSha256(), artifact.claimedSha256(),
                "builder-key", true);
        return new OBuilderBuildContext(
                plan, consumedPlan, source, artifact, runtime, dependency, sbom, provenance,
                toolchain, log, declared, actual, null, execution);
    }

    private static OBuilderBuildContext.EvidenceFile evidence(Path root, String name, String value)
            throws Exception {
        Path file = root.resolve(name);
        Files.writeString(file, value);
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        return new OBuilderBuildContext.EvidenceFile(file, digest);
    }

    private static ReceiptEnvelope receipt(String type, String authority, Map<String, Object> claims) {
        return new ReceiptEnvelope("r-1", type, authority, "w-1", COMMIT, D, "permit-1",
                "TESTED", "OTESTER_VERIFIED", Decision.PASS, Instant.now(), List.of(D), List.of(D2),
                claims, "otester-key", "sig", D);
    }
}
