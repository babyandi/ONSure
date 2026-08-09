package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import kr.co.oruda.onsure.platform.AirGappedDependencyPackager.PackResult;
import kr.co.oruda.onsure.platform.AirGappedDependencyPackager.VerifyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class AirGappedDependencyPackagerTest {
    @TempDir Path temp;

    @Test
    void packsApprovedJarsIntoAMinimalLocalMavenRepositoryLayoutWithAMatchingManifest() throws Exception {
        Path repo = temp.resolve("m2-repo");
        writeFakeJar(repo, "org.example", "alpha", "1.0.0", "alpha-jar-bytes");
        writeFakeJar(repo, "org.example.sub", "beta", "2.3.4", "beta-jar-bytes");
        Path manifest = writeApprovedManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"alpha","version":"1.0.0","scope":"compile"},
                  {"groupId":"org.example.sub","artifactId":"beta","version":"2.3.4","scope":"test"}
                ]}
                """);
        Path outputDir = temp.resolve("pack-out");

        PackResult result = AirGappedDependencyPackager.pack(manifest, repo, outputDir);
        assertEquals(2, result.packedCount());

        Path alphaJar = outputDir.resolve("org/example/alpha/1.0.0/alpha-1.0.0.jar");
        Path betaJar = outputDir.resolve("org/example/sub/beta/2.3.4/beta-2.3.4.jar");
        assertTrue(Files.isRegularFile(alphaJar));
        assertTrue(Files.isRegularFile(betaJar));
        assertEquals("alpha-jar-bytes", Files.readString(alphaJar));
        assertEquals("beta-jar-bytes", Files.readString(betaJar));

        var manifestNode = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(result.manifestFile().toFile());
        assertEquals("ONSURE_AIR_GAPPED_DEPENDENCY_PACK_V1", manifestNode.path("contract").asText());
        assertEquals(2, manifestNode.path("packed_count").asInt());
        assertEquals(Hashing.file(alphaJar), manifestNode.path("entries").get(0).path("sha256").asText());
        assertEquals(Hashing.file(betaJar), manifestNode.path("entries").get(1).path("sha256").asText());

        VerifyResult verified = AirGappedDependencyPackager.verify(outputDir);
        assertTrue(verified.valid(), "violations: " + verified.violations());
        assertTrue(verified.violations().isEmpty());
    }

    @Test
    void packFailsClosedAndIdentifiesTheMissingDependencyWhenAJarIsAbsentFromTheLocalRepository() throws Exception {
        Path repo = temp.resolve("m2-repo-partial");
        writeFakeJar(repo, "org.example", "present", "1.0.0", "present-jar-bytes");
        // org.example:absent:9.9.9 is approved but intentionally never written into repo.
        Path manifest = writeApprovedManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"present","version":"1.0.0","scope":"compile"},
                  {"groupId":"org.example","artifactId":"absent","version":"9.9.9","scope":"compile"}
                ]}
                """);

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> AirGappedDependencyPackager.pack(manifest, repo, temp.resolve("pack-out-partial")));
        assertTrue(failure.getMessage().contains("AIR_GAPPED_PACK_JAR_MISSING_FROM_LOCAL_REPOSITORY"));
        assertTrue(failure.getMessage().contains("org.example:absent:9.9.9"));
        assertFalse(failure.getMessage().contains("org.example:present:1.0.0"));
    }

    @Test
    void verifyFailsClosedWhenAPackedJarIsTamperedWithAfterPacking() throws Exception {
        Path repo = temp.resolve("m2-repo-tamper");
        writeFakeJar(repo, "org.example", "alpha", "1.0.0", "original-bytes");
        Path manifest = writeApprovedManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"alpha","version":"1.0.0","scope":"compile"}
                ]}
                """);
        Path outputDir = temp.resolve("pack-out-tamper");
        AirGappedDependencyPackager.pack(manifest, repo, outputDir);

        Files.writeString(outputDir.resolve("org/example/alpha/1.0.0/alpha-1.0.0.jar"), "tampered-bytes");

        VerifyResult result = AirGappedDependencyPackager.verify(outputDir);
        assertFalse(result.valid());
        assertTrue(result.violations().stream()
                .anyMatch(v -> v.equals("AIR_GAPPED_PACK_JAR_INTEGRITY_MISMATCH:org.example:alpha:1.0.0")));
    }

    @Test
    void verifyFailsClosedWhenAPackedJarIsRemovedAfterPacking() throws Exception {
        Path repo = temp.resolve("m2-repo-remove");
        writeFakeJar(repo, "org.example", "alpha", "1.0.0", "original-bytes");
        Path manifest = writeApprovedManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"alpha","version":"1.0.0","scope":"compile"}
                ]}
                """);
        Path outputDir = temp.resolve("pack-out-remove");
        AirGappedDependencyPackager.pack(manifest, repo, outputDir);

        Files.delete(outputDir.resolve("org/example/alpha/1.0.0/alpha-1.0.0.jar"));

        VerifyResult result = AirGappedDependencyPackager.verify(outputDir);
        assertFalse(result.valid());
        assertTrue(result.violations().contains("AIR_GAPPED_PACK_JAR_MISSING:org.example:alpha:1.0.0"));
    }

    @Test
    void verifyFailsClosedWhenTheManifestIsMissing() throws Exception {
        Path outputDir = temp.resolve("pack-out-no-manifest");
        Files.createDirectories(outputDir);
        VerifyResult result = AirGappedDependencyPackager.verify(outputDir);
        assertFalse(result.valid());
        assertTrue(result.violations().contains("AIR_GAPPED_PACK_MANIFEST_MISSING"));
    }

    @Test
    void currentRepositoryRealApprovedManifestPacksAndVerifiesAgainstTheRealLocalMavenRepository() throws Exception {
        Path realMavenRepository = Path.of(System.getProperty("user.home"), ".m2", "repository");
        assertTrue(Files.isDirectory(realMavenRepository),
                "expected a populated local Maven repository at " + realMavenRepository);

        Path outputDir = temp.resolve("real-air-gapped-pack");
        PackResult result = AirGappedDependencyPackager.pack(
                Path.of("contracts/approved-dependency-manifest.v1.json"), realMavenRepository, outputDir);
        assertTrue(result.packedCount() >= 3);

        VerifyResult verified = AirGappedDependencyPackager.verify(outputDir);
        assertTrue(verified.valid(), "violations: " + verified.violations());
    }

    private void writeFakeJar(Path repo, String groupId, String artifactId, String version, String contents)
            throws Exception {
        Path jar = repo.resolve(groupId.replace('.', '/') + "/" + artifactId + "/" + version
                + "/" + artifactId + "-" + version + ".jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, contents);
    }

    private Path writeApprovedManifest(String json) throws Exception {
        Path manifest = temp.resolve("approved-manifest-" + System.nanoTime() + ".json");
        Files.writeString(manifest, json);
        return manifest;
    }
}
