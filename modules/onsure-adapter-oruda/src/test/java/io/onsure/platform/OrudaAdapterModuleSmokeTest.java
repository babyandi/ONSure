package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaAdapterModuleSmokeTest {
    @TempDir Path temp;

    @Test
    void optionalAdapterRegistersExplicitlyAndPreservesNonfinalAuthority() throws Exception {
        Path root = Path.of("../../fixtures/oruda/mvf-001").toAbsolutePath().normalize();
        ValidationTarget target = new ValidationTarget(
                "ORUDA-MVF-001", "ORUDA Minimum Viable Fixture Set",
                TargetType.AI_AGENTIC_PLATFORM, root,
                SourceReferenceBinding.treeReference(root), OrudaTargetAdapter.ID,
                "ONSURE_ORUDA_MVF_POLICY_V1", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);

        var result = ValidationEngine.withOptionalAdapters(
                temp.resolve("runs"), List.of(new OrudaTargetAdapter())).run(target);
        assertEquals(Decision.PASS, result.report().decision());
        assertEquals("SELF_VALIDATION_NONFINAL", result.report().summary().get("assurance_class"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_verifier"));
        assertEquals("NOT_RUN", result.report().summary().get("independent_audit"));
        assertTrue(result.report().fixtureResults().stream()
                .allMatch(value -> value.decision() == Decision.PASS));
    }
}
