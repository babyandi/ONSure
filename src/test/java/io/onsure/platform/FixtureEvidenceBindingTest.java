package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.FixtureResult;
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
    void strictSandboxClaimsRequireAllControls() {
        FixtureResult result = result("fixture-a");
        Map<String, Object> changed = new HashMap<>(attributes());
        changed.put("sandbox_profile", FixtureProcessSandbox.STRICT_BWRAP);
        changed.put("assurance_class", "SELF_VALIDATION_NONFINAL_SANDBOXED");
        changed.put("network_isolated", true);
        changed.put("filesystem_read_only", true);
        changed.put("pid_namespace_isolated", false);
        changed.put("resource_limits_enforced", true);
        Evidence evidence = evidence("fixture-a", changed);
        assertViolation(result, evidence,
                "FIXTURE_STRICT_SANDBOX_CONTROL_MISSING:fixture-a:pid_namespace_isolated");
    }

    @Test
    void semanticExecutionAndSandboxFieldsAreBoundIntoEvidenceDigest() {
        Map<String, Object> original = attributes();
        String digest = FixtureEvidenceBinding.digest("fixture-a", original);
        for (String field : List.of(
                "oracle", "harness", "command_executed", "timeout_seconds",
                "environment_sha256", "output_sha256", "sandbox_profile",
                "network_isolated", "filesystem_read_only", "pid_namespace_isolated",
                "resource_limits_enforced", "assurance_class")) {
            Map<String, Object> changed = new HashMap<>(original);
            Object replacement = original.get(field) instanceof Boolean value ? !value : "changed";
            changed.put(field, replacement);
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
                fixtureId, "HARNESS", "EQUALS", "SAFE", "SAFE", Decision.PASS,
                Instant.now());
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
                Map.entry("output_sha256", Hashing.sha256("SAFE")),
                Map.entry("sandbox_profile", FixtureProcessSandbox.REVIEWED_LOCAL_NONFINAL),
                Map.entry("network_isolated", false),
                Map.entry("filesystem_read_only", false),
                Map.entry("pid_namespace_isolated", false),
                Map.entry("resource_limits_enforced", false),
                Map.entry("assurance_class", "SELF_VALIDATION_NONFINAL"));
    }
}
