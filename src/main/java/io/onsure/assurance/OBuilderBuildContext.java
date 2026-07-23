package io.onsure.assurance;

import java.util.List;

public record OBuilderBuildContext(
        String approvedPlanDigest,
        String consumedPlanDigest,
        String sourceDigest,
        String artifactDigest,
        String runtimeArtifactDigest,
        String dependencyLockDigest,
        String sbomDigest,
        String provenanceDigest,
        List<String> declaredFileOperations,
        List<String> actualFileOperations,
        boolean networkAccessed,
        boolean networkPermitPresent,
        boolean isolatedBuild,
        boolean reproducibleBuild) {
}
