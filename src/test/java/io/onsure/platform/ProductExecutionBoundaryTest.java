package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.TargetAdapter.FixtureDefinition;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductExecutionBoundaryTest {
    @TempDir Path temp;

    @Test
    void trustedInRootScriptExecutesAndProducesOracleDecision() throws Exception {
        Files.writeString(temp.resolve("fixture.sh"), "#!/usr/bin/env bash\nprintf 'SAFE\\n'\n");
        FixtureDefinition fixture = new FixtureDefinition(
                "safe", "", "SAFE", "", "EQUALS",
                List.of("bash", "fixture.sh"), 10, Map.of());
        var execution = new FixtureHarness("test-harness").execute(fixture, temp);
        assertTrue(execution.commandExecuted());
        assertEquals(0, execution.exitCode());
        assertEquals("SAFE", execution.result().observed());
        assertEquals(Decision.PASS, execution.result().decision());
    }

    @Test
    void inlineShellAndPathEscapeAreRejected() throws Exception {
        Files.writeString(temp.resolve("inside.sh"), "printf 'SAFE\\n'\n");
        FixtureHarness harness = new FixtureHarness("test-harness");
        assertThrows(IllegalArgumentException.class, () -> harness.execute(
                new FixtureDefinition("inline", "", "SAFE", "", "EQUALS",
                        List.of("bash", "-c", "printf SAFE"), 10, Map.of()), temp));
        Path outside = temp.getParent().resolve("outside-fixture.sh");
        Files.writeString(outside, "printf 'SAFE\\n'\n");
        assertThrows(IllegalArgumentException.class, () -> harness.execute(
                new FixtureDefinition("escape", "", "SAFE", "", "EQUALS",
                        List.of("bash", "../outside-fixture.sh"), 10, Map.of()), temp));
    }

    @Test
    void receiptTamperingIsRejected() throws Exception {
        Path receipt = temp.resolve("receipt.json");
        ProductReceiptWriter.write(receipt, "CONTRACT", "AUTHORITY", "job-0001", Map.of("x", 1));
        ProductReceiptWriter.verify(receipt, "CONTRACT", "AUTHORITY", "job-0001");
        Files.writeString(receipt, Files.readString(receipt).replace("\"PASS\"", "\"FAIL\""));
        assertThrows(IllegalStateException.class,
                () -> ProductReceiptWriter.verify(receipt, "CONTRACT", "AUTHORITY", "job-0001"));
    }

    @Test
    void adapterRegistryRejectsDuplicateAndTargetTypeMismatch() {
        TargetAdapter generic = new GenericManifestTargetAdapter();
        assertThrows(IllegalArgumentException.class,
                () -> new TargetAdapterRegistry(List.of(generic, generic)));
        TargetAdapterRegistry registry = new TargetAdapterRegistry(List.of(generic));
        ValidationTarget target = new ValidationTarget(
                "ORUDA", "ORUDA", TargetType.AI_AGENTIC_PLATFORM, temp,
                "a".repeat(40), GenericManifestTargetAdapter.ID,
                "policy", "DECLARATIVE_ONLY");
        assertThrows(IllegalArgumentException.class, () -> registry.require(target));
    }
}
