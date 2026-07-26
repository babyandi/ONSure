package io.onsure.platform;

import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.TargetType;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Fail-closed completeness policy for one nonfinal product validation run. */
public final class ValidationCompletionGate {
    public static final String CONTRACT = "ONSURE_VALIDATION_COMPLETION_GATE_V4";

    public record Evaluation(boolean eligible, List<String> reasons) {
        public Evaluation { reasons = List.copyOf(reasons); }
    }

    private static final List<String> REQUIRED_STAGES = List.of(
            "TARGET_INTAKE", "SOURCE_INVENTORY", "PROGRAM_LEARNING",
            "RISK_BASED_EXECUTION_PLANNING", "STATIC_ANALYSIS", "OREVIEW",
            "FIXTURE_ORACLE_REGISTRY", "FIXTURE_HARNESS_ORACLE",
            "FAILURE_MODE_AND_RCA", "EVIDENCE_BASED_RCA", "REMEDIATION_PLANNING",
            "REGRESSION_LOCK", "INTERNAL_PRODUCT_VERIFIER", "INTERNAL_PRODUCT_AUDIT");

    private ValidationCompletionGate() {}

    public static Evaluation evaluate(ValidationContext context) {
        List<String> reasons = new ArrayList<>();
        Set<String> stageIds = new HashSet<>();
        for (StageResult stage : context.stageResults()) {
            if (!stageIds.add(stage.stageId())) reasons.add("DUPLICATE_STAGE_RESULT:" + stage.stageId());
        }
        for (String required : REQUIRED_STAGES) {
            if (!stageIds.contains(required)) reasons.add("REQUIRED_STAGE_MISSING:" + required);
        }
        boolean aiTarget = context.target().targetType() == TargetType.AI_APPLICATION
                || context.target().targetType() == TargetType.AI_AGENTIC_PLATFORM;
        if (aiTarget) {
            for (String required : List.of("AI_BEHAVIOR_VALIDATION", "BEHAVIOR_LEARNING")) {
                if (!stageIds.contains(required)) reasons.add("REQUIRED_STAGE_MISSING:" + required);
            }
        }
        requireArtifact(context, "program_profile_id", "program-profile.json",
                "PROGRAM_PROFILE_CANDIDATE", "PROGRAM_PROFILE", reasons);
        requireArtifact(context, "execution_plan_id", "execution-plan.json",
                "EXECUTION_PLAN", "EXECUTION_PLAN", reasons);
        requireArtifact(context, "review_id", "review-result.json",
                "OREVIEW_RESULT", "OREVIEW", reasons);
        requireAttributeAndFile(context, "evidence_based_rca_sha256", "evidence-based-rca.json",
                "EVIDENCE_BASED_RCA_SET", "EVIDENCE_BASED_RCA", reasons);
        Object approval = context.attributes().get("execution_plan_approval");
        if (!(approval instanceof String state)
                || !List.of("AUTO_APPROVED_DEVELOPMENT_NONFINAL", "USER_APPROVED").contains(state)) {
            reasons.add("EXECUTION_PLAN_NOT_APPROVED");
        }
        if (aiTarget) {
            requireArtifact(context, "behavior_profile_id", "behavior-profile.json",
                    "BEHAVIOR_PROFILE_CANDIDATE", "BEHAVIOR_PROFILE", reasons);
        }
        reasons.addAll(runtimeCoverageReasons(context));
        if (!Boolean.TRUE.equals(context.attributes().get("immutable_source_verified"))) {
            reasons.add("IMMUTABLE_SOURCE_REFERENCE_UNVERIFIED");
        }
        return new Evaluation(reasons.isEmpty(), reasons);
    }

    private static void requireArtifact(
            ValidationContext context, String attribute, String filename, String evidenceType,
            String label, List<String> reasons) {
        Object value = context.attributes().get(attribute);
        if (!(value instanceof String text) || text.isBlank()) reasons.add(label + "_ID_MISSING");
        requireEvidenceAndFile(context, filename, evidenceType, label, reasons);
    }

    private static void requireAttributeAndFile(
            ValidationContext context, String attribute, String filename, String evidenceType,
            String label, List<String> reasons) {
        Object value = context.attributes().get(attribute);
        if (!(value instanceof String text) || !text.matches("[0-9a-f]{64}")) {
            reasons.add(label + "_DIGEST_MISSING");
        }
        requireEvidenceAndFile(context, filename, evidenceType, label, reasons);
    }

    private static void requireEvidenceAndFile(
            ValidationContext context, String filename, String evidenceType, String label,
            List<String> reasons) {
        if (!Files.isRegularFile(context.runRoot().resolve(filename))) reasons.add(label + "_ARTIFACT_MISSING");
        if (context.evidence().stream().noneMatch(item -> evidenceType.equals(item.evidenceType()))) {
            reasons.add(label + "_EVIDENCE_MISSING");
        }
    }

    static List<String> runtimeCoverageReasons(ValidationContext context) {
        List<String> reasons = new ArrayList<>();
        long registered = numericAttribute(context, "registered_fixture_count");
        long registeredExecutable = numericAttribute(context, "registered_executable_fixture_count");
        long executed = numericAttribute(context, "executed_fixture_count");
        long results = context.fixtureResults().size();
        long evidence = context.evidence().stream()
                .filter(value -> "FIXTURE_EXECUTION".equals(value.evidenceType())).count();
        long uniqueResults = context.fixtureResults().stream()
                .map(value -> value.fixtureId()).distinct().count();
        if (registered <= 0) reasons.add("REGISTERED_FIXTURE_COUNT_ZERO");
        if (registeredExecutable <= 0) reasons.add("EXECUTABLE_FIXTURE_COUNT_ZERO");
        if (registered > 0 && registeredExecutable != registered) reasons.add("NOT_ALL_FIXTURES_EXECUTABLE");
        if (results <= 0) reasons.add("FIXTURE_RESULT_COUNT_ZERO");
        if (registered > 0 && results != registered) reasons.add("FIXTURE_RESULT_COUNT_MISMATCH");
        if (results != uniqueResults) reasons.add("DUPLICATE_FIXTURE_RESULT_ID");
        if (registered > 0 && executed != registered) reasons.add("FIXTURE_EXECUTION_COUNT_MISMATCH");
        if (results > 0 && evidence != results) reasons.add("FIXTURE_EVIDENCE_COUNT_MISMATCH");
        reasons.addAll(FixtureEvidenceBinding.violations(context.fixtureResults(), context.evidence()));
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
