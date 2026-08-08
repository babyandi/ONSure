package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationCompletionGateTest {
    @TempDir Path temp;

    @Test
    void emptyFixtureSetCannotProducePass() throws Exception {
        ValidationTarget target = writeTarget("empty", "[]");
        try {
            ValidationEngine.defaultEngine(temp.resolve("empty-runs")).run(target);
        } catch (ValidationEngine.ValidationExecutionException failure) {
            assertEquals(Decision.FAIL, failure.report().decision());
            assertTrue(failure.getCause().getMessage().contains("FIXTURE_SET_EMPTY"));
            assertFalse((Boolean) failure.report().summary().get("completion_gate_eligible"));
            return;
        }
        throw new AssertionError("empty fixture set must fail closed");
    }

    @Test
    void declaredOnlyFixtureCannotStandInForRuntimeExecution() throws Exception {
        ValidationTarget target = writeTarget("declared-only", """
                [{
                  "id":"declared-only",
                  "input":"unsafe",
                  "expected":"BLOCK",
                  "observed":"BLOCK",
                  "oracle":"EQUALS"
                }]
                """);
        try {
            ValidationEngine.defaultEngine(temp.resolve("declared-runs")).run(target);
        } catch (ValidationEngine.ValidationExecutionException failure) {
            assertEquals(Decision.FAIL, failure.report().decision());
            assertTrue(failure.getCause().getMessage().contains("ALL_FIXTURES_MUST_BE_EXECUTABLE"));
            return;
        }
        throw new AssertionError("declared observation must not replace runtime execution");
    }

    @Test
    void duplicateFixtureIdsCannotProduceAmbiguousEvidence() throws Exception {
        ValidationTarget target = writeTarget("duplicate", """
                [
                  {
                    "id":"same-id",
                    "input":"one",
                    "expected":"SAFE",
                    "oracle":"EQUALS",
                    "command":["bash","fixture.sh","one"]
                  },
                  {
                    "id":"same-id",
                    "input":"two",
                    "expected":"SAFE",
                    "oracle":"EQUALS",
                    "command":["bash","fixture.sh","two"]
                  }
                ]
                """);
        try {
            ValidationEngine.defaultEngine(temp.resolve("duplicate-runs")).run(target);
        } catch (ValidationEngine.ValidationExecutionException failure) {
            assertEquals(Decision.FAIL, failure.report().decision());
            assertTrue(failure.getCause().getMessage().contains("DUPLICATE_FIXTURE_ID"));
            return;
        }
        throw new AssertionError("duplicate fixture identifiers must fail closed");
    }

    @Test
    void partialPipelineCannotPassByOmittingRequiredStages() throws Exception {
        ValidationTarget target = writeTarget("partial", "[]");
        ValidatorStage singlePassingStage = new ValidatorStage() {
            @Override public String stageId() { return "TARGET_INTAKE"; }
            @Override public boolean supports(ValidationContext context) { return true; }
            @Override public StageResult execute(ValidationContext context) {
                Instant now = Instant.now();
                return new StageResult(stageId(), Decision.PASS, now, now, List.of(), Map.of());
            }
        };
        ValidationEngine engine = new ValidationEngine(
                List.of(new GenericManifestTargetAdapter()),
                List.of(singlePassingStage),
                new FileValidationStore(temp.resolve("partial-runs")));

        ValidationEngine.RunResult result = engine.run(target);

        assertEquals(Decision.FAIL, result.report().decision());
        assertFalse((Boolean) result.report().summary().get("completion_gate_eligible"));
        @SuppressWarnings("unchecked")
        List<String> reasons = (List<String>) result.report().summary().get("completion_gate_reasons");
        assertTrue(reasons.contains("REQUIRED_STAGE_MISSING:FIXTURE_ORACLE_REGISTRY"));
        assertTrue(reasons.contains("REGISTERED_FIXTURE_COUNT_ZERO"));
        assertTrue(reasons.contains("FIXTURE_RESULT_COUNT_ZERO"));
    }

    @Test
    void bogusImmutableReferenceCannotProducePass() throws Exception {
        ValidationTarget valid = writeTarget("bogus-ref", """
                [{
                  "id":"safe",
                  "input":"safe",
                  "expected":"SAFE",
                  "oracle":"EQUALS",
                  "command":["bash","fixture.sh"]
                }]
                """);
        ValidationTarget bogus = new ValidationTarget(
                valid.targetId(), valid.targetName(), valid.targetType(), valid.sourceRoot(),
                "0".repeat(40), valid.adapterId(), valid.policyProfile(), valid.executionProfile());
        try {
            ValidationEngine.defaultEngine(temp.resolve("bogus-ref-runs")).run(bogus);
        } catch (ValidationEngine.ValidationExecutionException failure) {
            assertEquals(Decision.FAIL, failure.report().decision());
            assertTrue(failure.getCause().getMessage().contains(
                    "IMMUTABLE_SOURCE_REFERENCE_UNVERIFIED"));
            assertFalse((Boolean) failure.report().summary().get("completion_gate_eligible"));
            return;
        }
        throw new AssertionError("unverified immutable reference must fail closed");
    }

    private ValidationTarget writeTarget(String id, String fixtures) throws Exception {
        Path root = temp.resolve(id);
        Files.createDirectories(root);
        Files.writeString(root.resolve("fixture.sh"), "#!/usr/bin/env bash\nprintf 'SAFE\\n'\n");
        Files.writeString(root.resolve("onsure-target.json"), """
                {
                  "contract":"ONSURE_TARGET_MANIFEST_V1",
                  "target_id":"%s",
                  "target_type":"GENERAL_SOFTWARE",
                  "self_reported_final_decision":false,
                  "capabilities":[],
                  "fixtures":%s
                }
                """.formatted(id, fixtures));
        return new ValidationTarget(
                id, id, TargetType.GENERAL_SOFTWARE, root,
                SourceReferenceBinding.treeReference(root),
                GenericManifestTargetAdapter.ID, "ONSURE_DEFAULT_POLICY_V1", "LOCAL_E2E");
    }
}
