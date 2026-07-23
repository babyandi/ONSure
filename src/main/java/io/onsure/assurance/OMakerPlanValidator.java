package io.onsure.assurance;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class OMakerPlanValidator {
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");

    public ValidationResult validate(OMakerPlanContext context) {
        if (context == null) {
            return ValidationResult.fail(List.of("OMAKER_PLAN_MISSING"));
        }
        List<String> violations = new ArrayList<>();
        if (same(context.producerAuthority(), context.approverAuthority())) {
            violations.add("SELF_APPROVAL_PROHIBITED");
        }
        requireDigest(context.sourceDigest(), "INVALID_SOURCE_DIGEST", violations);
        requireDigest(context.requirementDigest(), "INVALID_REQUIREMENT_DIGEST", violations);
        requireDigest(context.designDigest(), "INVALID_DESIGN_DIGEST", violations);
        requireDigest(context.policyDigest(), "INVALID_POLICY_DIGEST", violations);
        requireDigest(context.planDigest(), "INVALID_PLAN_DIGEST", violations);
        requireNonEmpty(context.declaredFileOperations(), "MISSING_CHANGE_MANIFEST", violations);
        requireNonEmpty(context.requiredTests(), "MISSING_REQUIRED_TESTS", violations);
        requireNonEmpty(context.securityControls(), "MISSING_SECURITY_CONTROLS", violations);
        if (context.approvalRequired() && !context.approvalPresent()) {
            violations.add("REQUIRED_APPROVAL_MISSING");
        }
        if (!context.rollbackPlanPresent()) {
            violations.add("ROLLBACK_PLAN_MISSING");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static boolean same(String left, String right) {
        return left != null && !left.isBlank() && left.equals(right);
    }

    private static void requireDigest(String value, String code, List<String> violations) {
        if (value == null || !SHA256.matcher(value).matches()) {
            violations.add(code);
        }
    }

    private static void requireNonEmpty(List<String> values, String code, List<String> violations) {
        if (values == null || values.isEmpty() || values.stream().anyMatch(v -> v == null || v.isBlank())) {
            violations.add(code);
        }
    }
}
