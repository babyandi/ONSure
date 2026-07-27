package io.onsure.platform;

import io.onsure.platform.ValidationModel.Evidence;
import io.onsure.platform.ValidationModel.FixtureResult;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Recalculates the bijective binding between runtime fixture results and execution evidence. */
final class FixtureEvidenceBinding {
    private FixtureEvidenceBinding() {}

    static List<String> violations(List<FixtureResult> results, List<Evidence> allEvidence) {
        List<String> violations = new ArrayList<>();
        Map<String, FixtureResult> resultsById = new HashMap<>();
        for (FixtureResult result : results) {
            if (resultsById.putIfAbsent(result.fixtureId(), result) != null) {
                violations.add("DUPLICATE_FIXTURE_RESULT_ID");
            }
        }

        Map<String, Evidence> evidenceByFixture = new HashMap<>();
        Set<String> evidenceIds = new HashSet<>();
        for (Evidence evidence : allEvidence) {
            if (!"FIXTURE_EXECUTION".equals(evidence.evidenceType())) continue;
            if (!evidenceIds.add(evidence.evidenceId())) {
                violations.add("DUPLICATE_FIXTURE_EVIDENCE_ID");
            }
            if (evidenceByFixture.putIfAbsent(evidence.source(), evidence) != null) {
                violations.add("DUPLICATE_FIXTURE_EVIDENCE_SOURCE");
            }
        }

        for (FixtureResult result : results) {
            Evidence evidence = evidenceByFixture.get(result.fixtureId());
            if (evidence == null) {
                violations.add("FIXTURE_EVIDENCE_MISSING:" + result.fixtureId());
                continue;
            }
            verifyPair(result, evidence, violations);
        }
        for (String fixtureId : evidenceByFixture.keySet()) {
            if (!resultsById.containsKey(fixtureId)) {
                violations.add("ORPHAN_FIXTURE_EVIDENCE:" + fixtureId);
            }
        }
        return List.copyOf(violations);
    }

    private static void verifyPair(
            FixtureResult result, Evidence evidence, List<String> violations) {
        Map<String, Object> attributes = evidence.attributes();
        String fixtureId = result.fixtureId();
        if (!Boolean.TRUE.equals(attributes.get("command_executed"))) {
            violations.add("FIXTURE_COMMAND_NOT_EXECUTED:" + fixtureId);
        }
        if (!result.expected().equals(text(attributes.get("expected")))
                || !result.observed().equals(text(attributes.get("observed")))
                || !result.oracleId().equals(text(attributes.get("oracle")))
                || !result.harnessId().equals(text(attributes.get("harness")))) {
            violations.add("FIXTURE_RESULT_EVIDENCE_MISMATCH:" + fixtureId);
        }
        String outputDigest = text(attributes.get("output_sha256"));
        if (!outputDigest.matches("[0-9a-f]{64}")) {
            violations.add("FIXTURE_OUTPUT_SHA_INVALID:" + fixtureId);
        } else if (!outputDigest.equals(Hashing.sha256(result.observed()))) {
            violations.add("FIXTURE_OUTPUT_SHA_MISMATCH:" + fixtureId);
        }
        String expectedEvidenceId = "EV-FIXTURE-" + evidence.sha256().substring(0, 16);
        if (!expectedEvidenceId.equals(evidence.evidenceId())) {
            violations.add("FIXTURE_EVIDENCE_ID_MISMATCH:" + fixtureId);
        }
        if (integer(attributes.get("timeout_seconds")) < 1) {
            violations.add("FIXTURE_TIMEOUT_INVALID:" + fixtureId);
        }
        if (!text(attributes.get("environment_sha256")).matches("[0-9a-f]{64}")) {
            violations.add("FIXTURE_ENVIRONMENT_SHA_INVALID:" + fixtureId);
        }
        String recalculated = digest(evidence.source(), attributes);
        if (!evidence.sha256().equals(recalculated)) {
            violations.add("FIXTURE_EVIDENCE_DIGEST_MISMATCH:" + fixtureId);
        }
    }

    static String digest(String fixtureId, Map<String, Object> attributes) {
        return Hashing.sha256(
                fixtureId + "|" + text(attributes.get("expected"))
                        + "|" + text(attributes.get("observed"))
                        + "|" + text(attributes.get("oracle"))
                        + "|" + text(attributes.get("harness"))
                        + "|" + Boolean.TRUE.equals(attributes.get("command_executed"))
                        + "|" + integer(attributes.get("exit_code"))
                        + "|" + Boolean.TRUE.equals(attributes.get("timed_out"))
                        + "|" + integer(attributes.get("timeout_seconds"))
                        + "|" + text(attributes.get("environment_sha256"))
                        + "|" + text(attributes.get("output_sha256"))
                        + "|" + String.join("\u0000", stringList(attributes.get("command"))));
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static int integer(Object value) {
        return value instanceof Number number ? number.intValue() : Integer.MIN_VALUE;
    }

    private static List<String> stringList(Object value) {
        if (!(value instanceof List<?> values)) return List.of();
        return values.stream().map(String::valueOf).toList();
    }

    static String environmentDigest(Map<String, String> environment) {
        Map<String, String> canonicalEnvironment = new TreeMap<>(environment);
        canonicalEnvironment.put("ONSURE_RUNTIME_SANDBOX_MODE", FixtureHarness.sandboxMode());
        canonicalEnvironment.put("ONSURE_RUNTIME_SANDBOX_BACKEND", FixtureHarness.sandboxBackend());
        String canonical = canonicalEnvironment.entrySet().stream()
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .reduce("", (left, right) -> left + "\u0000" + right);
        return Hashing.sha256(canonical);
    }
}
