package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.TargetAdapter.FixtureDefinition;
import io.onsure.platform.ValidationModel.FixtureResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Executes reviewed fixtures through a declared sandbox profile and named Oracle. */
public final class FixtureHarness {
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of("bash");
    private static final int MAX_ARGUMENTS = 64;
    private static final int MAX_COMMAND_CHARACTERS = 8192;
    private static final int MAX_OUTPUT_BYTES = 65_536;

    public interface Oracle {
        String oracleId();
        Decision judge(String expected, String observed);
    }

    public record HarnessExecution(
            FixtureResult result,
            boolean commandExecuted,
            int exitCode,
            boolean timedOut,
            long durationMillis,
            String outputSha256,
            List<String> command,
            String sandboxProfile,
            boolean networkIsolated,
            boolean filesystemReadOnly,
            boolean pidNamespaceIsolated,
            boolean resourceLimitsEnforced,
            String assuranceClass) {
        public HarnessExecution { command = List.copyOf(command); }
    }

    public static final class EqualsOracle implements Oracle {
        @Override public String oracleId() { return "EQUALS"; }
        @Override public Decision judge(String expected, String observed) {
            return java.util.Objects.equals(expected, observed) ? Decision.PASS : Decision.FAIL;
        }
    }

    public static final class ContainsOracle implements Oracle {
        @Override public String oracleId() { return "CONTAINS"; }
        @Override public Decision judge(String expected, String observed) {
            return observed != null && expected != null && observed.contains(expected)
                    ? Decision.PASS : Decision.FAIL;
        }
    }

    private final String harnessId;
    private final String executionProfile;
    private final Map<String, Oracle> oracles = new LinkedHashMap<>();

    public FixtureHarness(String harnessId) {
        this(harnessId, FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
    }

    public FixtureHarness(String harnessId, String executionProfile) {
        if (harnessId == null || harnessId.isBlank()) throw new IllegalArgumentException("harnessId");
        if (executionProfile == null || executionProfile.isBlank()) {
            throw new IllegalArgumentException("executionProfile");
        }
        this.harnessId = harnessId;
        this.executionProfile = executionProfile;
        register(new EqualsOracle());
        register(new ContainsOracle());
    }

    public String harnessId() { return harnessId; }
    public String executionProfile() { return executionProfile; }
    public Set<String> oracleIds() { return Set.copyOf(oracles.keySet()); }

    public void register(Oracle oracle) {
        if (oracle == null || oracle.oracleId() == null || oracle.oracleId().isBlank()) {
            throw new IllegalArgumentException("invalid oracle");
        }
        if (oracles.putIfAbsent(oracle.oracleId(), oracle) != null) {
            throw new IllegalArgumentException("duplicate oracle: " + oracle.oracleId());
        }
    }

    public HarnessExecution execute(FixtureDefinition fixture, Path workingDirectory) throws Exception {
        Oracle oracle = oracles.get(fixture.oracleId());
        if (oracle == null) throw new IllegalArgumentException("unknown oracle: " + fixture.oracleId());
        Path normalizedRoot = workingDirectory.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalizedRoot)) {
            throw new IllegalArgumentException("fixture working directory missing");
        }

        Instant started = Instant.now();
        String observed = fixture.declaredObserved();
        boolean executed = fixture.executable();
        int exitCode = 0;
        boolean timedOut = false;
        List<String> originalCommand = fixture.command();
        Map<String, String> restrictedEnvironment = restrictedEnvironment(fixture.environment());
        FixtureProcessSandbox.Plan sandbox = new FixtureProcessSandbox.Plan(
                FixtureProcessSandbox.mapExecutionProfile(executionProfile),
                originalCommand, false, false, false, false, "SELF_VALIDATION_NONFINAL");

