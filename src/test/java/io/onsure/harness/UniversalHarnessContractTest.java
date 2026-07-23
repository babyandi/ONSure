package io.onsure.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.harness.HarnessModels.AxisSet;
import io.onsure.harness.HarnessModels.FixtureSet;
import io.onsure.harness.HarnessModels.OracleSet;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UniversalHarnessContractTest {
    private static final Path AXES = Path.of("harness/universal-v1/axes/verification-axes.v1.json");
    private static final Path FIXTURES = Path.of("fixtures/universal-v1/sample-target/fixtures.v1.json");
    private static final Path ORACLES = Path.of("harness/universal-v1/oracles/default-oracles.v1.json");

    @Test
    void thirtyUniversalAxesAreUniqueRequiredAndFinalLockIsNeverAutomatic() throws Exception {
        AxisSet axes = JsonSupport.read(AXES, AxisSet.class);
        assertEquals("ONSURE_UNIVERSAL_VERIFICATION_AXES_V1", axes.contract());
        assertEquals(30, axes.axes().size());
        assertEquals(30, axes.axes().stream().map(value -> value.id()).distinct().count());
        assertEquals(30, axes.axes().stream().map(value -> value.code()).distinct().count());
        assertTrue(axes.axes().stream().allMatch(value -> value.required() && value.blockingOnNotRun()));
        assertTrue(axes.finalCandidateRules().notRunMustBeZero());
        assertTrue(axes.finalCandidateRules().blockedMustBeZero());
        assertEquals(2, axes.finalCandidateRules().criticalZeroConsecutiveRuns());
        assertEquals(2, axes.finalCandidateRules().majorZeroConsecutiveRuns());
        assertFalse(axes.finalCandidateRules().automaticFinalLock());
    }

    @Test
    void sevenFixtureKindsCoverEveryUniversalAxisAndUseKnownOracles() throws Exception {
        AxisSet axes = JsonSupport.read(AXES, AxisSet.class);
        FixtureSet fixtures = JsonSupport.read(FIXTURES, FixtureSet.class);
        OracleSet oracles = JsonSupport.read(ORACLES, OracleSet.class);
        Set<String> axisIds = new HashSet<>();
        Set<String> kinds = new HashSet<>();
        Set<String> oracleIds = new HashSet<>();
        oracles.oracles().forEach(value -> oracleIds.add(value.oracleId()));
        fixtures.fixtures().forEach(value -> {
            axisIds.addAll(value.axisIds());
            kinds.add(value.kind());
            assertTrue(oracleIds.contains(value.oracleId()));
            assertTrue(value.command().size() >= 2);
            assertTrue(value.requiredEvidence().containsAll(Set.of("stdout", "exit_code", "environment")));
        });
        assertEquals(axes.axes().stream().map(value -> value.id()).collect(java.util.stream.Collectors.toSet()), axisIds);
        assertEquals(Set.of("NORMAL", "ERROR", "AUTHORIZATION", "LARGE_DATA", "CONCURRENCY",
                "FAILURE_RECOVERY", "ADVERSARIAL"), kinds);
    }

    @Test
    void requiredSchemasAndRcaTemplateExist() {
        for (String path : Set.of(
                "harness/universal-v1/schemas/fixture.v1.schema.json",
                "harness/universal-v1/schemas/oracle.v1.schema.json",
                "harness/universal-v1/schemas/evidence.v1.schema.json",
                "harness/universal-v1/schemas/receipt.v1.schema.json",
                "harness/universal-v1/rca/rca-template.v1.json")) {
            assertTrue(Files.isRegularFile(Path.of(path)), path);
        }
    }
}
