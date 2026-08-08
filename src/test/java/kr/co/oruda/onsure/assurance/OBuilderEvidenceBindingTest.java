package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import org.junit.jupiter.api.Test;

class OBuilderEvidenceBindingTest {
    @Test
    void validByteAndReceiptBoundBuildPasses() throws Exception {
        assertEquals(Decision.PASS, new OBuilderBuildValidator().validate(valid()).decision());
    }

    @Test
    void matchingFabricatedPlanDigestsCannotPass() throws Exception {
        OBuilderBuildContext base = valid();
        String fabricated = "a".repeat(64);
        OBuilderBuildContext context = replacePlan(
                base,
                new OBuilderBuildContext.EvidenceFile(base.approvedPlan().path(), fabricated),
                new OBuilderBuildContext.EvidenceFile(base.consumedPlan().path(), fabricated));
        ValidationResult result = new OBuilderBuildValidator().validate(context);
        assertTrue(result.violations().contains("APPROVED_PLAN_DIGEST_MISMATCH"));
        assertTrue(result.violations().contains("CONSUMED_PLAN_DIGEST_MISMATCH"));
    }

    @Test
    void artifactTamperingAfterReceiptIsRejected() throws Exception {
        OBuilderBuildContext context = valid();
        Files.writeString(context.artifact().path(), "tampered");
        assertTrue(new OBuilderBuildValidator().validate(context).violations()
                .contains("ARTIFACT_DIGEST_MISMATCH"));
    }

    @Test
    void isolationAndReproducibilityRequireExecutionReceipt() throws Exception {
        OBuilderBuildContext base = valid();
        OBuilderBuildContext context = replaceExecution(base, null);
        assertTrue(new OBuilderBuildValidator().validate(context).violations()
                .contains("BUILD_EXECUTION_RECEIPT_MISSING"));
    }

    @Test
    void reproducibilityRequiresTwoExecutedBuildsWithSameArtifact() throws Exception {
        OBuilderBuildContext base = valid();
        var receipt = base.buildExecution();
        var unexecuted = new OBuilderBuildContext.BuildExecutionReceipt(
                receipt.runId(), receipt.sourceSha256(), receipt.toolchainLockSha256(),
                receipt.buildLogSha256(), true, false, true, false, receipt.firstArtifactSha256(),
                receipt.secondArtifactSha256(), receipt.signerKeyId(), true);
        assertTrue(new OBuilderBuildValidator().validate(replaceExecution(base, unexecuted))
                .violations().contains("REPRODUCIBLE_BUILD_NOT_EXECUTED"));
    }

    @Test
    void permitMustBeSignedRunAndSourceBoundAndUseIndependentKey() throws Exception {
        OBuilderBuildContext base = valid();
        var permit = new OBuilderBuildContext.NetworkPermitReceipt(
                "permit-1", "wrong-run", "b".repeat(64), true,
                Instant.now().plusSeconds(60), "builder-key", false);
        OBuilderBuildContext context = replacePermit(base, permit);
        ValidationResult result = new OBuilderBuildValidator().validate(context);
        assertTrue(result.violations().contains("NETWORK_PERMIT_SIGNATURE_UNVERIFIED"));
        assertTrue(result.violations().contains("NETWORK_PERMIT_RUN_MISMATCH"));
        assertTrue(result.violations().contains("NETWORK_PERMIT_SOURCE_MISMATCH"));
        assertTrue(result.violations().contains("BUILD_PERMIT_SIGNING_KEY_COLLISION"));
    }

    @Test
    void toolchainAndBuildLogAreBoundToExecutionReceipt() throws Exception {
        OBuilderBuildContext base = valid();
        var receipt = base.buildExecution();
        var swapped = new OBuilderBuildContext.BuildExecutionReceipt(
                receipt.runId(), receipt.sourceSha256(), "c".repeat(64), "d".repeat(64),
                true, false, true, true, receipt.firstArtifactSha256(), receipt.secondArtifactSha256(),
                receipt.signerKeyId(), true);
        ValidationResult result = new OBuilderBuildValidator().validate(replaceExecution(base, swapped));
        assertTrue(result.violations().contains("TOOLCHAIN_LOCK_BINDING_MISMATCH"));
        assertTrue(result.violations().contains("BUILD_LOG_BINDING_MISMATCH"));
    }

    private static OBuilderBuildContext valid() throws Exception {
        Path root = Files.createTempDirectory("obuilder-binding-");
        var plan = evidence(root, "plan", "approved-plan");
        var source = evidence(root, "source", "source-tree-manifest");
        var artifact = evidence(root, "artifact", "artifact");
        var dependency = evidence(root, "dependency", "dependency-lock");
        var sbom = evidence(root, "sbom", "sbom");
        var provenance = evidence(root, "provenance", "provenance");
        var toolchain = evidence(root, "toolchain", "jdk=17");
        var log = evidence(root, "log", "build executed");
        var execution = new OBuilderBuildContext.BuildExecutionReceipt(
                "run-1", source.claimedSha256(), toolchain.claimedSha256(), log.claimedSha256(),
                true, false, true, true, artifact.claimedSha256(), artifact.claimedSha256(),
                "builder-key", true);
        return new OBuilderBuildContext(
                plan, plan, source, artifact, artifact, dependency, sbom, provenance,
                toolchain, log, List.of("MODIFY:src/A.java"), List.of("MODIFY:src/A.java"),
                null, execution);
    }

    private static OBuilderBuildContext replacePlan(
            OBuilderBuildContext base,
            OBuilderBuildContext.EvidenceFile approved,
            OBuilderBuildContext.EvidenceFile consumed) {
        return new OBuilderBuildContext(
                approved, consumed, base.source(), base.artifact(), base.runtimeArtifact(),
                base.dependencyLock(), base.sbom(), base.provenance(), base.toolchainLock(),
                base.buildLog(), base.declaredFileOperations(), base.actualFileOperations(),
                base.networkPermit(), base.buildExecution());
    }

    private static OBuilderBuildContext replaceExecution(
            OBuilderBuildContext base, OBuilderBuildContext.BuildExecutionReceipt execution) {
        return new OBuilderBuildContext(
                base.approvedPlan(), base.consumedPlan(), base.source(), base.artifact(),
                base.runtimeArtifact(), base.dependencyLock(), base.sbom(), base.provenance(),
                base.toolchainLock(), base.buildLog(), base.declaredFileOperations(),
                base.actualFileOperations(), base.networkPermit(), execution);
    }

    private static OBuilderBuildContext replacePermit(
            OBuilderBuildContext base, OBuilderBuildContext.NetworkPermitReceipt permit) {
        return new OBuilderBuildContext(
                base.approvedPlan(), base.consumedPlan(), base.source(), base.artifact(),
                base.runtimeArtifact(), base.dependencyLock(), base.sbom(), base.provenance(),
                base.toolchainLock(), base.buildLog(), base.declaredFileOperations(),
                base.actualFileOperations(), permit, base.buildExecution());
    }

    private static OBuilderBuildContext.EvidenceFile evidence(Path root, String name, String value)
            throws Exception {
        Path file = root.resolve(name);
        Files.writeString(file, value);
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        return new OBuilderBuildContext.EvidenceFile(file, digest);
    }
}