        if (executed) {
            validateCommand(originalCommand, normalizedRoot);
            sandbox = FixtureProcessSandbox.plan(
                    executionProfile, originalCommand, normalizedRoot, restrictedEnvironment);
            Path outputFile = Files.createTempFile("onsure-fixture-", ".log");
            Process process = null;
            try {
                ProcessBuilder builder = new ProcessBuilder(sandbox.command())
                        .directory(normalizedRoot.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(outputFile.toFile());
                builder.environment().clear();
                if (FixtureProcessSandbox.REVIEWED_LOCAL_NONFINAL.equals(sandbox.profile())) {
                    builder.environment().putAll(restrictedEnvironment);
                }
                process = builder.start();
                boolean completed = process.waitFor(fixture.timeoutSeconds(), TimeUnit.SECONDS);
                if (!completed) {
                    timedOut = true;
                    destroyProcessTree(process);
                    exitCode = 124;
                } else {
                    exitCode = process.exitValue();
                }
                observed = readLimited(outputFile).strip();
            } finally {
                if (process != null && process.isAlive()) destroyProcessTree(process);
                Files.deleteIfExists(outputFile);
            }
        }

        Decision oracleDecision = oracle.judge(fixture.expected(), observed);
        Decision decision = executed && (timedOut || exitCode != 0)
                ? Decision.FAIL : oracleDecision;
        FixtureResult result = new FixtureResult(
                fixture.fixtureId(), harnessId, oracle.oracleId(), fixture.expected(), observed,
                decision, Instant.now());
        return new HarnessExecution(
                result, executed, exitCode, timedOut,
                Duration.between(started, Instant.now()).toMillis(),
                Hashing.sha256(observed), originalCommand, sandbox.profile(),
                sandbox.networkIsolated(), sandbox.filesystemReadOnly(),
                sandbox.pidNamespaceIsolated(), sandbox.resourceLimitsEnforced(),
                sandbox.assuranceClass());
    }

    private static void validateCommand(List<String> command, Path root) throws Exception {
        if (command.size() < 2 || command.size() > MAX_ARGUMENTS) {
            throw new IllegalArgumentException("fixture command size invalid");
        }
        if (command.stream().anyMatch(value -> value.indexOf('\u0000') >= 0
                || value.indexOf('\n') >= 0 || value.indexOf('\r') >= 0)) {
            throw new IllegalArgumentException("fixture command contains control characters");
        }
        int characters = command.stream().mapToInt(String::length).sum();
        if (characters > MAX_COMMAND_CHARACTERS) {
            throw new IllegalArgumentException("fixture command too long");
        }
        String executable = Path.of(command.get(0)).getFileName().toString();
        if (!ALLOWED_EXECUTABLES.contains(executable)) {
            throw new IllegalArgumentException("fixture executable not allowed: " + executable);
        }
        if (command.contains("-c") || command.contains("--command")) {
            throw new IllegalArgumentException("inline shell command is prohibited");
        }
        Path declaredScript = Path.of(command.get(1));
        if (declaredScript.isAbsolute()) {
            throw new IllegalArgumentException("absolute fixture script is prohibited");
        }
        Path script = root.resolve(declaredScript).normalize();
        if (!script.startsWith(root)
                || Files.isSymbolicLink(script)
                || !Files.isRegularFile(script, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !script.toRealPath().startsWith(root.toRealPath())) {
            throw new IllegalArgumentException(
                    "fixture script must be a regular file inside target root");
        }
    }

    private static Map<String, String> restrictedEnvironment(
            Map<String, String> fixtureEnvironment) {
        Map<String, String> environment = new LinkedHashMap<>();
        Map<String, String> host = System.getenv();
        for (String key : List.of("PATH", "JAVA_HOME", "LANG", "LC_ALL")) {
            String value = host.get(key);
            if (value != null) environment.put(key, value);
        }
        fixtureEnvironment.forEach((key, value) -> {
            if (!key.matches("ONSURE_FIXTURE_[A-Z0-9_]{1,64}")) {
                throw new IllegalArgumentException(
                        "fixture environment key not allowed: " + key);
            }
            environment.put(key, value);
        });
        return Map.copyOf(environment);
    }

    private static void destroyProcessTree(Process process) {
        List<ProcessHandle> descendants = new ArrayList<>(process.descendants().toList());
        descendants.sort(Comparator.comparingLong(ProcessHandle::pid).reversed());
        for (ProcessHandle handle : descendants) handle.destroy();
        process.destroy();
        try {
            if (!process.waitFor(2, TimeUnit.SECONDS)) {
                for (ProcessHandle handle : descendants) {
                    if (handle.isAlive()) handle.destroyForcibly();
                }
                process.destroyForcibly();
                process.waitFor(3, TimeUnit.SECONDS);
            }
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            for (ProcessHandle handle : descendants) {
                if (handle.isAlive()) handle.destroyForcibly();
            }
            process.destroyForcibly();
        }
    }

    private static String readLimited(Path file) throws Exception {
        long size = Files.size(file);
        if (size > MAX_OUTPUT_BYTES) {
            throw new IllegalArgumentException("fixture output limit exceeded");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
