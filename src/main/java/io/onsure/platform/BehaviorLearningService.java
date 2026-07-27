package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.Decision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
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
    public static final String OBSERVATION_RECEIPT_CONTRACT = "ONSURE_BEHAVIOR_OBSERVATION_RECEIPT_V1";
    public static final String COVERAGE_PROXY = "EXECUTABLE_FIXTURE_PROCESS_PROXY";
    public static final String COVERAGE_DIRECT_FIXTURE = "DIRECT_INSTRUMENTED_FIXTURE_TELEMETRY";
    public static final String COVERAGE_DIRECT_PRODUCTION = "DIRECT_PRODUCTION_MODEL_TELEMETRY";
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
        String profileId = "BP-" + sourceDigest.substring(0, 16) + "-" + repetitions;
        Path output = outputFile.toAbsolutePath().normalize();
        Path receiptDirectory = output.resolveSibling(profileId + "-receipts");
        if (Files.exists(receiptDirectory)) {
            throw new IllegalStateException("BEHAVIOR_RECEIPT_DIRECTORY_ALREADY_EXISTS");
        }
        Files.createDirectories(receiptDirectory);

        FixtureHarness harness = new FixtureHarness("ONSURE_BEHAVIOR_LEARNING_HARNESS_V1");
        String environmentDigest = environmentDigest(target, adapter, harness);
        Map<String, Object> targetMetadata = adapter.collectTargetMetadata(target);
        boolean directFixtureTelemetry = Boolean.TRUE.equals(targetMetadata.get("direct_behavior_telemetry"));
        boolean productionTelemetry = directFixtureTelemetry
                && Boolean.TRUE.equals(targetMetadata.get("production_behavior_telemetry"))
                && metadataDigest(targetMetadata, "prompt_digest").matches("[0-9a-f]{64}")
                && metadataDigest(targetMetadata, "tool_registry_digest").matches("[0-9a-f]{64}")
                && !"NOT_DECLARED".equals(metadata(targetMetadata, "model_id", "NOT_DECLARED"));
        String coverageClass = productionTelemetry ? COVERAGE_DIRECT_PRODUCTION
                : directFixtureTelemetry ? COVERAGE_DIRECT_FIXTURE : COVERAGE_PROXY;
        String observationClass = productionTelemetry ? "DIRECT_PRODUCTION_MODEL"
                : directFixtureTelemetry ? "DIRECT_INSTRUMENTED_FIXTURE" : "PROCESS_COMMAND_PROXY";

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
                String receiptId = "BEH-" + sanitize(fixture.fixtureId()) + "-" + iteration + "-"
                        + outputDigest.substring(0, 12);
                Map<String, Object> receipt = new LinkedHashMap<>();
                receipt.put("contract", OBSERVATION_RECEIPT_CONTRACT);
                receipt.put("receipt_id", receiptId);
                receipt.put("profile_id", profileId);
                receipt.put("program_profile_id", requireId(programProfileId, "PROGRAM_PROFILE_ID_INVALID"));
                receipt.put("source_baseline_hash", sourceDigest);
                receipt.put("scenario_id", fixture.fixtureId());
                receipt.put("iteration", iteration);
                receipt.put("input_digest", inputDigest);
                receipt.put("output_digest", outputDigest);
                receipt.put("command", execution.command());
                receipt.put("decision", execution.result().decision().name());
                receipt.put("exit_code", execution.exitCode());
                receipt.put("timed_out", execution.timedOut());
                receipt.put("duration_ms", execution.durationMillis());
                receipt.put("environment_digest", environmentDigest);
                receipt.put("coverage_class", coverageClass);
                receipt.put("observation_class", observationClass);
                receipt.put("created_at", Instant.now().toString());
                receipt.put("final_claim_allowed", false);
                receipt.put("receipt_sha256", sha256(mapper.writeValueAsBytes(receipt)));
                Path receiptFile = receiptDirectory.resolve(receiptId + ".json");
                writeAtomic(receiptFile, receipt);
                String receiptFileSha = Hashing.file(receiptFile);

                Map<String, Object> observation = new LinkedHashMap<>();
                observation.put("scenario_id", fixture.fixtureId());
                observation.put("iteration", iteration);
                observation.put("input_digest", inputDigest);
                observation.put("output_digest", outputDigest);
                observation.put("tool_calls", execution.command());
                observation.put("tool_call_observation", observationClass);
                observation.put("decision", execution.result().decision().name());
                observation.put("duration_ms", execution.durationMillis());
                observation.put("run_receipt_id", receiptId);
                observation.put("run_receipt_path", receiptFile.toString());
                observation.put("run_receipt_file_sha256", receiptFileSha);
                observations.add(Map.copyOf(observation));
                outputByScenario.computeIfAbsent(fixture.fixtureId(), ignored -> new HashSet<>())
                        .add(outputDigest);
                allOutputs.add(outputDigest);
                evidenceRefs.add("receipt-file:sha256:" + receiptFileSha);
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
                .sorted().toList();
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", CONTRACT);
        profile.put("profile_id", profileId);
        profile.put("program_profile_id", requireId(programProfileId, "PROGRAM_PROFILE_ID_INVALID"));
        profile.put("source_baseline_hash", sourceDigest);
        profile.put("coverage_class", coverageClass);
        profile.put("direct_behavior_telemetry", directFixtureTelemetry);
        profile.put("production_behavior_telemetry", productionTelemetry);
        profile.put("runtime_context", Map.of(
                "harness_id", harness.harnessId(),
                "execution_profile", target.executionProfile(),
                "model_id", metadata(targetMetadata, "model_id", "NOT_DECLARED"),
                "model_version", metadata(targetMetadata, "model_version", "NOT_DECLARED"),
                "prompt_digest", metadataDigest(targetMetadata, "prompt_digest"),
                "tool_registry_digest", metadataDigest(targetMetadata, "tool_registry_digest"),
                "environment_digest", environmentDigest));
        profile.put("observations", List.copyOf(observations));
        profile.put("variability", Map.of(
                "repeated_run_count", repetitions,
                "scenario_count", fixtures.size(),
                "distinct_output_count", allOutputs.size(),
                "unstable_scenarios", unstable,
                "stable", unstable.isEmpty()));
        profile.put("failure_conditions", List.copyOf(failureConditions));
        profile.put("policy_violations", List.copyOf(policyViolations));
        profile.put("evidence_refs", List.copyOf(evidenceRefs));
        profile.put("receipt_directory", receiptDirectory.toString());
        profile.put("state", "BEHAVIOR_CANDIDATE");
        profile.put("generated_at", Instant.now().toString());
        profile.put("learning_method", "REPEATED_EXECUTABLE_FIXTURE_OBSERVATION_V1");
        profile.put("human_review", "NOT_RUN");
        profile.put("independent_validation", "NOT_RUN");
        profile.put("final_claim_allowed", false);
        profile.put("profile_sha256", sha256(mapper.writeValueAsBytes(profile)));
        writeAtomic(output, profile);
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

    private static String metadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        return value instanceof String text && !text.isBlank() ? text : fallback;
    }

    private static String metadataDigest(Map<String, Object> metadata, String key) {
        String value = metadata(metadata, key, "NOT_AVAILABLE");
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

    private static String sanitize(String value) {
        String sanitized = value.replaceAll("[^A-Za-z0-9._-]", "-");
        return sanitized.length() > 80 ? sanitized.substring(0, 80) : sanitized;
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
