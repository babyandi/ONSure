package kr.co.oruda.onsure.assurance;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public record OBuilderBuildContext(
        EvidenceFile approvedPlan,
        EvidenceFile consumedPlan,
        EvidenceFile source,
        EvidenceFile artifact,
        EvidenceFile runtimeArtifact,
        EvidenceFile dependencyLock,
        EvidenceFile sbom,
        EvidenceFile provenance,
        EvidenceFile toolchainLock,
        EvidenceFile buildLog,
        List<String> declaredFileOperations,
        List<String> actualFileOperations,
        NetworkPermitReceipt networkPermit,
        BuildExecutionReceipt buildExecution) {

    public record EvidenceFile(Path path, String claimedSha256) {
    }

    public record NetworkPermitReceipt(
            String permitId,
            String runId,
            String sourceSha256,
            boolean networkAllowed,
            Instant expiresAt,
            String signerKeyId,
            boolean signatureVerified) {
    }

    public record BuildExecutionReceipt(
            String runId,
            String sourceSha256,
            String toolchainLockSha256,
            String buildLogSha256,
            boolean isolated,
            boolean networkAccessed,
            boolean firstBuildExecuted,
            boolean secondBuildExecuted,
            String firstArtifactSha256,
            String secondArtifactSha256,
            String signerKeyId,
            boolean signatureVerified) {
    }
}
