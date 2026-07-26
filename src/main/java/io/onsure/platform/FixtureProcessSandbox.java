package io.onsure.platform;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Builds a fail-closed process command for fixture execution. */
public final class FixtureProcessSandbox {
    public static final String REVIEWED_LOCAL_NONFINAL = "REVIEWED_LOCAL_NONFINAL";
    public static final String STRICT_BWRAP = "STRICT_BWRAP";
    private static final Set<String> ALLOWED = Set.of(REVIEWED_LOCAL_NONFINAL, STRICT_BWRAP);

    public record Plan(
            String profile,
            List<String> command,
            boolean networkIsolated,
            boolean filesystemReadOnly,
            boolean pidNamespaceIsolated,
            boolean resourceLimitsEnforced,
            String assuranceClass) {
        public Plan {
            command = List.copyOf(command);
        }
    }

    private FixtureProcessSandbox() {}

    public static Plan plan(
            String executionProfile,
            List<String> fixtureCommand,
            Path sourceRoot,
            Map<String, String> restrictedEnvironment) {
        String sandboxProfile = mapExecutionProfile(executionProfile);
        if (!ALLOWED.contains(sandboxProfile)) {
            throw new IllegalArgumentException("FIXTURE_SANDBOX_PROFILE_INVALID:" + sandboxProfile);
        }
        if (REVIEWED_LOCAL_NONFINAL.equals(sandboxProfile)) {
            return new Plan(
                    sandboxProfile, fixtureCommand, false, false, false, false,
                    "SELF_VALIDATION_NONFINAL");
        }

        Path bwrap = requireExecutable("bwrap");
        Path prlimit = requireExecutable("prlimit");
        Path root = sourceRoot.toAbsolutePath().normalize();
        List<String> command = new ArrayList<>();
        command.add(prlimit.toString());
        command.add("--as=536870912");
        command.add("--cpu=300");
        command.add("--nproc=64");
        command.add("--nofile=256");
        command.add("--fsize=67108864");
        command.add("--");
        command.add(bwrap.toString());
        command.addAll(List.of(
                "--die-with-parent",
                "--new-session",
                "--unshare-user",
                "--unshare-pid",
                "--unshare-net",
                "--unshare-ipc",
                "--unshare-uts",
                "--proc", "/proc",
                "--dev", "/dev",
                "--tmpfs", "/tmp",
                "--ro-bind", root.toString(), "/workspace",
                "--chdir", "/workspace",
                "--setenv", "HOME", "/tmp/home"));
        bindSystemPath(command, "/usr");
        bindSystemPath(command, "/bin");
        bindSystemPath(command, "/lib");
        bindSystemPath(command, "/lib64");
        bindSystemPath(command, "/etc/alternatives");
        String path = restrictedEnvironment.getOrDefault("PATH", "/usr/bin:/bin");
        command.addAll(List.of("--setenv", "PATH", path));
        for (Map.Entry<String, String> entry : restrictedEnvironment.entrySet()) {
            if ("PATH".equals(entry.getKey())) continue;
            command.addAll(List.of("--setenv", entry.getKey(), entry.getValue()));
        }
        command.addAll(fixtureCommand);
        return new Plan(
                STRICT_BWRAP, command, true, true, true, true,
                "SELF_VALIDATION_NONFINAL_SANDBOXED");
    }

    public static String mapExecutionProfile(String executionProfile) {
        if (FixtureRegistryStage.STRICT_SANDBOX_PROFILE.equals(executionProfile)) {
            return STRICT_BWRAP;
        }
        if (FixtureRegistryStage.TRUSTED_LOCAL_PROFILE.equals(executionProfile)
                || "LOCAL_E2E".equals(executionProfile)
                || "LOCAL_MVF_E2E".equals(executionProfile)) {
            return REVIEWED_LOCAL_NONFINAL;
        }
        throw new IllegalArgumentException(
                "EXECUTION_PROFILE_HAS_NO_SANDBOX_MAPPING:" + executionProfile);
    }

    private static void bindSystemPath(List<String> command, String value) {
        Path path = Path.of(value);
        if (Files.exists(path)) command.addAll(List.of("--ro-bind", value, value));
    }

    private static Path requireExecutable(String executable) {
        String pathValue = System.getenv("PATH");
        if (pathValue == null || pathValue.isBlank()) {
            throw new IllegalStateException("STRICT_SANDBOX_TOOL_MISSING:" + executable);
        }
        for (String segment : pathValue.split(java.io.File.pathSeparator)) {
            Path candidate = Path.of(segment).resolve(executable);
            if (Files.isRegularFile(candidate) && Files.isExecutable(candidate)) {
                return candidate.toAbsolutePath().normalize();
            }
        }
        throw new IllegalStateException("STRICT_SANDBOX_TOOL_MISSING:" + executable);
    }
}
