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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Executes trusted development fixtures through a named Oracle. */
public final class FixtureHarness {
    private static final Set<String> ALLOWED_EXECUTABLES = Set.of("bash");
    private static final Set<String> ALLOWED_SANDBOX_BACKENDS = Set.of(
            "AUTO", "ROOTLESS_BWRAP", "OCI_DOCKER", "CI_SUDO_UNSHARE_BWRAP");
    private static final int MAX_ARGUMENTS = 64;
    private static final int MAX_COMMAND_CHARACTERS = 8192;
    private static final int MAX_OUTPUT_BYTES = 65_536;
    private static final String SANDBOX_ENV = "ONSURE_FIXTURE_SANDBOX_MODE";
    private static final String SANDBOX_BACKEND_ENV = "ONSURE_FIXTURE_SANDBOX_BACKEND";

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

    public static String sandboxMode() {
        return System.getenv().getOrDefault(SANDBOX_ENV, "HOST_REVIEWED_ONLY");
    }

    public static String sandboxBackend() {
        return System.getenv().getOrDefault(SANDBOX_BACKEND_ENV, "AUTO");
    }

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
        List<String> executedCommand = fixture.command();

        if (executed) {
            validateCommand(fixture.command(), normalizedRoot);
            executedCommand = sandboxedCommand(fixture, normalizedRoot);
            Path executionTemp = Files.createTempDirectory("onsure-fixture-execution-");
            Path outputFile = executionTemp.resolve("output.log");
            Path errorFile = executionTemp.resolve("error.log");
            try {
                ProcessBuilder builder = new ProcessBuilder(executedCommand)
                        .directory(normalizedRoot.toFile())
                        .redirectOutput(outputFile.toFile())
                        .redirectError(errorFile.toFile());
                restrictEnvironment(builder.environment(), fixture.environment(), executionTemp);
                Process process = builder.start();
                boolean completed = process.waitFor(fixture.timeoutSeconds() + 5L, TimeUnit.SECONDS);
                if (!completed) {
                    timedOut = true;
                    terminateProcessTree(process);
                    exitCode = 124;
                } else {
                    exitCode = process.exitValue();
                    terminateDescendants(process.toHandle());
                }
                String standardOutput = readLimited(outputFile).strip();
                String standardError = readLimited(errorFile).strip();
                observed = exitCode == 0 || !standardOutput.isBlank() ? standardOutput : standardError;
            } finally {
                deleteTemporaryTree(executionTemp);
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
                Hashing.sha256(observed), executedCommand);
    }

    private static List<String> sandboxedCommand(FixtureDefinition fixture, Path root) {
        String mode = sandboxMode();
        if ("HOST_REVIEWED_ONLY".equals(mode)) return fixture.command();
        if (!"REQUIRED".equals(mode)) {
            throw new IllegalArgumentException("unknown fixture sandbox mode: " + mode);
        }
        String backend = sandboxBackend();
        if (!ALLOWED_SANDBOX_BACKENDS.contains(backend)) {
            throw new IllegalArgumentException("unknown fixture sandbox backend: " + backend);
        }
        Path launcher = findSandboxLauncher();
        List<String> command = new ArrayList<>();
        command.add("bash");
        command.add(launcher.toString());
        command.add(root.toString());
        command.add(Integer.toString(fixture.timeoutSeconds()));
        command.addAll(fixture.command());
        return List.copyOf(command);
    }

    private static Path findSandboxLauncher() {
        Path current = Path.of(System.getProperty("user.dir", "."))
                .toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            Path candidate = current.resolve("scripts/fixture-sandbox-launcher.sh").normalize();
            if (Files.isRegularFile(candidate) && !Files.isSymbolicLink(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("FIXTURE_SANDBOX_LAUNCHER_MISSING");
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
        for (int index = 2; index < command.size(); index++) {
            String argument = command.get(index);
            if (argument.startsWith("/") || argument.contains("../") || argument.equals("..")) {
                throw new IllegalArgumentException("fixture argument path escape prohibited");
            }
        }
    }

    private static void restrictEnvironment(Map<String, String> processEnvironment,
            Map<String, String> fixtureEnvironment, Path executionTemp) {
        Map<String, String> host = System.getenv();
        processEnvironment.clear();
        for (String key : List.of(
                "PATH", "JAVA_HOME", "LANG", "LC_ALL", SANDBOX_ENV, SANDBOX_BACKEND_ENV,
                "CI", "GITHUB_ACTIONS")) {
            String value = host.get(key);
            if (value != null) processEnvironment.put(key, value);
        }
        processEnvironment.put("TMPDIR", executionTemp.toString());
        fixtureEnvironment.forEach((key, value) -> {
            if (!key.matches("ONSURE_FIXTURE_[A-Z0-9_]{1,64}")) {
                throw new IllegalArgumentException("fixture environment key not allowed: " + key);
            }
            processEnvironment.put(key, value);
        });
    }

    private static void deleteTemporaryTree(Path root) throws Exception {
        if (root == null || !Files.exists(root, java.nio.file.LinkOption.NOFOLLOW_LINKS)) return;
        try (var paths = Files.walk(root)) {
            for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private static void terminateProcessTree(Process process) throws InterruptedException {
        terminateDescendants(process.toHandle());
        process.destroyForcibly();
        process.waitFor(5, TimeUnit.SECONDS);
    }

    private static void terminateDescendants(ProcessHandle handle) {
        handle.descendants()
                .sorted((left, right) -> Long.compare(right.pid(), left.pid()))
                .forEach(child -> {
                    try { child.destroyForcibly(); } catch (Exception ignored) {}
                });
    }

    private static String readLimited(Path file) throws Exception {
        long size = Files.size(file);
        if (size > MAX_OUTPUT_BYTES) throw new IllegalArgumentException("fixture output limit exceeded");
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
