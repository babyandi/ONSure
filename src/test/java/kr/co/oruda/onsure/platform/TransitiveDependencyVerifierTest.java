package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import kr.co.oruda.onsure.platform.TransitiveDependencyVerifier.ResolvedDependency;
import kr.co.oruda.onsure.platform.TransitiveDependencyVerifier.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TransitiveDependencyVerifierTest {
    @TempDir Path temp;

    @Test
    void parsesResolvedDependencyListOutput() {
        List<ResolvedDependency> resolved = TransitiveDependencyVerifier.parseDependencyListOutput("""

                The following files have been resolved:
                   com.fasterxml.jackson.core:jackson-annotations:jar:2.18.2:compile
                   org.junit.jupiter:junit-jupiter:jar:5.11.4:test
                """);
        assertEquals(2, resolved.size());
        assertEquals("com.fasterxml.jackson.core:jackson-annotations", resolved.get(0).coordinate());
        assertEquals("2.18.2", resolved.get(0).version());
        assertEquals("compile", resolved.get(0).scope());
    }

    @Test
    void unapprovedTransitiveDependencyFailsClosed() throws Exception {
        List<ResolvedDependency> resolved = List.of(
                new ResolvedDependency("org.example", "sneaky-transitive", "1.0.0", "compile"));
        Path manifest = writeManifest("{\"contract\":\"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1\",\"approved_dependencies\":[]}");

        VerificationResult result = TransitiveDependencyVerifier.verify(resolved, manifest);
        assertFalse(result.passed());
        assertEquals("UNAPPROVED_TRANSITIVE_DEPENDENCY", result.violations().get(0).code());
        assertEquals("org.example:sneaky-transitive", result.violations().get(0).coordinate());
    }

    @Test
    void transitiveVersionDriftFailsClosed() throws Exception {
        List<ResolvedDependency> resolved = List.of(
                new ResolvedDependency("org.example", "lib", "2.0.0", "compile"));
        Path manifest = writeManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"lib","version":"1.0.0"}
                ]}
                """);

        VerificationResult result = TransitiveDependencyVerifier.verify(resolved, manifest);
        assertFalse(result.passed());
        assertEquals("TRANSITIVE_DEPENDENCY_VERSION_DRIFT", result.violations().get(0).code());
    }

    @Test
    void approvedTransitiveDependencyPasses() throws Exception {
        List<ResolvedDependency> resolved = List.of(
                new ResolvedDependency("org.example", "lib", "1.0.0", "compile"));
        Path manifest = writeManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"lib","version":"1.0.0"}
                ]}
                """);

        VerificationResult result = TransitiveDependencyVerifier.verify(resolved, manifest);
        assertTrue(result.passed());
    }

    @Test
    void currentRepositoryFullResolvedGraphSatisfiesItsOwnApprovedManifest() throws Exception {
        List<ResolvedDependency> resolved = TransitiveDependencyVerifier.resolveViaMaven(
                Path.of(".").toAbsolutePath().normalize(), Duration.ofSeconds(60));
        assertTrue(resolved.size() >= 10, "expected transitive deps to be resolved, got: " + resolved);

        VerificationResult result = TransitiveDependencyVerifier.verify(
                resolved, Path.of("contracts/approved-dependency-manifest.v1.json"));
        assertTrue(result.passed(), "violations: " + result.violations());
    }

    private Path writeManifest(String json) throws Exception {
        Path manifest = temp.resolve("manifest-" + System.nanoTime() + ".json");
        Files.writeString(manifest, json);
        return manifest;
    }
}
