package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SandboxedValidationStepExecutorTest {
    @TempDir Path temp;

    @Test
    void launcherEnvironmentPropagatesOnlyValidatedSandboxConfiguration() {
        Map<String, String> environment = SandboxedValidationStepExecutor.launcherEnvironment(
                temp.toAbsolutePath(),
                Map.of(
                        "ONSURE_VALIDATION_SANDBOX_BACKEND", "OCI_DOCKER",
                        "ONSURE_VALIDATION_OCI_IMAGE", "runtime@example/validation@sha256:abc",
                        "ONSURE_TEMP_ROOT", temp.toAbsolutePath().toString(),
                        "AWS_SECRET_ACCESS_KEY", "must-not-leak"));

        assertEquals("OCI_DOCKER", environment.get("ONSURE_VALIDATION_SANDBOX_BACKEND"));
        assertEquals("runtime@example/validation@sha256:abc", environment.get("ONSURE_VALIDATION_OCI_IMAGE"));
        assertEquals(temp.toAbsolutePath().toString(), environment.get("ONSURE_TEMP_ROOT"));
        assertEquals(temp.toAbsolutePath().toString(), environment.get("TMPDIR"));
        assertFalse(environment.containsKey("AWS_SECRET_ACCESS_KEY"));
    }

    @Test
    void launcherEnvironmentUsesSnapshotInsteadOfAmbientTmpByDefault() {
        Map<String, String> environment = SandboxedValidationStepExecutor.launcherEnvironment(
                temp.toAbsolutePath(), Map.of("TMPDIR", "/tmp", "TOKEN", "secret"));

        assertEquals(temp.toAbsolutePath().toString(), environment.get("ONSURE_TEMP_ROOT"));
        assertEquals(temp.toAbsolutePath().toString(), environment.get("TMPDIR"));
        assertFalse(environment.containsKey("TOKEN"));
    }

    @Test
    void launcherEnvironmentRejectsInvalidBackendImageAndTempRoot() {
        assertThrows(IllegalArgumentException.class, () -> SandboxedValidationStepExecutor.launcherEnvironment(
                temp.toAbsolutePath(), Map.of("ONSURE_VALIDATION_SANDBOX_BACKEND", "HOST")));
        assertThrows(IllegalArgumentException.class, () -> SandboxedValidationStepExecutor.launcherEnvironment(
                temp.toAbsolutePath(), Map.of("ONSURE_VALIDATION_OCI_IMAGE", "image;touch /tmp/x")));
        assertThrows(IllegalArgumentException.class, () -> SandboxedValidationStepExecutor.launcherEnvironment(
                temp.toAbsolutePath(), Map.of("ONSURE_TEMP_ROOT", "relative")));
    }

    @Test
    void generatedPythonCompileCheckIsAcceptedButArbitraryModuleIsDenied() {
        UniversalValidationProfile.Step compile = new UniversalValidationProfile.Step(
                "python.compile", UniversalValidationProfile.Phase.STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.STATIC_ANALYSIS, true,
                java.util.List.of("python3", "-m", "compileall", "-q", "."), Path.of("."),
                java.time.Duration.ofSeconds(30), java.util.List.of());
        UniversalValidationProfile.Step arbitrary = new UniversalValidationProfile.Step(
                "python.arbitrary", UniversalValidationProfile.Phase.STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.STATIC_ANALYSIS, true,
                java.util.List.of("python3", "-m", "http.server"), Path.of("."),
                java.time.Duration.ofSeconds(30), java.util.List.of());

        assertTrue(SandboxedValidationStepExecutor.supportsCommand(compile));
        assertFalse(SandboxedValidationStepExecutor.supportsCommand(arbitrary));
    }
}
