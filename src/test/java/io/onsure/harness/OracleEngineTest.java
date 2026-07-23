package io.onsure.harness;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.node.TextNode;
import io.onsure.harness.HarnessModels.Decision;
import io.onsure.harness.HarnessModels.Fixture;
import io.onsure.harness.HarnessModels.OracleSpec;
import io.onsure.harness.HarnessModels.Severity;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class OracleEngineTest {
    private final Fixture fixture = new Fixture(
            "FX-TEST-001", "NORMAL", Severity.MAJOR, List.of("AX-04"),
            List.of("bash", "fixture-runner.sh", "normal"), ".", 30,
            "ORC-EQUALS", TextNode.valueOf("PASS"),
            List.of("stdout", "exit_code", "environment"), List.of());
    private final OracleSpec oracle = new OracleSpec(
            "ORC-EQUALS", "EQUALS", "equals", true, Map.of());

    @Test
    void explicitStatusBoundariesAreFailClosed() {
        OracleEngine engine = new OracleEngine();
        assertEquals(Decision.PASS,
                engine.evaluate(oracle, fixture, "PASS", 0, true, false, true, Path.of(".")).decision());
        assertEquals(Decision.FAIL,
                engine.evaluate(oracle, fixture, "PASS", 7, true, false, true, Path.of(".")).decision());
        assertEquals(Decision.FAIL,
                engine.evaluate(oracle, fixture, "WRONG", 0, true, false, true, Path.of(".")).decision());
        assertEquals(Decision.BLOCKED,
                engine.evaluate(oracle, fixture, "", 124, true, true, true, Path.of(".")).decision());
        assertEquals(Decision.BLOCKED,
                engine.evaluate(oracle, fixture, "PASS", 0, true, false, false, Path.of(".")).decision());
        assertEquals(Decision.NOT_RUN,
                engine.evaluate(oracle, fixture, "", null, false, false, false, Path.of(".")).decision());
    }
}
