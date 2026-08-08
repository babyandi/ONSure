package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;
import kr.co.oruda.onsure.platform.DeploymentHealthMonitor.DeploymentHealthCheckFailedException;
import kr.co.oruda.onsure.platform.DeploymentHealthMonitor.GatedInstallResult;
import kr.co.oruda.onsure.platform.DeploymentHealthMonitor.HealthCheckResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeploymentHealthMonitorTest {
    @TempDir Path temp;

    @Test
    void healthyCheckOnFirstAttemptKeepsTheNewVersionActive() throws Exception {
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root"));
        Path v1 = buildPackage("healthy-v1");

        GatedInstallResult result = DeploymentHealthMonitor.installWithHealthGate(
                service, v1, "1.0.0", null,
                path -> new HealthCheckResult(true, "ok"), 3, Duration.ofMillis(1));

        assertEquals("1.0.0", result.installed().version());
        assertEquals(1, result.healthCheckAttempts());
        assertEquals("1.0.0", service.activeVersion());
    }

    @Test
    void unhealthyCheckAfterExhaustingAttemptsRollsBackAutomaticallyAndThrows() throws Exception {
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-rollback"));
        Path v1 = buildPackage("rollback-v1");
        Path v2 = buildPackage("rollback-v2");
        DeploymentHealthMonitor.installWithHealthGate(
                service, v1, "1.0.0", null, path -> new HealthCheckResult(true, "ok"), 1, Duration.ofMillis(1));
        assertEquals("1.0.0", service.activeVersion());

        AtomicInteger checks = new AtomicInteger();
        DeploymentHealthCheckFailedException failure = assertThrows(DeploymentHealthCheckFailedException.class,
                () -> DeploymentHealthMonitor.installWithHealthGate(
                        service, v2, "2.0.0", null,
                        path -> new HealthCheckResult(false, "attempt-" + checks.incrementAndGet()),
                        3, Duration.ofMillis(1)));

        assertEquals("2.0.0", failure.attemptedVersion());
        assertEquals("1.0.0", failure.rolledBackToVersion());
        assertEquals(3, checks.get(), "should have exhausted all 3 attempts before rolling back");
        assertEquals("1.0.0", service.activeVersion(), "active version must be back to the previous one");
        assertEquals(3, service.history().size(), "install v1, install v2, rollback to v1");
        assertEquals("ROLLBACK", service.history().get(2).get("event"));
    }

    @Test
    void unhealthyCheckWithNoPreviousVersionFailsClosedWithoutRollback() throws Exception {
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-first"));
        Path v1 = buildPackage("first-ever-v1");

        assertThrows(IllegalStateException.class, () -> DeploymentHealthMonitor.installWithHealthGate(
                service, v1, "1.0.0", null, path -> new HealthCheckResult(false, "never healthy"),
                2, Duration.ofMillis(1)));
        assertEquals(1, service.history().size(), "no rollback entry should be added since there's nothing to roll back to");
    }

    @Test
    void healthCheckReceivesTheActiveInstallationPathWithRealFiles() throws Exception {
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-path"));
        Path v1 = buildPackage("path-check-v1");

        DeploymentHealthMonitor.installWithHealthGate(
                service, v1, "1.0.0", null,
                path -> new HealthCheckResult(Files.isRegularFile(path.resolve("onsure.jar")), "checked jar presence"),
                1, Duration.ofMillis(1));
        assertEquals("1.0.0", service.activeVersion());
    }

    @Test
    void rejectsInvalidAttemptsAndRetryInterval() throws Exception {
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-invalid"));
        Path v1 = buildPackage("invalid-v1");
        assertThrows(IllegalArgumentException.class, () -> DeploymentHealthMonitor.installWithHealthGate(
                service, v1, "1.0.0", null, path -> new HealthCheckResult(true, "ok"), 0, Duration.ofMillis(1)));
    }

    @Test
    void commandHealthCheckReportsExitCodeAndOutput() throws Exception {
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-command"));
        Path v1 = buildPackage("command-v1");
        Files.writeString(v1.resolve("health-check.sh"), "#!/bin/bash\necho all-good\nexit 0\n");

        GatedInstallResult result = DeploymentHealthMonitor.installWithHealthGate(
                service, v1, "1.0.0", null,
                DeploymentHealthMonitor.commandHealthCheck(java.util.List.of("bash", "health-check.sh"), Duration.ofSeconds(5)),
                1, Duration.ofMillis(1));
        assertEquals(1, result.healthCheckAttempts());
        assertTrue(result.finalHealthDetail().contains("exit_code=0"));
    }

    private Path buildPackage(String label) throws Exception {
        Path source = temp.resolve("source-" + label);
        Files.createDirectories(source);
        Files.writeString(source.resolve("onsure.jar"), "jar-bytes-" + label);
        Path packageDir = temp.resolve("package-" + label);
        DeploymentPackageBuilder.build(source, packageDir, DeploymentProfile.ON_PREMISES, null, null);
        return packageDir;
    }
}
