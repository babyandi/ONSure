package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.Evidence;
import kr.co.oruda.onsure.platform.ValidationModel.FixtureResult;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class FixtureEvidenceBindingTest {
    @Test
    void substitutedFixtureEvidenceIsRejected() {
        FixtureResult result = result("fixture-a");
        Evidence evidence = evidence("fixture-b", attributes());
        assertViolation(result, evidence, "FIXTURE_EVIDENCE_MISSING:fixture-a");
    }

    @Test
    void replayedEvidenceIdIsRejected() {
        FixtureResult one = result("fixture-a");
        FixtureResult two = result("fixture-b");
        Evidence evidenceOne = evidence("fixture-a", attributes());
        Evidence evidenceTwo = new Evidence(
                evidenceOne.evidenceId(), "FIXTURE_EXECUTION", "fixture-b",
                FixtureEvidenceBinding.digest("fixture-b", attributes()), Instant.now(), attributes());
        List<String> violations = FixtureEvidenceBinding.violations(
                List.of(one, two), List.of(evidenceOne, evidenceTwo));
        assertTrue(violations.contains("DUPLICATE_FIXTURE_EVIDENCE_ID"));
    }

    @Test
    void duplicateEvidenceSourceIsRejected() {
        FixtureResult result = result("fixture-a");
        Evidence one = evidence("fixture-a", attributes());
        Evidence two = new Evidence(
                "EV-SECOND", "FIXTURE_EXECUTION", "fixture-a", one.sha256(),
                Instant.now(), attributes());
        assertTrue(FixtureEvidenceBinding.violations(List.of(result), List.of(one, two))
                .contains("DUPLICATE_FIXTURE_EVIDENCE_SOURCE"));
    }

    @Test
    void digestTamperingIsRejected() {
        FixtureResult result = result("fixture-a");
        Evidence valid = evidence("fixture-a", attributes());
        Evidence tampered = new Evidence(
                valid.evidenceId(), valid.evidenceType(), valid.source(), "0".repeat(64),
                valid.collectedAt(), valid.attributes());
        assertViolation(result, tampered, "FIXTURE_EVIDENCE_DIGEST_MISMATCH:fixture-a");
    }

    @Test
    void nonExecutedCommandIsRejected() {
        FixtureResult result = result("fixture-a");
        Map<String, Object> changed = new HashMap<>(attributes());
        changed.put("command_executed", false);
        Evidence evidence = evidence("fixture-a", changed);
        assertViolation(result, evidence, "FIXTURE_COMMAND_NOT_EXECUTED:fixture-a");
    }

    @Test
    void resultAndEvidenceMismatchIsRejected() {
        FixtureResult result = result("fixture-a");
        Map<String, Object> changed = new HashMap<>(attributes());
        changed.put("observed", "OTHER");
        Evidence evidence = evidence("fixture-a", changed);
        assertViolation(result, evidence, "FIXTURE_RESULT_EVIDENCE_MISMATCH:fixture-a");
    }

    @Test
    void fabricatedOutputDigestIsRejectedEvenWhenEvidenceDigestIsRecomputed() {
        FixtureResult result = result("fixture-a");
        Map<String, Object> changed = new HashMap<>(attributes());
        changed.put("output_sha256", "0".repeat(64));
        Evidence evidence = evidence("fixture-a", changed);
        assertViolation(result, evidence, "FIXTURE_OUTPUT_SHA_MISMATCH:fixture-a");
    }

    @Test
    void arbitraryEvidenceIdIsRejected() {
        FixtureResult result = result("fixture-a");
        Evidence valid = evidence("fixture-a", attributes());
        Evidence tampered = new Evidence(
                "ARBITRARY-ID", valid.evidenceType(), valid.source(), valid.sha256(),
                valid.collectedAt(), valid.attributes());
        assertViolation(result, tampered, "FIXTURE_EVIDENCE_ID_MISMATCH:fixture-a");
    }

    @Test
    void semanticExecutionFieldsAreBoundIntoEvidenceDigest() {
        Map<String, Object> original = attributes();
        String digest = FixtureEvidenceBinding.digest("fixture-a", original);
        for (String field : List.of(
                "oracle", "harness", "command_executed", "timeout_seconds",
                "environment_sha256", "output_sha256")) {
            Map<String, Object> changed = new HashMap<>(original);
            changed.put(field, "command_executed".equals(field) ? false : "changed");
            assertTrue(!digest.equals(FixtureEvidenceBinding.digest("fixture-a", changed)), field);
        }
    }

    private static void assertViolation(
            FixtureResult result, Evidence evidence, String expected) {
        assertTrue(FixtureEvidenceBinding.violations(List.of(result), List.of(evidence))
                .contains(expected));
    }

    private static FixtureResult result(String fixtureId) {
        return new FixtureResult(
                fixtureId, "HARNESS", "EQUALS", "SAFE", "SAFE", Decision.PASS, Instant.now());
    }

    private static Evidence evidence(String fixtureId, Map<String, Object> attributes) {
        String digest = FixtureEvidenceBinding.digest(fixtureId, attributes);
        return new Evidence(
                "EV-FIXTURE-" + digest.substring(0, 16),
                "FIXTURE_EXECUTION", fixtureId, digest, Instant.now(), attributes);
    }

    private static Map<String, Object> attributes() {
        return Map.ofEntries(
                Map.entry("expected", "SAFE"),
                Map.entry("observed", "SAFE"),
                Map.entry("oracle", "EQUALS"),
                Map.entry("harness", "HARNESS"),
                Map.entry("command_executed", true),
                Map.entry("command", List.of("bash", "fixture.sh")),
                Map.entry("exit_code", 0),
                Map.entry("timed_out", false),
                Map.entry("timeout_seconds", 30),
                Map.entry("environment_sha256", FixtureEvidenceBinding.environmentDigest(Map.of())),
                Map.entry("duration_ms", 1),
                Map.entry("output_sha256", Hashing.sha256("SAFE")));
    }
}
