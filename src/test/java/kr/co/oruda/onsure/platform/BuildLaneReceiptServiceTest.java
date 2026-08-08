package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildLaneReceiptServiceTest {
    @TempDir Path temp;

    @Test
    void generatesAPassingReceiptWithAnEmbeddedSbomForTheCurrentRepository() throws Exception {
        Path outputFile = temp.resolve("build-lane-receipt.json");
        Map<String, Object> receipt = BuildLaneReceiptService.generate(
                Path.of(".").toAbsolutePath().normalize(),
                Path.of("pom.xml"),
                Path.of("contracts/approved-dependency-manifest.v1.json"),
                Path.of("contracts/dependency-license-policy.v1.json"),
                Path.of("contracts/dependency-vulnerability-denylist.v1.json"),
                null,
                outputFile);

        assertEquals("PASS", receipt.get("decision"));
        assertEquals("NOT_PROVIDED", receipt.get("artifact_digest"));
        assertEquals("NOT_RUN", receipt.get("provenance"));
        assertEquals("NOT_RUN", receipt.get("reproducibility_result"));
        assertTrue(((Number) receipt.get("resolved_dependency_count")).intValue() >= 10);
        assertTrue(receipt.containsKey("sbom"));

        assertTrue(Files.isRegularFile(outputFile));
        JsonNode written = new ObjectMapper().readTree(outputFile.toFile());
        assertEquals(BuildLaneReceiptService.CONTRACT, written.path("contract").asText());
        assertEquals("CycloneDX", written.path("sbom").path("bomFormat").asText());
        assertTrue(written.path("sbom").path("components").size() >= 3);
    }

    @Test
    void includesARealArtifactDigestWhenAJarPathIsProvided() throws Exception {
        Path fakeJar = temp.resolve("onsure-fake.jar");
        Files.writeString(fakeJar, "fake-jar-bytes-for-digest-test");

        Map<String, Object> receipt = BuildLaneReceiptService.generate(
                Path.of(".").toAbsolutePath().normalize(),
                Path.of("pom.xml"),
                Path.of("contracts/approved-dependency-manifest.v1.json"),
                Path.of("contracts/dependency-license-policy.v1.json"),
                Path.of("contracts/dependency-vulnerability-denylist.v1.json"),
                fakeJar,
                temp.resolve("with-jar-receipt.json"));

        assertEquals(Hashing.file(fakeJar), receipt.get("artifact_digest"));
    }

    @Test
    void decisionFailsClosedWhenADependencyIsUnapproved() throws Exception {
        Path pom = temp.resolve("pom.xml");
        Files.writeString(pom, """
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>unreviewed-lib</artifactId><version>9.9.9</version></dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = temp.resolve("manifest.json");
        Files.writeString(manifest, "{\"contract\":\"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1\",\"approved_dependencies\":[]}");
        Path policy = temp.resolve("policy.json");
        Files.writeString(policy, "{\"contract\":\"ONSURE_DEPENDENCY_LICENSE_POLICY_V1\",\"decisions\":[],"
                + "\"default_decision_for_unlisted_license\":\"FORBIDDEN\"}");
        Path denylist = temp.resolve("denylist.json");
        Files.writeString(denylist, "{\"contract\":\"ONSURE_DEPENDENCY_VULNERABILITY_DENYLIST_V1\",\"denylisted_dependencies\":[]}");

        Map<String, Object> receipt = BuildLaneReceiptService.generate(
                Path.of(".").toAbsolutePath().normalize(), pom, manifest, policy, denylist, null,
                temp.resolve("standalone-pom-receipt.json"));

        assertEquals("FAIL", receipt.get("decision"));
        @SuppressWarnings("unchecked")
        List<DependencyManifestVerifier.Violation> violations =
                (List<DependencyManifestVerifier.Violation>) receipt.get("direct_dependency_violations");
        assertTrue(violations.stream().anyMatch(v -> "UNAPPROVED_DEPENDENCY".equals(v.code())));
    }

    @Test
    void refusesASymlinkedOutputPath() throws Exception {
        Path realFile = temp.resolve("real-receipt.json");
        Files.writeString(realFile, "{}");
        Path link = temp.resolve("receipt-link.json");
        try {
            Files.createSymbolicLink(link, realFile);
        } catch (UnsupportedOperationException unsupported) {
            return;
        }

        assertThrows(IllegalArgumentException.class, () -> BuildLaneReceiptService.generate(
                Path.of(".").toAbsolutePath().normalize(),
                Path.of("pom.xml"),
                Path.of("contracts/approved-dependency-manifest.v1.json"),
                Path.of("contracts/dependency-license-policy.v1.json"),
                Path.of("contracts/dependency-vulnerability-denylist.v1.json"),
                null,
                link));
    }
}
