package io.onsure.harness;

import com.fasterxml.jackson.databind.JsonNode;
import io.onsure.harness.HarnessModels.Decision;
import io.onsure.harness.HarnessModels.Fixture;
import io.onsure.harness.HarnessModels.OracleSpec;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public final class OracleEngine {
    public record OracleDecision(Decision decision, String reason) {}

    public OracleDecision evaluate(OracleSpec oracle, Fixture fixture, String stdout,
            Integer exitCode, boolean processStarted, boolean timedOut,
            boolean evidenceComplete, Path workingDirectory) {
        if (!processStarted) return new OracleDecision(Decision.NOT_RUN, "COMMAND_NOT_STARTED");
        if (timedOut) return new OracleDecision(Decision.BLOCKED, "COMMAND_TIMEOUT");
        if (!evidenceComplete && oracle.blockedOnMissingEvidence()) {
            return new OracleDecision(Decision.BLOCKED, "REQUIRED_EVIDENCE_MISSING");
        }
        if (exitCode == null) return new OracleDecision(Decision.BLOCKED, "EXIT_CODE_MISSING");
        if (exitCode != 0 && !"EXIT_CODE".equals(oracle.type())) {
            return new OracleDecision(Decision.FAIL, "NON_ZERO_EXIT_CODE:" + exitCode);
        }

        try {
            return switch (oracle.type()) {
                case "EQUALS" -> compare(stdout.strip(), fixture.expected().asText(), "OUTPUT_EQUALS");
                case "CONTAINS" -> stdout.contains(fixture.expected().asText())
                        ? pass("OUTPUT_CONTAINS_EXPECTED") : fail("OUTPUT_MISSING_EXPECTED");
                case "EXIT_CODE" -> {
                    int expected = number(oracle.parameters(), "expected_exit_code", 0);
                    yield exitCode == expected ? pass("EXIT_CODE_MATCH") : fail("EXIT_CODE_MISMATCH");
                }
                case "NUMERIC_MAX" -> {
                    double expected = fixture.expected().asDouble();
                    double actual = Double.parseDouble(stdout.strip());
                    yield actual <= expected ? pass("NUMERIC_MAX_SATISFIED") : fail("NUMERIC_MAX_EXCEEDED");
                }
                case "NUMERIC_MIN" -> {
                    double expected = fixture.expected().asDouble();
                    double actual = Double.parseDouble(stdout.strip());
                    yield actual >= expected ? pass("NUMERIC_MIN_SATISFIED") : fail("NUMERIC_MIN_NOT_MET");
                }
                case "FILE_EXISTS" -> {
                    JsonNode expected = fixture.expected();
                    Path file = workingDirectory.resolve(expected.asText()).normalize();
                    if (!file.startsWith(workingDirectory)) yield fail("EXPECTED_FILE_PATH_ESCAPE");
                    yield Files.isRegularFile(file) ? pass("EXPECTED_FILE_EXISTS") : fail("EXPECTED_FILE_MISSING");
                }
                default -> new OracleDecision(Decision.BLOCKED, "UNKNOWN_ORACLE_TYPE:" + oracle.type());
            };
        } catch (Exception e) {
            return new OracleDecision(Decision.BLOCKED, "ORACLE_EVALUATION_ERROR:" + e.getClass().getSimpleName());
        }
    }

    private static OracleDecision compare(String actual, String expected, String reason) {
        return actual.equals(expected) ? pass(reason) : fail("OUTPUT_MISMATCH");
    }

    private static OracleDecision pass(String reason) { return new OracleDecision(Decision.PASS, reason); }
    private static OracleDecision fail(String reason) { return new OracleDecision(Decision.FAIL, reason); }

    private static int number(Map<String, Object> values, String key, int fallback) {
        Object value = values.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }
}
