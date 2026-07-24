package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.RevalidationDelta;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationReport;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes general, remediated, AI and ORUDA product scenarios and preserves evidence. */
public final class ProductPlatformE2EMain {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ProductPlatformE2EMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: ProductPlatformE2EMain <output-root>");
            System.exit(64);
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        ValidationEngine engine = ValidationEngine.defaultEngine(output.resolve("validation-data"));

        ValidationEngine.RunResult generalBaseline = engine.run(target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program"), GenericManifestTargetAdapter.ID, "a".repeat(40)));
        ValidationEngine.RunResult generalFixed = engine.run(target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program-fixed"), GenericManifestTargetAdapter.ID, "b".repeat(40)));
        ValidationEngine.RunResult ai = engine.run(target(
                "sample-ai-program", TargetType.AI_APPLICATION,
                Path.of("fixtures/e2e/ai-program"), GenericManifestTargetAdapter.ID, "c".repeat(40)));
        ValidationEngine.RunResult oruda = engine.run(target(
                "ORUDA", TargetType.AI_AGENTIC_PLATFORM,
                Path.of("fixtures/e2e/oruda-target"), OrudaTargetAdapter.ID, "d".repeat(40)));
        ValidationEngine.RunResult orudaMvf = engine.run(target(
                "ORUDA-MVF-001", TargetType.AI_AGENTIC_PLATFORM,
                Path.of("fixtures/oruda/mvf-001"), OrudaTargetAdapter.ID, "f".repeat(40)));

        requireDecision("general-baseline", generalBaseline.report(), Decision.FAIL);
        requireDecision("general-fixed", generalFixed.report(), Decision.PASS);
        requireDecision("ai", ai.report(), Decision.FAIL);
        requireDecision("oruda", oruda.report(), Decision.FAIL);
        requireDecision("oruda-mvf-001", orudaMvf.report(), Decision.PASS);
        for (ValidationEngine.RunResult result : List.of(
                generalBaseline, generalFixed, ai, oruda, orudaMvf)) {
            requireIndependentReceipts(result.report());
        }
        requireFindingCategory(ai.report(), "AI_TOOL_AUTHORIZATION");
        requireFindingCategory(ai.report(), "PROMPT_INJECTION");
        requireFindingCategory(oruda.report(), "AI_SELF_APPROVAL");
        if (orudaMvf.report().fixtureResults().size() != 17
                || orudaMvf.report().fixtureResults().stream()
                        .anyMatch(value -> value.decision() != Decision.PASS)) {
            throw new IllegalStateException("ORUDA MVF-001 fixture execution mismatch");
        }

        RevalidationDelta delta = new RevalidationService().compareAndWrite(
                generalBaseline.report(), generalFixed.report(),
                output.resolve("general-program-revalidation-delta.json"));
        if (!delta.sourceChanged() || !delta.regressionResultChanged()
                || delta.resolvedFindingFingerprints().isEmpty()
                || !delta.newFindingFingerprints().isEmpty()) {
            throw new IllegalStateException("general program remediation delta is not acceptable");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("general_baseline", normalize(generalBaseline.report()));
        normalized.put("general_fixed", normalize(generalFixed.report()));
        normalized.put("ai_program", normalize(ai.report()));
        normalized.put("oruda_target", normalize(oruda.report()));
        normalized.put("oruda_mvf_001", normalize(orudaMvf.report()));
        normalized.put("general_revalidation", Map.of(
                "resolved_count", delta.resolvedFindingFingerprints().size(),
                "new_count", delta.newFindingFingerprints().size(),
                "unchanged_count", delta.unchangedFindingFingerprints().size(),
                "source_changed", delta.sourceChanged(),
                "regression_result_changed", delta.regressionResultChanged()));
        MAPPER.writeValue(output.resolve("normalized-result.json").toFile(), normalized);

        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("contract", "ONSURE_PRODUCT_PLATFORM_E2E_V1");
        inventory.put("general_baseline_run", generalBaseline.runRoot().toString());
        inventory.put("general_fixed_run", generalFixed.runRoot().toString());
        inventory.put("ai_run", ai.runRoot().toString());
        inventory.put("oruda_run", oruda.runRoot().toString());
        inventory.put("oruda_mvf_001_run", orudaMvf.runRoot().toString());
        inventory.put("normalized_result", output.resolve("normalized-result.json").toString());
        inventory.put("revalidation_delta", output.resolve("general-program-revalidation-delta.json").toString());
        MAPPER.writeValue(output.resolve("execution-inventory.json").toFile(), inventory);
        System.out.println("ONSURE_PRODUCT_E2E_EXECUTION_PASS " + output);
    }

    private static Map<String, Object> normalize(ValidationReport report) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("target_id", report.target().targetId());
        value.put("target_type", report.target().targetType().name());
        value.put("decision", report.decision().name());
        value.put("adapter_id", report.summary().get("adapter_id"));
        value.put("finding_fingerprints", report.findings().stream()
                .map(finding -> finding.fingerprint()).sorted().toList());
        value.put("failure_mode_codes", report.failureModes().stream()
                .map(mode -> mode.code()).sorted().toList());
        value.put("fixtures", report.fixtureResults().stream()
                .map(fixture -> fixture.fixtureId() + ":" + fixture.expected() + ":"
                        + fixture.observed() + ":" + fixture.decision())
                .sorted().toList());
        value.put("source_digest", report.regressionLock().sourceDigest());
        value.put("result_digest", report.regressionLock().resultDigest());
        value.put("internal_verifier", report.summary().get("internal_verifier"));
        value.put("internal_audit", report.summary().get("internal_audit"));
        value.put("assurance_class", report.summary().get("assurance_class"));
        return value;
    }

    private static ValidationTarget target(String id, TargetType type, Path sourceRoot,
            String adapterId, String ignoredSourceReference) throws Exception {
        return new ValidationTarget(
                id, id, type, sourceRoot, SourceReferenceBinding.treeReference(sourceRoot), adapterId,
                "ONSURE_DEFAULT_POLICY_V1", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
    }

    private static void requireDecision(String scenario, ValidationReport report, Decision expected) {
        if (report.decision() != expected) {
            throw new IllegalStateException(scenario + " expected " + expected + " but got " + report.decision());
        }
    }

    private static void requireFindingCategory(ValidationReport report, String category) {
        if (report.findings().stream().noneMatch(value -> category.equals(value.category()))) {
            throw new IllegalStateException("required finding category missing: " + category);
        }
    }

    private static void requireIndependentReceipts(ValidationReport report) {
        if (!"PASS".equals(report.summary().get("internal_verifier"))
                || !"PASS".equals(report.summary().get("internal_audit"))
                || !"NOT_RUN".equals(report.summary().get("independent_verifier"))
                || !"NOT_RUN".equals(report.summary().get("independent_audit"))) {
            throw new IllegalStateException("internal nonfinal verification missing for "
                    + report.target().targetId());
        }
    }
}
