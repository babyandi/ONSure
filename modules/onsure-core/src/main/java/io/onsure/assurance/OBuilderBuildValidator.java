package io.onsure.assurance;

import static io.onsure.assurance.OBuilderBuildContext.EvidenceFile;

import java.nio.file.Files;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class OBuilderBuildValidator {
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

    public ValidationResult validate(OBuilderBuildContext context) {
        if (context == null) {
            return ValidationResult.fail(List.of("OBUILDER_CONTEXT_MISSING"));
        }
        List<String> violations = new ArrayList<>();
        String approvedPlan = verify(context.approvedPlan(), "APPROVED_PLAN", violations);
        String consumedPlan = verify(context.consumedPlan(), "CONSUMED_PLAN", violations);
        String source = verify(context.source(), "SOURCE", violations);
        String artifact = verify(context.artifact(), "ARTIFACT", violations);
        String runtimeArtifact = verify(context.runtimeArtifact(), "RUNTIME_ARTIFACT", violations);
        verify(context.dependencyLock(), "DEPENDENCY_LOCK", violations);
        verify(context.sbom(), "SBOM", violations);
        verify(context.provenance(), "PROVENANCE", violations);
        String toolchainLock = verify(context.toolchainLock(), "TOOLCHAIN_LOCK", violations);
        String buildLog = verify(context.buildLog(), "BUILD_LOG", violations);

        if (!equal(approvedPlan, consumedPlan)) violations.add("PLAN_DIGEST_MISMATCH");
        if (!equal(artifact, runtimeArtifact)) violations.add("BUILD_RUNTIME_DIGEST_MISMATCH");
        if (!sameOperations(context.declaredFileOperations(), context.actualFileOperations())) {
            violations.add("UNDECLARED_FILE_OPERATION");
        }
        verifyExecution(context, source, artifact, toolchainLock, buildLog, violations);
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static void verifyExecution(
            OBuilderBuildContext context,
            String source,
            String artifact,
            String toolchainLock,
            String buildLog,
            List<String> violations) {
        var execution = context.buildExecution();
        if (execution == null) {
            violations.add("BUILD_EXECUTION_RECEIPT_MISSING");
            return;
        }
        if (blank(execution.runId())) violations.add("BUILD_RUN_ID_MISSING");
        if (!execution.signatureVerified()) violations.add("BUILD_RECEIPT_SIGNATURE_UNVERIFIED");
        if (blank(execution.signerKeyId())) violations.add("BUILD_RECEIPT_SIGNER_MISSING");
        if (!equal(source, execution.sourceSha256())) violations.add("BUILD_SOURCE_BINDING_MISMATCH");
        if (!equal(toolchainLock, execution.toolchainLockSha256())) {
            violations.add("TOOLCHAIN_LOCK_BINDING_MISMATCH");
        }
        if (!equal(buildLog, execution.buildLogSha256())) violations.add("BUILD_LOG_BINDING_MISMATCH");
        if (!execution.isolated()) violations.add("BUILD_NOT_ISOLATED");
        if (!execution.firstBuildExecuted() || !execution.secondBuildExecuted()) {
            violations.add("REPRODUCIBLE_BUILD_NOT_EXECUTED");
        }
        if (!equal(artifact, execution.firstArtifactSha256())
                || !equal(artifact, execution.secondArtifactSha256())) {
            violations.add("NON_REPRODUCIBLE_BUILD");
        }

        var permit = context.networkPermit();
        if (execution.networkAccessed() && permit == null) {
            violations.add("UNAUTHORIZED_NETWORK_ACCESS");
            return;
        }
        if (permit != null) {
            if (!permit.signatureVerified()) violations.add("NETWORK_PERMIT_SIGNATURE_UNVERIFIED");
            if (!equal(execution.runId(), permit.runId())) violations.add("NETWORK_PERMIT_RUN_MISMATCH");
            if (!equal(source, permit.sourceSha256())) violations.add("NETWORK_PERMIT_SOURCE_MISMATCH");
            if (permit.expiresAt() == null || !permit.expiresAt().isAfter(Instant.now())) {
                violations.add("NETWORK_PERMIT_EXPIRED");
            }
            if (blank(permit.permitId()) || blank(permit.signerKeyId())) {
                violations.add("NETWORK_PERMIT_IDENTITY_MISSING");
            }
            if (equal(execution.signerKeyId(), permit.signerKeyId())) {
                violations.add("BUILD_PERMIT_SIGNING_KEY_COLLISION");
            }
            if (!permit.networkAllowed()) violations.add("UNAUTHORIZED_NETWORK_ACCESS");
        }
    }

    private static String verify(EvidenceFile evidence, String name, List<String> violations) {
        if (evidence == null || evidence.path() == null || !Files.isRegularFile(evidence.path())) {
            violations.add(name + "_EVIDENCE_MISSING");
            return null;
        }
        if (!validDigest(evidence.claimedSha256())) {
            violations.add("INVALID_" + name + "_DIGEST");
            return null;
        }
        try {
            String actual = HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(evidence.path())));
            if (!actual.equals(evidence.claimedSha256())) {
                violations.add(name + "_DIGEST_MISMATCH");
            }
            return actual;
        } catch (Exception exception) {
            violations.add(name + "_EVIDENCE_UNREADABLE");
            return null;
        }
    }

    private static boolean equal(String left, String right) {
        return left != null && left.equals(right);
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static boolean validDigest(String value) {
        return value != null && SHA256.matcher(value).matches();
    }

    private static boolean sameOperations(List<String> declared, List<String> actual) {
        if (declared == null || actual == null) return false;
        Set<String> declaredSet = new HashSet<>(declared);
        Set<String> actualSet = new HashSet<>(actual);
        return declaredSet.size() == declared.size()
                && actualSet.size() == actual.size()
                && declaredSet.equals(actualSet);
    }
}
