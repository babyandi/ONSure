package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Executes registered scenarios repeatedly and materializes an evidence-bound Behavior Profile. */
public final class BehaviorLearningService {
    public static final String CONTRACT = "ONSURE_BEHAVIOR_PROFILE_V1";
    private static final int MIN_REPETITIONS = 2;
    private static final int MAX_REPETITIONS = 10;

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> learn(
            ValidationTargetBundle bundle,
            String programProfileId,
            int repetitions,
            Path outputFile) throws Exception {
        if (repetitions < MIN_REPETITIONS || repetitions > MAX_REPETITIONS) {
            throw new IllegalArgumentException("BEHAVIOR_REPETITION_RANGE_INVALID");
        }
        TargetAdapter adapter = bundle.adapter();
        ValidationModel.ValidationTarget target = bundle.target();
        adapter.validateRegistration(target);
        List<TargetAdapter.FixtureDefinition> fixtures = adapter.loadFixtures(target);
        if (fixtures.isEmpty()) throw new IllegalStateException("BEHAVIOR_FIXTURES_REQUIRED");
        if (fixtures.stream().anyMatch(fixture -> !fixture.executable())) {
            throw new IllegalStateException("BEHAVIOR_FIXTURE_NOT_EXECUTABLE");
        }

        String sourceDigest = Hashing.tree(target.sourceRoot());
        FixtureHarness harness = new FixtureHarness("ONSURE_BEHAVIOR_LEARNING_HARNESS_V1");
        List<Map<String, Object>> observations = new ArrayList<>();
        Map<String, Set<String>> outputByScenario = new TreeMap<>();
        Set<String> failureConditions = new LinkedHashSet<>();
        Set<String> policyViolations = new LinkedHashSet<>();
        Set<String> evidenceRefs = new LinkedHashSet<>();
        Set<String> allOutputs = new HashSet<>();

        for (int iteration = 1; iteration <= repetitions; iteration++) {
            for (TargetAdapter.FixtureDefinition fixture : fixtures) {
                FixtureHarness.HarnessExecution execution = harness.execute(fixture, target.sourceRoot());
                String inputDigest = sha256(mapper.writeValueAsBytes(Map.of(
                        "scenario_id", fixture.fixtureId(),
                        "input", fixture.input(),
                        "expected", fixture.expected(),
                        "command", fixture.command(),
                        "environment", new TreeMap<>(fixture.environment()))));
                String outputDigest = execution.outputSha256();
                String receiptId = "BEH-" + fixture.fixtureId() + "-" + iteration + "-"
                        + outputDigest.substring(0, 12);
                Map<String, Object> observation = new LinkedHashMap<>();
                observation.put("scenario_id", fixture.fixtureId());
                observation.put("iteration", iteration);
                observation.put("input_digest", inputDigest);
                observation.put("output_digest", outputDigest);
                observation.put("tool_calls", execution.command());
                observation.put("decision", execution.result().decision().name());
                observation.put("duration_ms", execution.durationMillis());
                observation.put("run_receipt_id", receiptId);
                observations.add(Map.copyOf(observation));
                outputByScenario.computeIfAbsent(fixture.fixtureId(), ignored -> new HashSet<>())
                        .add(outputDigest);
                allOutputs.add(outputDigest);
                evidenceRefs.add(receiptId);
                if (execution.result().decision() != Decision.PASS) {
                    failureConditions.add(fixture.fixtureId() + ":"
                            + execution.result().decision().name());
                }
                if (execution.timedOut()) policyViolations.add("SCENARIO_TIMEOUT:" + fixture.fixtureId());
                if (execution.exitCode() != 0) {
                    policyViolations.add("NON_ZERO_EXIT:" + fixture.fixtureId() + ":" + execution.exitCode());
                }
            }
        }

        List<String> unstable = outputByScenario.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();
        boolean stable = unstable.isEmpty();
        String environmentDigest = environmentDigest(target, adapter, harness);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", CONTRACT);
        profile.put("profile_id", "BP-" + sourceDigest.substring(0, 16) + "-" + repetitions);
        profile.put("program_profile_id", requireId(programProfileId, "PROGRAM_PROFILE_ID_INVALID"));
        profile.put("source_baseline_hash", sourceDigest);
        profile.put("runtime_context", Map.of(
                "harness_id", harness.harnessId(),
                "execution_profile", target.executionProfile(),
                "model_id", metadata(adapter, target, "model_id", "NOT_DECLARED"),
                "model_version", metadata(adapter, target, "model_version", "NOT_DECLARED"),
                "prompt_digest", metadataDigest(adapter, target, "prompt_digest"),
                "tool_registry_digest", metadataDigest(adapter, target, "tool_registry_digest"),
                "environment_digest", environmentDigest));
        profile.put("observations", List.copyOf(observations));
        profile.put("variability", Map.of(
                "repeated_run_count", repetitions,
                "scenario_count", fixtures.size(),
                "distinct_output_count", allOutputs.size(),
                "unstable_scenarios", unstable,
                "stable", stable));
        profile.put("failure_conditions", List.copyOf(failureConditions));
        profile.put("policy_violations", List.copyOf(policyViolations));
        profile.put("evidence_refs", List.copyOf(evidenceRefs));
        profile.put("state", "BEHAVIOR_CANDIDATE");
        profile.put("generated_at", Instant.now().toString());
        profile.put("learning_method", "REPEATED_EXECUTABLE_FIXTURE_OBSERVATION_V1");
        profile.put("human_review", "NOT_RUN");
        profile.put("independent_validation", "NOT_RUN");
        profile.put("final_claim_allowed", false);
        writeAtomic(outputFile, profile);
        return Map.copyOf(profile);
    }

