package io.onsure.assurance;

import java.util.ArrayList;
import java.util.HashSet;
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
        if (!equal(context.approvedPlanDigest(), context.consumedPlanDigest())) {
            violations.add("PLAN_DIGEST_MISMATCH");
        }
        requireDigest(context.sourceDigest(), "INVALID_SOURCE_DIGEST", violations);
        requireDigest(context.artifactDigest(), "INVALID_ARTIFACT_DIGEST", violations);
        requireDigest(context.runtimeArtifactDigest(), "INVALID_RUNTIME_ARTIFACT_DIGEST", violations);
        requireDigest(context.dependencyLockDigest(), "INVALID_DEPENDENCY_LOCK_DIGEST", violations);
        requireDigest(context.sbomDigest(), "INVALID_SBOM_DIGEST", violations);
        requireDigest(context.provenanceDigest(), "INVALID_PROVENANCE_DIGEST", violations);
        if (!equal(context.artifactDigest(), context.runtimeArtifactDigest())) {
            violations.add("BUILD_RUNTIME_DIGEST_MISMATCH");
        }
        if (!sameOperations(context.declaredFileOperations(), context.actualFileOperations())) {
            violations.add("UNDECLARED_FILE_OPERATION");
        }
        if (context.networkAccessed() && !context.networkPermitPresent()) {
            violations.add("UNAUTHORIZED_NETWORK_ACCESS");
        }
        if (!context.isolatedBuild()) {
            violations.add("BUILD_NOT_ISOLATED");
        }
        if (!context.reproducibleBuild()) {
            violations.add("NON_REPRODUCIBLE_BUILD");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static boolean equal(String left, String right) {
        return left != null && left.equals(right);
    }

    private static boolean sameOperations(List<String> declared, List<String> actual) {
        if (declared == null || actual == null) {
            return false;
        }
        Set<String> declaredSet = new HashSet<>(declared);
        Set<String> actualSet = new HashSet<>(actual);
        return declaredSet.size() == declared.size()
                && actualSet.size() == actual.size()
                && declaredSet.equals(actualSet);
    }

    private static void requireDigest(String value, String code, List<String> violations) {
        if (value == null || !SHA256.matcher(value).matches()) {
            violations.add(code);
        }
    }
}
