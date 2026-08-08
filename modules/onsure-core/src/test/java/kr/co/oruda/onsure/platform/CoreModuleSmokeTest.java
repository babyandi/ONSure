package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreModuleSmokeTest {
    @TempDir Path temp;

    @Test
    void coreRunsGenericAndAiWithoutOptionalAdapter() throws Exception {
        Path fixedRoot = Path.of("../../fixtures/e2e/general-program-fixed").toAbsolutePath().normalize();
        ValidationTarget fixed = target(
                "sample-general-program", TargetType.GENERAL_SOFTWARE,
                fixedRoot, GenericManifestTargetAdapter.ID);
        var fixedResult = ValidationEngine.defaultEngine(temp.resolve("fixed")).run(fixed);
        assertEquals(Decision.HOLD, fixedResult.report().decision());
        assertEquals("HOLD", fixedResult.report().summary().get("review_quality_decision"));
        assertFalse(fixedResult.report().summary().get("registered_adapter_ids").toString().contains("ORUDA"));

        Path aiRoot = Path.of("../../fixtures/e2e/ai-program").toAbsolutePath().normalize();
        ValidationTarget ai = target(
                "sample-ai-program", TargetType.AI_APPLICATION,
                aiRoot, GenericManifestTargetAdapter.ID);
        var aiResult = ValidationEngine.defaultEngine(temp.resolve("ai")).run(ai);
        assertEquals(Decision.FAIL, aiResult.report().decision());
        assertTrue(aiResult.report().findings().stream()
                .anyMatch(value -> "PROMPT_INJECTION".equals(value.category())));
    }

    @Test
    void coreRejectsAgenticTargetWithoutOptionalAdapterAndWithoutTargetSpecificFixture() throws Exception {
        Path root = temp.resolve("neutral-agentic-target");
        Files.createDirectories(root);
        Files.writeString(root.resolve("placeholder.txt"), "target-neutral\n");
        ValidationTarget target = new ValidationTarget(
                "external-agentic-target", "External Agentic Target",
                TargetType.AI_AGENTIC_PLATFORM, root,
                SourceReferenceBinding.treeReference(root), "OPTIONAL_AGENTIC_ADAPTER_V1",
                "policy", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> ValidationEngine.defaultEngine(temp.resolve("reject")).run(target));
        assertTrue(error.getMessage().contains("NO_TARGET_ADAPTER"));
    }

    private static ValidationTarget target(String id, TargetType type, Path root, String adapter) throws Exception {
        return new ValidationTarget(
                id, id, type, root, SourceReferenceBinding.treeReference(root), adapter,
                "ONSURE_DEFAULT_POLICY_V1", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
    }
}