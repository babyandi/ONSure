package io.onsure.platform;

import io.onsure.assurance.Decision;
import io.onsure.platform.TargetAdapter.FixtureDefinition;
import io.onsure.platform.ValidationModel.FixtureResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Executes trusted development fixtures through a named Oracle. */
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
            List<String> command) {
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
    private final Map<String, Oracle> oracles = new LinkedHashMap<>();

    public FixtureHarness(String harnessId) {
        if (harnessId == null || harnessId.isBlank()) throw new IllegalArgumentException("harnessId");
        this.harnessId = harnessId;
        register(new EqualsOracle());
        register(new ContainsOracle());
    }

    public String harnessId() { return harnessId; }
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
        if (!Files.isDirectory(normalizedRoot)) throw new IllegalArgumentException("fixture working directory missing");

        Instant started = Instant.now();
        String observed = fixture.declaredObserved();
        boolean executed = fixture.executable();
        int exitCode = 0;
        boolean timedOut = false;
        List<String> command = fixture.command();

        if (executed) {
            validateCommand(command, normalizedRoot);
            Path outputFile = Files.createTempFile("onsure-fixture-", ".log");
            try {
                ProcessBuilder builder = new ProcessBuilder(command)
                        .directory(normalizedRoot.toFile())
                        .redirectErrorStream(true)
                        .redirectOutput(outputFile.toFile());
                restrictEnvironment(builder.environment(), fixture.environment());
                Process process = builder.start();
                boolean completed = process.waitFor(fixture.timeoutSeconds(), TimeUnit.SECONDS);
                if (!completed) {
                    timedOut = true;
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                    exitCode = 124;
                } else {
                    exitCode = process.exitValue();
                }
                observed = readLimited(outputFile).strip();
            } finally {
                Files.deleteIfExists(outputFile);
            }
        }

        Decision oracleDecision = oracle.judge(fixture.expected(), observed);
        Decision decision = executed && (timedOut || exitCode != 0) ? Decision.FAIL : oracleDecision;
        FixtureResult result = new FixtureResult(
                fixture.fixtureId(), harnessId, oracle.oracleId(), fixture.expected(), observed,
                decision, Instant.now());
        return new HarnessExecution(
                result, executed, exitCode, timedOut,
                Duration.between(started, Instant.now()).toMillis(),
                Hashing.sha256(observed), command);
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
        if (characters > MAX_COMMAND_CHARACTERS) throw new IllegalArgumentException("fixture command too long");
        String executable = Path.of(command.get(0)).getFileName().toString();
        if (!ALLOWED_EXECUTABLES.contains(executable)) {
            throw new IllegalArgumentException("fixture executable not allowed: " + executable);
        }
        if (command.contains("-c") || command.contains("--command")) {
            throw new IllegalArgumentException("inline shell command is prohibited");
        }
        Path declaredScript = Path.of(command.get(1));
        if (declaredScript.isAbsolute()) throw new IllegalArgumentException("absolute fixture script is prohibited");
        Path script = root.resolve(declaredScript).normalize();
        if (!script.startsWith(root)
                || Files.isSymbolicLink(script)
                || !Files.isRegularFile(script, java.nio.file.LinkOption.NOFOLLOW_LINKS)
                || !script.toRealPath().startsWith(root.toRealPath())) {
            throw new IllegalArgumentException("fixture script must be a regular file inside target root");
        }
    }

    private static void restrictEnvironment(Map<String, String> processEnvironment,
            Map<String, String> fixtureEnvironment) {
        Map<String, String> host = System.getenv();
        processEnvironment.clear();
        for (String key : List.of("PATH", "JAVA_HOME", "LANG", "LC_ALL")) {
            String value = host.get(key);
            if (value != null) processEnvironment.put(key, value);
        }
        fixtureEnvironment.forEach((key, value) -> {
            if (!key.matches("ONSURE_FIXTURE_[A-Z0-9_]{1,64}")) {
                throw new IllegalArgumentException("fixture environment key not allowed: " + key);
            }
            processEnvironment.put(key, value);
        });
    }

    private static String readLimited(Path file) throws Exception {
        byte[] bytes = Files.readAllBytes(file);
        if (bytes.length > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("fixture output limit exceeded");
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
