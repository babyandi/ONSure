package io.onsure.assurance;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class SupplyChainValidator {
    public ValidationResult validateDependencyLock(boolean dependencyPresentInLock) {
        return dependencyPresentInLock ? ValidationResult.pass() : ValidationResult.fail(List.of("UNLOCKED_DEPENDENCY"));
    }

    public ValidationResult validateRegression(String runId1, String runId2, String outcome1, String outcome2, String artifactDigest1, String artifactDigest2) {
        List<String> violations = new ArrayList<>();
        if (runId1 == null || runId2 == null || Objects.equals(runId1, runId2)) violations.add("REGRESSION_NOT_INDEPENDENT");
        if (!Objects.equals(outcome1, outcome2) || !Objects.equals(artifactDigest1, artifactDigest2)) violations.add("NON_REPRODUCIBLE_REGRESSION");
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public ValidationResult validatePolicyBinding(String reviewedPolicyDigest, String consumedPolicyDigest) {
        return validDigest(reviewedPolicyDigest) && Objects.equals(reviewedPolicyDigest, consumedPolicyDigest)
                ? ValidationResult.pass() : ValidationResult.fail(List.of("POLICY_DIGEST_DRIFT"));
    }

    public ValidationResult validateStandaloneUpdate(boolean signedComponentBundle, boolean rollbackTested) {
        List<String> violations = new ArrayList<>();
        if (!signedComponentBundle) violations.add("UNSIGNED_COMPONENT_UPDATE");
        if (!rollbackTested) violations.add("ROLLBACK_EVIDENCE_MISSING");
        if (violations.isEmpty()) return ValidationResult.pass();
        return signedComponentBundle
                ? new ValidationResult(Decision.HOLD, violations)
                : ValidationResult.fail(violations);
    }

    public ValidationResult validateImplementationEquivalence(String externalDigest, String embeddedDigest) {
        return validDigest(externalDigest) && Objects.equals(externalDigest, embeddedDigest)
                ? ValidationResult.pass()
                : new ValidationResult(Decision.INCONCLUSIVE, List.of("IMPLEMENTATION_EQUIVALENCE_DIVERGENCE"));
    }

    private static boolean validDigest(String value) { return value != null && value.matches("[0-9a-f]{64}"); }
}
