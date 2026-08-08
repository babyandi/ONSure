package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import kr.co.oruda.onsure.platform.DependencyManifestVerifier.VerificationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DependencyManifestVerifierTest {
    @TempDir Path temp;

    @Test
    void currentRepositoryPomSatisfiesItsOwnApprovedManifest() throws Exception {
        VerificationResult result = DependencyManifestVerifier.verify(
                Path.of("pom.xml"), Path.of("contracts/approved-dependency-manifest.v1.json"));
        assertTrue(result.passed(), "violations: " + result.violations());
        assertTrue(result.declared().size() >= 3);
    }

    @Test
    void resolvesPropertyPlaceholderVersions() throws Exception {
        Path pom = writePom("""
                <project>
                  <properties><demo.version>9.9.9</demo.version></properties>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>demo</artifactId><version>${demo.version}</version></dependency>
                  </dependencies>
                </project>
                """);
        var declared = DependencyManifestVerifier.parsePom(pom);
        assertEquals(1, declared.size());
        assertEquals("9.9.9", declared.get(0).version());
        assertEquals("org.example:demo", declared.get(0).coordinate());
    }

    @Test
    void injectedUnapprovedDependencyFailsClosed() throws Exception {
        Path pom = writePom("""
                <project>
                  <dependencies>
                    <dependency><groupId>com.evil</groupId><artifactId>supply-chain-injection</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = writeManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[]}
                """);

        VerificationResult result = DependencyManifestVerifier.verify(pom, manifest);
        assertFalse(result.passed());
        assertEquals(1, result.violations().size());
        assertEquals("UNAPPROVED_DEPENDENCY", result.violations().get(0).code());
        assertEquals("com.evil:supply-chain-injection", result.violations().get(0).coordinate());
    }

    @Test
    void injectedVersionDriftFailsClosed() throws Exception {
        Path pom = writePom("""
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>demo</artifactId><version>2.0.0</version></dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = writeManifest("""
                {"contract":"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1","approved_dependencies":[
                  {"groupId":"org.example","artifactId":"demo","version":"1.0.0"}
                ]}
                """);

        VerificationResult result = DependencyManifestVerifier.verify(pom, manifest);
        assertFalse(result.passed());
        assertEquals("DEPENDENCY_VERSION_DRIFT", result.violations().get(0).code());
        assertTrue(result.violations().get(0).detail().contains("2.0.0"));
        assertTrue(result.violations().get(0).detail().contains("1.0.0"));
    }

    @Test
    void manifestWithWrongContractIdIsRejected() throws Exception {
        Path pom = writePom("<project><dependencies></dependencies></project>");
        Path manifest = writeManifest("{\"contract\":\"WRONG\",\"approved_dependencies\":[]}");
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> DependencyManifestVerifier.verify(pom, manifest));
    }

    private Path writePom(String xml) throws Exception {
        Path pom = temp.resolve("pom-" + System.nanoTime() + ".xml");
        Files.writeString(pom, xml);
        return pom;
    }

    private Path writeManifest(String json) throws Exception {
        Path manifest = temp.resolve("manifest-" + System.nanoTime() + ".json");
        Files.writeString(manifest, json);
        return manifest;
    }
}