    private String environmentDigest(
            ValidationModel.ValidationTarget target, TargetAdapter adapter, FixtureHarness harness)
            throws Exception {
        Map<String, Object> environment = new TreeMap<>();
        environment.put("adapter_id", adapter.adapterId());
        environment.put("execution_profile", target.executionProfile());
        environment.put("policy_profile", target.policyProfile());
        environment.put("harness_id", harness.harnessId());
        environment.put("sandbox_mode",
                System.getenv().getOrDefault("ONSURE_FIXTURE_SANDBOX_MODE", "HOST_REVIEWED_ONLY"));
        environment.put("java_version", System.getProperty("java.version", "UNKNOWN"));
        environment.put("os_name", System.getProperty("os.name", "UNKNOWN"));
        environment.put("os_arch", System.getProperty("os.arch", "UNKNOWN"));
        return sha256(mapper.writeValueAsBytes(environment));
    }

    private static String metadata(
            TargetAdapter adapter, ValidationModel.ValidationTarget target, String key, String fallback) {
        try {
            Object value = adapter.collectTargetMetadata(target).get(key);
            return value instanceof String text && !text.isBlank() ? text : fallback;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String metadataDigest(
            TargetAdapter adapter, ValidationModel.ValidationTarget target, String key) {
        String value = metadata(adapter, target, key, "NOT_AVAILABLE");
        return value.matches("[0-9a-f]{64}") ? value : "NOT_AVAILABLE";
    }

    private void writeAtomic(Path outputFile, Object value) throws Exception {
        Path output = outputFile.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String requireId(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    /** Immutable input pair so the caller cannot accidentally mix target and adapter instances. */
    public record ValidationTargetBundle(
            ValidationModel.ValidationTarget target, TargetAdapter adapter) {
        public ValidationTargetBundle {
            if (target == null || adapter == null) throw new IllegalArgumentException("TARGET_BUNDLE_INVALID");
            if (!adapter.adapterId().equals(target.adapterId())) {
                throw new IllegalArgumentException("TARGET_ADAPTER_MISMATCH");
            }
        }
    }
}
