package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import io.onsure.platform.TargetAdapter.FixtureDefinition;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Repeatedly executes registered fixtures and creates an evidence-bound Behavior Profile candidate. */
public final class BehaviorObservationEngine {
    public static final String CONTRACT = "ONSURE_BEHAVIOR_PROFILE_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> observe(
            ValidationTarget target,
            TargetAdapter adapter,
            String programProfileId,
            int repeatedRuns,
            Path output) throws Exception {
        if (repeatedRuns < 2 || repeatedRuns > 20) {
            throw new IllegalArgumentException("BEHAVIOR_REPEATED_RUN_COUNT_INVALID");
        }
        if (programProfileId == null || programProfileId.isBlank()) {
            throw new IllegalArgumentException("PROGRAM_PROFILE_ID_MISSING");
        }
        adapter.validateRegistration(target);
        List<FixtureDefinition> fixtures = adapter.loadFixtures(target);
        if (fixtures.isEmpty()) throw new IllegalStateException("BEHAVIOR_FIXTURE_SET_EMPTY");
        FixtureHarness harness = new FixtureHarness(
                "ONSURE_BEHAVIOR_OBSERVER_V1", target.executionProfile());
        String sourceDigest = Hashing.tree(target.sourceRoot());

        List<Map<String, Object>> observations = new ArrayList<>();
        Set<String> outputDigests = new LinkedHashSet<>();
        List<String> failureConditions = new ArrayList<>();
        List<String> policyViolations = new ArrayList<>();
        List<String> evidenceRefs = new ArrayList<>();

        for (int run = 1; run <= repeatedRuns; run++) {
            for (FixtureDefinition fixture : fixtures) {
                FixtureHarness.HarnessExecution execution = harness.execute(
                        fixture, target.sourceRoot());
                String inputDigest = Hashing.sha256(fixture.input());
                String outputDigest = execution.outputSha256();
                String receiptDigest = Hashing.sha256(
                        target.targetId() + "|" + fixture.fixtureId() + "|" + run + "|"
                                + inputDigest + "|" + outputDigest + "|"
                                + execution.result().decision() + "|" + execution.sandboxProfile());
                String receiptId = "BEHAVIOR-RUN-" + receiptDigest.substring(0, 20);
                outputDigests.add(outputDigest);
                List<String> toolCalls = execution.commandExecuted()
                        ? List.of(String.join(" ", execution.command())) : List.of();
                Map<String, Object> observation = new LinkedHashMap<>();
                observation.put("scenario_id", fixture.fixtureId());
                observation.put("input_digest", inputDigest);
                observation.put("output_digest", outputDigest);
                observation.put("tool_calls", toolCalls);
                observation.put("decision", execution.result().decision().name());
                observation.put("run_receipt_id", receiptId);
                observation.put("run_index", run);
                observation.put("exit_code", execution.exitCode());
                observation.put("timed_out", execution.timedOut());
                observation.put("sandbox_profile", execution.sandboxProfile());
                observation.put("environment_digest",
                        FixtureEvidenceBinding.environmentDigest(fixture.environment()));
                observations.add(Map.copyOf(observation));
                evidenceRefs.add(receiptId + ":" + receiptDigest);
                if (execution.result().decision() != Decision.PASS) {
                    failureConditions.add(fixture.fixtureId() + ":"
                            + execution.result().observed());
                    if (fixture.fixtureId().toLowerCase().contains("prompt")
                            || fixture.fixtureId().toLowerCase().contains("tool")
                            || fixture.fixtureId().toLowerCase().contains("authorization")) {
                        policyViolations.add("POLICY_SCENARIO_FAILED:" + fixture.fixtureId());
                    }
                }
            }
        }

        Map<String, Set<String>> scenarioOutputs = new LinkedHashMap<>();
        for (Map<String, Object> observation : observations) {
            scenarioOutputs.computeIfAbsent(observation.get("scenario_id").toString(),
                    ignored -> new LinkedHashSet<>())
                    .add(observation.get("output_digest").toString());
        }
        boolean stable = scenarioOutputs.values().stream().allMatch(values -> values.size() == 1);
        String profileId = "BEHAVIOR-" + Hashing.sha256(
                programProfileId + "|" + sourceDigest + "|" + repeatedRuns)
                .substring(0, 20);

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", CONTRACT);
        profile.put("profile_id", profileId);
        profile.put("program_profile_id", programProfileId);
        profile.put("source_baseline_hash", sourceDigest);
        profile.put("observations", observations);
        profile.put("variability", Map.of(
                "repeated_run_count", repeatedRuns,
                "total_observation_count", observations.size(),
                "distinct_output_count", outputDigests.size(),
                "per_scenario_distinct_output_count", scenarioOutputs.entrySet().stream()
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey, entry -> entry.getValue().size())),
                "stable", stable));
        profile.put("failure_conditions", failureConditions.stream().distinct().sorted().toList());
        profile.put("policy_violations", policyViolations.stream().distinct().sorted().toList());
        profile.put("evidence_refs", evidenceRefs);
        profile.put("model_versions", List.of("NOT_DECLARED_BY_TARGET"));
        profile.put("prompt_versions", List.of("NOT_DECLARED_BY_TARGET"));
        profile.put("tool_versions", List.of("COMMAND_DIGEST_BOUND_IN_RECEIPTS"));
        profile.put("environment_versions", List.of(target.executionProfile()));
        profile.put("state", "BEHAVIOR_CANDIDATE");
        profile.put("generated_at", Instant.now().toString());
        profile.put("verification_state", "NOT_RUN");
        write(output, profile);
        return Map.copyOf(profile);
    }

    private void write(Path output, Object value) throws Exception {
        Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
