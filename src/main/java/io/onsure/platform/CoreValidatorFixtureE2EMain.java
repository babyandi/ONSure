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

/** Standalone Core fixture E2E. It must compile and run without any ORUDA class or fixture. */
public final class CoreValidatorFixtureE2EMain {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private CoreValidatorFixtureE2EMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            System.err.println("usage: CoreValidatorFixtureE2EMain <output-root>");
            System.exit(64);
        }
        Path output = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(output);
        ValidationEngine engine = ValidationEngine.defaultEngine(output.resolve("validation-data"));

        ValidationEngine.RunResult generalBaseline = engine.run(target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program")));
        ValidationEngine.RunResult generalFixed = engine.run(target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                Path.of("fixtures/e2e/general-program-fixed")));
        ValidationEngine.RunResult ai = engine.run(target(
                "sample-ai-program", TargetType.AI_APPLICATION,
                Path.of("fixtures/e2e/ai-program")));

        requireDecision("general-baseline", generalBaseline.report(), Decision.FAIL);
        requireDecision("general-fixed", generalFixed.report(), Decision.PASS);
        requireDecision("ai", ai.report(), Decision.FAIL);
        for (ValidationEngine.RunResult result : List.of(generalBaseline, generalFixed, ai)) {
            requireCoreOnly(result.report());
            requireInternalNonfinal(result.report());
        }
        requireFindingCategory(ai.report(), "AI_TOOL_AUTHORIZATION");
        requireFindingCategory(ai.report(), "PROMPT_INJECTION");

        RevalidationDelta delta = new RevalidationService().compareAndWrite(
                generalBaseline.report(), generalFixed.report(),
                output.resolve("general-program-revalidation-delta.json"));
        if (!delta.sourceChanged() || !delta.regressionResultChanged()
                || delta.resolvedFindingFingerprints().isEmpty()
                || !delta.newFindingFingerprints().isEmpty()) {
            throw new IllegalStateException("general program remediation delta is not acceptable");
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("scope", "ONSURE_CORE_GENERIC_AI_FIXTURE_E2E_NONFINAL");
        normalized.put("general_baseline", normalize(generalBaseline.report()));
        normalized.put("general_fixed", normalize(generalFixed.report()));
        normalized.put("ai_program", normalize(ai.report()));
        normalized.put("general_revalidation", Map.of(
                "resolved_count", delta.resolvedFindingFingerprints().size(),
                "new_count", delta.newFindingFingerprints().size(),
                "unchanged_count", delta.unchangedFindingFingerprints().size(),
                "source_changed", delta.sourceChanged(),
                "regression_result_changed", delta.regressionResultChanged()));
        MAPPER.writeValue(output.resolve("normalized-result.json").toFile(), normalized);

        Map<String, Object> inventory = new LinkedHashMap<>();
        inventory.put("contract", "ONSURE_CORE_VALIDATOR_FIXTURE_E2E_V1");
        inventory.put("oruda_classes_required", false);
        inventory.put("oruda_fixtures_required", false);
        inventory.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        inventory.put("product_full_chain", "NOT_RUN");
        inventory.put("general_baseline_run", generalBaseline.runRoot().toString());
        inventory.put("general_fixed_run", generalFixed.runRoot().toString());
        inventory.put("ai_run", ai.runRoot().toString());
        inventory.put("normalized_result", output.resolve("normalized-result.json").toString());
        inventory.put("revalidation_delta",
                output.resolve("general-program-revalidation-delta.json").toString());
        MAPPER.writeValue(output.resolve("execution-inventory.json").toFile(), inventory);
        System.out.println("ONSURE_CORE_VALIDATOR_FIXTURE_E2E_PASS_NONFINAL " + output);
    }

    private static ValidationTarget target(String id, TargetType type, Path sourceRoot)
            throws Exception {
        return new ValidationTarget(
                id, id, type, sourceRoot, SourceReferenceBinding.treeReference(sourceRoot),
                GenericManifestTargetAdapter.ID, "ONSURE_DEFAULT_POLICY_V1",
                FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
    }

    private static void requireCoreOnly(ValidationReport report) {
        Object registered = report.summary().get("registered_adapter_ids");
        if (!(registered instanceof List<?> values)
                || !values.equals(List.of(GenericManifestTargetAdapter.ID))) {
            throw new IllegalStateException("core engine contains optional adapter: " + registered);
        }
        if (!GenericManifestTargetAdapter.ID.equals(report.summary().get("adapter_id"))) {
            throw new IllegalStateException("core report adapter mismatch");
        }
    }

    private static void requireInternalNonfinal(ValidationReport report) {
        if (!"PASS".equals(report.summary().get("internal_verifier"))
                || !"PASS".equals(report.summary().get("internal_audit"))
                || !"NOT_RUN".equals(report.summary().get("independent_verifier"))
                || !"NOT_RUN".equals(report.summary().get("independent_audit"))
                || !"SELF_VALIDATION_NONFINAL".equals(report.summary().get("assurance_class"))) {
            throw new IllegalStateException("core nonfinal receipt boundary missing");
        }
    }

    private static void requireDecision(
            String scenario, ValidationReport report, Decision expected) {
        if (report.decision() != expected) {
            throw new IllegalStateException(
                    scenario + " expected " + expected + " but got " + report.decision());
        }
    }

    private static void requireFindingCategory(ValidationReport report, String category) {
        if (report.findings().stream().noneMatch(value -> category.equals(value.category()))) {
            throw new IllegalStateException("required finding category missing: " + category);
        }
    }

    private static Map<String, Object> normalize(ValidationReport report) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("target_id", report.target().targetId());
        value.put("target_type", report.target().targetType().name());
        value.put("decision", report.decision().name());
        value.put("adapter_id", report.summary().get("adapter_id"));
        value.put("registered_adapter_ids", report.summary().get("registered_adapter_ids"));
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
        value.put("assurance_class", report.summary().get("assurance_class"));
        return value;
    }
}
