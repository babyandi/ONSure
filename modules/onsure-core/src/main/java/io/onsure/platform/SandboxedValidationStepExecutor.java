package io.onsure.platform;

import io.onsure.platform.UniversalValidationProfile.Outcome;
import io.onsure.platform.UniversalValidationProfile.Step;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Executes a detected command only through the no-network writable-snapshot sandbox. */
final class SandboxedValidationStepExecutor implements UniversalValidationRunner.StepExecutor {
    private static final String LAUNCHER = "scripts/validation-sandbox-launcher.sh";
    private static final Path ENVIRONMENT_PROBE = Path.of(
            ".onsure", "internal", "environment-probe.sh");
    private static final String ENVIRONMENT_PROBE_SCRIPT = """
            #!/usr/bin/env bash
            set -euo pipefail
            while [[ $# -gt 0 ]]; do
              kind="$1"
              shift
              [[ $# -gt 0 ]] || { echo 'ONSURE_ENVIRONMENT_PROBE_ARGUMENT_MISSING' >&2; exit 64; }
              value="$1"
              shift
              case "$kind" in
                --executable)
                  command -v "$value" >/dev/null 2>&1 || {
                    echo "ONSURE_ENVIRONMENT_PROBE_MISSING executable:$value" >&2
                    exit 69
                  }
                  ;;
                --font)
                  command -v fc-match >/dev/null 2>&1 || {
                    echo 'ONSURE_ENVIRONMENT_PROBE_MISSING executable:fc-match' >&2
                    exit 69
                  }
                  actual="$(fc-match --format '%{family}\n' "$value" 2>/dev/null || true)"
                  [[ "${actual,,}" == *"${value,,}"* ]] || {
                    echo "ONSURE_ENVIRONMENT_PROBE_MISSING font:$value" >&2
                    exit 69
                  }
                  ;;
                *) echo 'ONSURE_ENVIRONMENT_PROBE_KIND_INVALID' >&2; exit 64 ;;
              esac
            done
            echo 'ONSURE_ENVIRONMENT_PROBE_PASS'
            """;

