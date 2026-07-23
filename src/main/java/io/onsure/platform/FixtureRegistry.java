package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.TargetAdapter.FixtureDefinition;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Seals executable fixture definitions and the Harness/Oracle/Command registries for a run. */
public final class FixtureRegistry {
    public static final String CONTRACT = "ONSURE_FIXTURE_REGISTRY_V1";
    public static final String COMMAND_MANIFEST_CONTRACT = "ONSURE_ORUDA_HARNESS_COMMAND_MANIFEST_V1";
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);

    public void persist(Path runRoot, String targetId, List<FixtureDefinition> fixtures,
            String harnessId, Set<String> oracleIds) throws Exception {
        persist(runRoot, targetId, Path.of("."), fixtures, harnessId, oracleIds);
    }

    public void persist(Path runRoot, String targetId, Path workingDirectory,
            List<FixtureDefinition> fixtures, String harnessId, Set<String> oracleIds) throws Exception {
        Map<String, Object> fixtureRegistry = new LinkedHashMap<>();
        fixtureRegistry.put("contract", CONTRACT);
        fixtureRegistry.put("target_id", targetId);
        fixtureRegistry.put("registered_at", Instant.now().toString());
        fixtureRegistry.put("fixtures", fixtures);
        write(runRoot.resolve("fixture-registry.json"), fixtureRegistry);

        Map<String, Object> oracleRegistry = new LinkedHashMap<>();
        oracleRegistry.put("contract", "ONSURE_ORACLE_REGISTRY_V1");
        oracleRegistry.put("harness_id", harnessId);
        oracleRegistry.put("oracle_ids", oracleIds.stream().sorted().toList());
        oracleRegistry.put("registered_at", Instant.now().toString());
        write(runRoot.resolve("oracle-registry.json"), oracleRegistry);

        List<Map<String, Object>> commands = new ArrayList<>();
        for (FixtureDefinition fixture : fixtures) {
            if (!fixture.executable()) continue;
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("fixture_id", fixture.fixtureId());
            entry.put("command", fixture.command());
            entry.put("timeout_seconds", fixture.timeoutSeconds());
            entry.put("oracle_id", fixture.oracleId());
            entry.put("expected_result", fixture.expected());
            entry.put("expected_exit_codes", List.of(0));
            entry.put("output_parser", "UTF8_STDOUT_STRIP");
            entry.put("environment_keys", fixture.environment().keySet().stream().sorted().toList());
            entry.put("receipt_binding", List.of(
                    "COMMAND", "TIMEOUT", "EXIT_CODE", "TIMED_OUT", "OUTPUT_SHA256",
                    "ORACLE_ID", "EXPECTED_RESULT", "ACTUAL_RESULT"));
            commands.add(Map.copyOf(entry));
        }
        if (!commands.isEmpty()) {
            Map<String, Object> commandManifest = new LinkedHashMap<>();
            commandManifest.put("contract", COMMAND_MANIFEST_CONTRACT);
            commandManifest.put("target_id", targetId);
            commandManifest.put("harness_id", harnessId);
            commandManifest.put("working_directory", workingDirectory.toAbsolutePath().normalize().toString());
            commandManifest.put("allowed_executables", List.of("bash"));
            commandManifest.put("network_policy", "DENY_BY_DEFAULT");
            commandManifest.put("created_at", Instant.now().toString());
            commandManifest.put("entries", commands);
            write(runRoot.resolve("harness-command-manifest.json"), commandManifest);
        }
    }

    private void write(Path file, Object value) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
