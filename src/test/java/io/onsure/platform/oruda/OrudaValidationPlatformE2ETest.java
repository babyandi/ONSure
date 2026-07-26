package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.OrudaTargetAdapter;
import io.onsure.platform.SourceReferenceBinding;
import io.onsure.platform.ValidationEngine;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaValidationPlatformE2ETest {
    @TempDir Path temp;

    @Test
    void standaloneDefaultEngineDoesNotRegisterOrudaAdapter() throws Exception {
        ValidationTarget oruda = target(
                "ORUDA", Path.of("fixtures/e2e/oruda-target"));
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationEngine.defaultEngine(temp.resolve("standalone-runs")).run(oruda));
        assertTrue(failure.getMessage().contains("NO_TARGET_ADAPTER"));
    }

    @Test
    void orudaRunsOnlyThroughExplicitOptionalAdapterModule() throws Exception {
        ValidationTarget oruda = target(
                "ORUDA", Path.of("fixtures/e2e/oruda-target"));
        ValidationEngine.RunResult result = OrudaValidationEngineFactory.create(
                temp.resolve("oruda-runs")).run(oruda);
        assertEquals(Decision.FAIL, result.report().decision());
        assertEquals(OrudaTargetAdapter.ID, result.report().summary().get("adapter_id"));
        assertEquals(List.of("ONSURE_GENERIC_MANIFEST_V1", OrudaTargetAdapter.ID),
                result.report().summary().get("registered_adapter_ids"));
        assertTrue(result.report().findings().stream()
                .anyMatch(value -> value.category().equals("AI_SELF_APPROVAL")));
        assertTrue(result.report().fixtureResults().stream()
                .anyMatch(value -> value.fixtureId().equals("agent-self-approval")
                        && value.observed().equals("ALLOW")
                        && value.decision() == Decision.FAIL));
        assertInternalNonfinal(result);
        assertTrue(Files.isRegularFile(
                result.runRoot().resolve("oruda-evidence-registry.json")));
    }

    @Test
    void orudaRegistrationFailsClosedWhenItClaimsONSureAuthority() throws Exception {
        Path root = temp.resolve("oruda-invalid");
        Files.createDirectories(root);
        Files.writeString(root.resolve("oruda-target.json"), """
                {
                  "contract":"ONSURE_ORUDA_TARGET_PROFILE_V1",
                  "target_id":"ORUDA",
                  "relationship":"EXTERNAL_VALIDATION_TARGET",
                  "onsure_runtime_dependency_on_oruda":false,
                  "oruda_can_write_onsure_final_decision":true,
                  "fixtures":[]
                }
                """);
        ValidationTarget target = target("ORUDA", root);
        try {
            OrudaValidationEngineFactory.create(temp.resolve("invalid-runs")).run(target);
        } catch (ValidationEngine.ValidationExecutionException e) {
            assertTrue(e.getCause().getMessage()
                    .contains("ORUDA_CANNOT_WRITE_ONSURE_FINAL_DECISION"));
            assertNotNull(e.report());
            assertTrue(Files.isRegularFile(e.runRoot().resolve("validation-report.json")));
            return;
        }
        throw new AssertionError("ORUDA authority claim must fail closed");
    }

    private static ValidationTarget target(String id, Path sourceRoot) throws Exception {
        return new ValidationTarget(
                id, id, TargetType.AI_AGENTIC_PLATFORM, sourceRoot,
                SourceReferenceBinding.treeReference(sourceRoot), OrudaTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_E2E");
    }

    private static void assertInternalNonfinal(ValidationEngine.RunResult result) {
        assertEquals("PASS", result.report().summary().get("internal_verifier"));
        assertEquals("PASS", result.report().summary().get("internal_audit"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_verifier"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_audit"));
        assertEquals("SELF_VALIDATION_NONFINAL",
                result.report().summary().get("assurance_class"));
    }
}
