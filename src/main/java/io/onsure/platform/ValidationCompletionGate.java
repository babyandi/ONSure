package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.TargetType;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Fail-closed completeness policy for a product validation run.
 *
 * <p>A validation run is not eligible for PASS merely because every stage that happened to
 * execute returned PASS. The authoritative pipeline, executable runtime coverage, fixture
 * evidence, and independent verification stages must all be present.
 */
public final class ValidationCompletionGate {
    public static final String CONTRACT = "ONSURE_VALIDATION_COMPLETION_GATE_V1";

    public record Evaluation(boolean eligible, List<String> reasons) {
        public Evaluation {
            reasons = List.copyOf(reasons);
        }
    }

    private static final List<String> REQUIRED_STAGES = List.of(
            "TARGET_INTAKE",
            "SOURCE_INVENTORY",
            "STATIC_ANALYSIS",
            "FIXTURE_ORACLE_REGISTRY",
            "FIXTURE_HARNESS_ORACLE",
            "FAILURE_MODE_AND_RCA",
            "REMEDIATION_PLANNING",
            "REGRESSION_LOCK",
            "INTERNAL_PRODUCT_VERIFIER",
            "INTERNAL_PRODUCT_AUDIT");

    private ValidationCompletionGate() {}

    public static Evaluation evaluate(ValidationContext context) {
        List<String> reasons = new ArrayList<>();
        Set<String> stageIds = new HashSet<>();
        for (StageResult stage : context.stageResults()) {
            if (!stageIds.add(stage.stageId())) {
                reasons.add("DUPLICATE_STAGE_RESULT:" + stage.stageId());
            }
        }
        for (String required : REQUIRED_STAGES) {
            if (!stageIds.contains(required)) reasons.add("REQUIRED_STAGE_MISSING:" + required);
        }
        if ((context.target().targetType() == TargetType.AI_APPLICATION
                || context.target().targetType() == TargetType.AI_AGENTIC_PLATFORM)
                && !stageIds.contains("AI_BEHAVIOR_VALIDATION")) {
            reasons.add("REQUIRED_STAGE_MISSING:AI_BEHAVIOR_VALIDATION");
        }
        reasons.addAll(runtimeCoverageReasons(context));
        return new Evaluation(reasons.isEmpty(), reasons);
    }

    static List<String> runtimeCoverageReasons(ValidationContext context) {
        List<String> reasons = new ArrayList<>();
        long registered = numericAttribute(context, "registered_fixture_count");
        long registeredExecutable = numericAttribute(context, "registered_executable_fixture_count");
        long executed = numericAttribute(context, "executed_fixture_count");
        long results = context.fixtureResults().size();
        long evidence = context.evidence().stream()
                .filter(value -> "FIXTURE_EXECUTION".equals(value.evidenceType()))
                .count();
        long uniqueResults = context.fixtureResults().stream()
                .map(value -> value.fixtureId())
                .distinct()
                .count();

        if (registered <= 0) reasons.add("REGISTERED_FIXTURE_COUNT_ZERO");
        if (registeredExecutable <= 0) reasons.add("EXECUTABLE_FIXTURE_COUNT_ZERO");
        if (registered > 0 && registeredExecutable != registered) {
            reasons.add("NOT_ALL_FIXTURES_EXECUTABLE");
        }
        if (results <= 0) reasons.add("FIXTURE_RESULT_COUNT_ZERO");
        if (registered > 0 && results != registered) reasons.add("FIXTURE_RESULT_COUNT_MISMATCH");
        if (results != uniqueResults) reasons.add("DUPLICATE_FIXTURE_RESULT_ID");
        if (registered > 0 && executed != registered) reasons.add("FIXTURE_EXECUTION_COUNT_MISMATCH");
        if (results > 0 && evidence != results) reasons.add("FIXTURE_EVIDENCE_COUNT_MISMATCH");
        reasons.addAll(FixtureEvidenceBinding.violations(
                context.fixtureResults(), context.evidence()));
        return List.copyOf(reasons);
    }

    static void requireRuntimeCoverage(ValidationContext context, String authority) {
        List<String> reasons = runtimeCoverageReasons(context);
        if (!reasons.isEmpty()) {
            throw new IllegalStateException(authority + "_RUNTIME_COVERAGE_INCOMPLETE:"
                    + String.join(",", reasons));
        }
    }

    private static long numericAttribute(ValidationContext context, String key) {
        Object value = context.attributes().get(key);
        return value instanceof Number number ? number.longValue() : -1;
    }
}
