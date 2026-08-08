package kr.co.oruda.onsure.platform;

import java.nio.file.Path;
import java.security.PublicKey;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Health-check-triggered automatic rollback on top of {@link DeploymentInstallationService}
 * (DEPLOYMENT: NO_HEALTH_CHECK_TRIGGERED_AUTOMATIC_ROLLBACK). Installs a new version, then runs a
 * caller-supplied health check against the newly active installation; if it never reports healthy
 * within the attempt budget, automatically rolls back to whatever version was active immediately
 * before this install and fails closed by throwing -- callers never observe a "successful" install
 * that silently rolled itself back without being told.
 */
public final class DeploymentHealthMonitor {

    @FunctionalInterface
    public interface HealthCheck {
        HealthCheckResult check(Path activeInstallationPath) throws Exception;
    }

    public record HealthCheckResult(boolean healthy, String detail) {}

    public record GatedInstallResult(
            DeploymentInstallationService.InstalledVersion installed,
            int healthCheckAttempts,
            String finalHealthDetail) {}

    public static final class DeploymentHealthCheckFailedException extends RuntimeException {
        private final String attemptedVersion;
        private final String rolledBackToVersion;

        DeploymentHealthCheckFailedException(String attemptedVersion, String rolledBackToVersion, String detail) {
            super("DEPLOYMENT_HEALTH_CHECK_FAILED:" + attemptedVersion
                    + ":rolled_back_to:" + rolledBackToVersion + ":" + detail);
            this.attemptedVersion = attemptedVersion;
            this.rolledBackToVersion = rolledBackToVersion;
        }

        public String attemptedVersion() { return attemptedVersion; }
        public String rolledBackToVersion() { return rolledBackToVersion; }
    }

    private DeploymentHealthMonitor() {}

    public static GatedInstallResult installWithHealthGate(
            DeploymentInstallationService service,
            Path packageDir,
            String version,
            PublicKey verificationKey,
            HealthCheck healthCheck,
            int maxAttempts,
            Duration retryInterval) throws Exception {
        if (service == null) throw new IllegalArgumentException("DEPLOYMENT_HEALTH_GATE_SERVICE_REQUIRED");
        if (healthCheck == null) throw new IllegalArgumentException("DEPLOYMENT_HEALTH_GATE_CHECK_REQUIRED");
        if (maxAttempts < 1) throw new IllegalArgumentException("DEPLOYMENT_HEALTH_GATE_ATTEMPTS_INVALID");
        if (retryInterval == null || retryInterval.isNegative()) {
            throw new IllegalArgumentException("DEPLOYMENT_HEALTH_GATE_RETRY_INTERVAL_INVALID");
        }

        String previousVersion = readActiveVersionOrNull(service);
        DeploymentInstallationService.InstalledVersion installed = service.install(packageDir, version, verificationKey);

        HealthCheckResult result = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            result = healthCheck.check(service.activeInstallationPath());
            if (result.healthy()) {
                return new GatedInstallResult(installed, attempt, result.detail());
            }
            if (attempt < maxAttempts) Thread.sleep(retryInterval.toMillis());
        }

        String detail = result == null ? "NO_HEALTH_CHECK_RESULT" : result.detail();
        if (previousVersion == null) {
            throw new IllegalStateException(
                    "DEPLOYMENT_HEALTH_CHECK_FAILED_NO_PREVIOUS_VERSION_TO_ROLL_BACK_TO:" + version + ":" + detail);
        }
        service.rollback(previousVersion, verificationKey);
        throw new DeploymentHealthCheckFailedException(version, previousVersion, detail);
    }

    private static String readActiveVersionOrNull(DeploymentInstallationService service) throws Exception {
        try {
            return service.activeVersion();
        } catch (IllegalStateException noVersionInstalledYet) {
            return null;
        }
    }

    /** Runs a health-check command (relative to the active installation directory) via BoundedProcessRunner. */
    public static HealthCheck commandHealthCheck(List<String> relativeCommand, Duration timeout) {
        if (relativeCommand == null || relativeCommand.isEmpty()) {
            throw new IllegalArgumentException("DEPLOYMENT_HEALTH_CHECK_COMMAND_REQUIRED");
        }
        return activeInstallationPath -> {
            List<String> command = new java.util.ArrayList<>();
            command.add(relativeCommand.get(0)); // executable name: resolved via PATH, not relative to the install dir
            for (String argument : relativeCommand.subList(1, relativeCommand.size())) {
                command.add(argument.startsWith("/") || argument.startsWith("-")
                        ? argument : activeInstallationPath.resolve(argument).toString());
            }
            Map<String, String> environment = new LinkedHashMap<>();
            String path = System.getenv("PATH");
            if (path != null) environment.put("PATH", path);
            BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    command, activeInstallationPath, timeout, environment, "DEPLOYMENT_HEALTH_CHECK");
            return new HealthCheckResult(result.exitCode() == 0, "exit_code=" + result.exitCode()
                    + ":output=" + truncate(result.output()));
        };
    }

    private static String truncate(String value) {
        return value.length() > 500 ? value.substring(0, 500) + "..." : value;
    }
}
