package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.TargetAdapter.FixtureDefinition;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
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
    void fixtureProcessDoesNotReceiveHostHome() throws Exception {
        Files.writeString(temp.resolve("environment.sh"),
                "#!/usr/bin/env bash\nprintf '%s\\n' \"${HOME:-ABSENT}\"\n");
        FixtureDefinition fixture = new FixtureDefinition(
                "host-home", "", "ABSENT", "", "EQUALS",
                List.of("bash", "environment.sh"), 10, Map.of());
        var execution = new FixtureHarness("test-harness").execute(fixture, temp);
        assertEquals("ABSENT", execution.result().observed());
        assertEquals(Decision.PASS, execution.result().decision());
    }

    @Test
    void commandManifestDoesNotClaimUnenforcedNetworkIsolation() throws Exception {
        Files.writeString(temp.resolve("fixture.sh"), "#!/usr/bin/env bash\nprintf 'SAFE\\n'\n");
        FixtureDefinition fixture = new FixtureDefinition(
                "safe", "", "SAFE", "", "EQUALS",
                List.of("bash", "fixture.sh"), 10, Map.of());
        Path run = temp.resolve("run");
        new FixtureRegistry().persist(
                run, "target", temp, List.of(fixture), "test-harness", java.util.Set.of("EQUALS"));
        String manifest = Files.readString(run.resolve("harness-command-manifest.json"));
        assertTrue(manifest.contains("\"network_policy\" : \"HOST_POLICY_NOT_ENFORCED\""));
        assertTrue(manifest.contains("\"execution_trust_boundary\" : \"REVIEWED_LOCAL_FIXTURES_ONLY\""));
    }

    @Test
    void learnedValidationPackRequiresDedicatedScenarioFixtures() throws Exception {
        Files.writeString(temp.resolve("fixture.sh"), "#!/usr/bin/env bash\nprintf 'PASS\\n'\n");
        Files.writeString(temp.resolve("onsure-target.json"), """
                {
                  "contract":"ONSURE_TARGET_MANIFEST_V1",
                  "target_id":"learned-pack",
                  "target_type":"GENERAL_SOFTWARE",
                  "self_reported_final_decision":false,
                  "capabilities":["ONSURE_LEARNED_VALIDATION_PACK"],
                  "required_scenarios":["known-miss"],
                  "fixtures":[{
                    "id":"different-case",
                    "input":"x",
                    "expected":"PASS",
                    "oracle":"EQUALS",
                    "command":["bash","fixture.sh"]
                  }]
                }
                """);
        ValidationTarget target = new ValidationTarget(
                "learned-pack", "learned-pack", TargetType.GENERAL_SOFTWARE, temp,
                SourceReferenceBinding.treeReference(temp), GenericManifestTargetAdapter.ID,
                "policy", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
        var error = assertThrows(
                IllegalArgumentException.class,
                () -> new GenericManifestTargetAdapter().validateRegistration(target));
        assertTrue(error.getMessage().contains("LEARNED_SCENARIO_FIXTURE_MISSING"));
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
        Path linked = temp.resolve("linked-fixture.sh");
        Files.createSymbolicLink(linked, outside);
        assertThrows(IllegalArgumentException.class, () -> harness.execute(
                new FixtureDefinition("symlink", "", "SAFE", "", "EQUALS",
                        List.of("bash", "linked-fixture.sh"), 10, Map.of()), temp));
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