    @Override
    public UniversalValidationRunner.StepExecution execute(Step step, Path snapshotRoot) {
        Path launcher;
        try {
            launcher = findLauncher();
        } catch (Exception error) {
            return blocked("VALIDATION_SANDBOX_LAUNCHER_MISSING");
        }
        List<String> command = new ArrayList<>();
        command.add(hostBash().toString());
        command.add(launcher.toString());
        command.add(snapshotRoot.toString());
        command.add(Long.toString(step.timeout().toSeconds()));
        command.addAll(step.command());
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put("PATH", "/usr/sbin:/usr/bin:/sbin:/bin");
        if (step.stepId().startsWith("reviewed.")) {
            environment.put("ONSURE_REVIEWED_EXECUTION", "1");
        }
        Path maven = Path.of(System.getProperty("user.home", "/nonexistent"), ".m2", "repository");
        if (Files.isDirectory(maven) && !Files.isSymbolicLink(maven)) {
            environment.put("ONSURE_MAVEN_CACHE", maven.toAbsolutePath().normalize().toString());
        }
        Path npm = Path.of(System.getProperty("user.home", "/nonexistent"), ".npm");
        if (Files.isDirectory(npm) && !Files.isSymbolicLink(npm)) {
            environment.put("ONSURE_NPM_CACHE", npm.toAbsolutePath().normalize().toString());
        }
        try {
            BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    command, snapshotRoot, step.timeout().plus(Duration.ofSeconds(10)),
                    BoundedProcessRunner.DEFAULT_MAX_OUTPUT_BYTES, environment,
                    "UNIVERSAL_VALIDATION_" + step.stepId().toUpperCase().replaceAll("[^A-Z0-9]", "_"));
            String output = result.output();
            if (result.exitCode() == 0) {
                return new UniversalValidationRunner.StepExecution(
                        Outcome.PASS_NONFINAL, result.exitCode(), output, result.outputTruncated(), "EXECUTED");
            }
            if (output.contains("ONSURE_VALIDATION_SANDBOX_FAIL")
                    || output.contains("bwrap: No permissions")
                    || output.startsWith("bwrap:")
                    || output.contains("Operation not permitted")) {
                return new UniversalValidationRunner.StepExecution(
                        Outcome.BLOCKED, result.exitCode(), output, result.outputTruncated(),
                        "SANDBOX_UNAVAILABLE_OR_DENIED");
            }
            if (output.contains("Cannot access") && output.contains("offline mode")
                    || output.contains("has not been downloaded from it before")
                    || output.contains("ENOTCACHED")
                    || output.contains("cache mode is 'only-if-cached'")) {
                return new UniversalValidationRunner.StepExecution(
                        Outcome.BLOCKED, result.exitCode(), output, result.outputTruncated(),
                        "OFFLINE_DEPENDENCY_CACHE_INCOMPLETE");
            }
            return new UniversalValidationRunner.StepExecution(
                    Outcome.FAIL, result.exitCode(), output, result.outputTruncated(), "COMMAND_EXIT_NONZERO");
        } catch (Exception error) {
            return new UniversalValidationRunner.StepExecution(
                    Outcome.BLOCKED, -1, "", false,
                    "SANDBOX_EXECUTION_ERROR:" + error.getClass().getSimpleName());
        }
    }

    @Override
    public UniversalValidationRunner.StepExecution probe(Path snapshotRoot) {
        return probe(snapshotRoot, List.of(), List.of());
    }

    @Override
    public UniversalValidationRunner.StepExecution probe(
            Path snapshotRoot,
            List<String> requiredExecutables,
            List<UniversalValidationProfile.EnvironmentRequirement> requirements) {
        Path launcher;
        try {
            launcher = findLauncher();
            Path root = snapshotRoot.toAbsolutePath().normalize();
            Path probe = root.resolve(ENVIRONMENT_PROBE).normalize();
            if (!probe.startsWith(root) || Files.exists(probe, LinkOption.NOFOLLOW_LINKS)) {
                return blocked("ENVIRONMENT_PROBE_PATH_UNSAFE");
            }
            Files.createDirectories(probe.getParent());
            Files.writeString(probe, ENVIRONMENT_PROBE_SCRIPT,
                    StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            Map<String, String> environment = new LinkedHashMap<>();
            environment.put("PATH", "/usr/sbin:/usr/bin:/sbin:/bin");
            environment.put("ONSURE_SANDBOX_PROBE", "1");
            List<String> sandboxCommand = new ArrayList<>(List.of(
                    "bash", ENVIRONMENT_PROBE.toString().replace('\\', '/')));
            requiredExecutables.stream().distinct().sorted().forEach(executable -> {
                sandboxCommand.add("--executable");
                sandboxCommand.add(executable);
            });
            requirements.stream()
                    .filter(UniversalValidationProfile.EnvironmentRequirement::required)
                    .filter(requirement -> requirement.kind()
                            == UniversalValidationProfile.RequirementKind.EXECUTABLE)
                    .map(UniversalValidationProfile.EnvironmentRequirement::value)
                    .distinct().sorted().forEach(executable -> {
                        sandboxCommand.add("--executable");
                        sandboxCommand.add(executable);
                    });
            requirements.stream()
                    .filter(UniversalValidationProfile.EnvironmentRequirement::required)
                    .filter(requirement -> requirement.kind()
                            == UniversalValidationProfile.RequirementKind.FONT_FAMILY)
                    .map(UniversalValidationProfile.EnvironmentRequirement::value)
                    .distinct().sorted().forEach(font -> {
                        sandboxCommand.add("--font");
                        sandboxCommand.add(font);
                    });
            List<String> command = new ArrayList<>(List.of(
                    hostBash().toString(), launcher.toString(), root.toString(), "15"));
            command.addAll(sandboxCommand);
            BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    command, root, Duration.ofSeconds(20), BoundedProcessRunner.DEFAULT_MAX_OUTPUT_BYTES,
                    environment, "UNIVERSAL_VALIDATION_SANDBOX_PROBE");
            if (result.exitCode() == 0) {
                return new UniversalValidationRunner.StepExecution(
                        Outcome.PASS_NONFINAL, 0, result.output(), result.outputTruncated(), "SANDBOX_PROBE_PASS");
            }
            if (result.output().contains("ONSURE_ENVIRONMENT_PROBE_MISSING")) {
                return new UniversalValidationRunner.StepExecution(
                        Outcome.BLOCKED, result.exitCode(), result.output(), result.outputTruncated(),
                        "SANDBOX_ENVIRONMENT_REQUIREMENT_MISSING");
            }
            return new UniversalValidationRunner.StepExecution(
                    Outcome.BLOCKED, result.exitCode(), result.output(), result.outputTruncated(),
                    "SANDBOX_UNAVAILABLE_OR_DENIED");
        } catch (Exception error) {
            return blocked("SANDBOX_PROBE_ERROR:" + error.getClass().getSimpleName());
        }
    }

    private static UniversalValidationRunner.StepExecution blocked(String reason) {
        return new UniversalValidationRunner.StepExecution(Outcome.BLOCKED, -1, "", false, reason);
    }

    static boolean supportsCommand(Step step) {
        if (!step.executable()) return true;
        String executable = Path.of(step.command().get(0)).getFileName().toString();
        return switch (executable) {
            case "mvn" -> step.command().contains("-o");
            case "python3" -> step.command().size() >= 3 && "-m".equals(step.command().get(1))
                    && ("pytest".equals(step.command().get(2)) || "unittest".equals(step.command().get(2)))
                    || reviewedScript(step, ".py");
            case "npm" -> step.command().contains("--offline");
            case "bash" -> step.command().size() >= 2 && "gradlew".equals(step.command().get(1))
                    && step.command().contains("--offline") || reviewedScript(step, ".sh");
            case "node" -> reviewedScript(step, ".js");
            default -> false;
        };
    }

    private static boolean reviewedScript(Step step, String suffix) {
        if (!step.stepId().startsWith("reviewed.") || step.command().size() < 2) return false;
        String value = step.command().get(1).replace('\\', '/');
        Path path = Path.of(value).normalize();
        return value.endsWith(suffix) && !value.startsWith("-")
                && !path.isAbsolute() && !path.startsWith("..");
    }

    private static Path findLauncher() {
        Path current = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath().normalize();
        for (int depth = 0; current != null && depth < 8; depth++, current = current.getParent()) {
            Path candidate = current.resolve(LAUNCHER).normalize();
            if (Files.isRegularFile(candidate) && !Files.isSymbolicLink(candidate)) return candidate;
        }
        throw new IllegalStateException("VALIDATION_SANDBOX_LAUNCHER_MISSING");
    }

    private static Path hostBash() {
        for (Path candidate : List.of(Path.of("/usr/bin/bash"), Path.of("/bin/bash"))) {
            try {
                if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) return candidate.toRealPath();
            } catch (Exception ignored) { /* try the next fixed system path */ }
        }
        throw new IllegalStateException("VALIDATION_HOST_BASH_MISSING");
    }
}
