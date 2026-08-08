package kr.co.oruda.onsure.assurance;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class StateTransitionValidator {
    private static final Map<String, String> NEXT = new LinkedHashMap<>();

    static {
        NEXT.put("UNINITIALIZED", "SOURCE_LOCKED");
        NEXT.put("SOURCE_LOCKED", "REQUIREMENTS_VALIDATED");
        NEXT.put("REQUIREMENTS_VALIDATED", "ARCHITECTURE_REVIEWED");
        NEXT.put("ARCHITECTURE_REVIEWED", "DESIGN_REVIEWED");
        NEXT.put("DESIGN_REVIEWED", "OMAKER_PLAN_APPROVED");
        NEXT.put("OMAKER_PLAN_APPROVED", "CODE_REVIEWED");
        NEXT.put("CODE_REVIEWED", "SECURITY_REVIEWED");
        NEXT.put("SECURITY_REVIEWED", "REMEDIATION_READY");
        NEXT.put("REMEDIATION_READY", "PATCHED");
        NEXT.put("PATCHED", "OBUILDER_BUILT");
        NEXT.put("OBUILDER_BUILT", "TESTED");
        NEXT.put("TESTED", "OTESTER_VERIFIED");
        NEXT.put("OTESTER_VERIFIED", "OAUDIT_VERIFIED");
        NEXT.put("OAUDIT_VERIFIED", "PUBLICATION_ELIGIBLE");
    }

    public ValidationResult validate(TransitionContext context) {
        Objects.requireNonNull(context, "context");
        List<String> violations = new ArrayList<>();
        String expected = NEXT.get(context.from());
        if (!Objects.equals(expected, context.to())) violations.add("STAGE_SKIP");
        if (context.inputReceipts().isEmpty()) violations.add("MISSING_INPUT_RECEIPT");
        if (context.outputReceipts().isEmpty()) violations.add("MISSING_OUTPUT_RECEIPT");
        if (context.criticalOrHighOpen()) violations.add("OPEN_BLOCKING_SECURITY_FINDING");
        if (context.claimedDecision() != Decision.PASS) violations.add("NOT_RUN_CANNOT_PASS");
        validateKeySeparation(context, violations);
        validateRegressionIndependence(context, violations);
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static void validateKeySeparation(TransitionContext context, List<String> violations) {
        List<String> keys = List.of(nullToEmpty(context.runtimeKeyId()), nullToEmpty(context.otesterKeyId()), nullToEmpty(context.oauditKeyId()));
        Set<String> uniqueKeys = new HashSet<>(keys);
        if (keys.contains("") || uniqueKeys.size() != 3) violations.add("INDEPENDENCE_KEY_COLLISION");
    }

    private static void validateRegressionIndependence(TransitionContext context, List<String> violations) {
        if ("TESTED".equals(context.to()) || "OTESTER_VERIFIED".equals(context.to()) || "OAUDIT_VERIFIED".equals(context.to()) || "PUBLICATION_ELIGIBLE".equals(context.to())) {
            if (context.regressionRunId1() == null || context.regressionRunId2() == null || context.regressionRunId1().equals(context.regressionRunId2())) {
                violations.add("REGRESSION_NOT_INDEPENDENT");
            }
        }
    }

    private static String nullToEmpty(String value) { return value == null ? "" : value; }
}
