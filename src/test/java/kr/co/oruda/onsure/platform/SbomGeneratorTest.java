package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SbomGeneratorTest {
    @TempDir Path temp;

    @Test
    void generatesCycloneDxComponentPerDeclaredDependencyWithLicense() throws Exception {
        ObjectNode sbom = SbomGenerator.generate(
                Path.of("pom.xml"), Path.of("contracts/approved-dependency-manifest.v1.json"));

        assertEquals("CycloneDX", sbom.get("bomFormat").asText());
        assertEquals("1.5", sbom.get("specVersion").asText());
        assertTrue(sbom.get("components").size() >= 3);

        boolean foundJacksonWithLicense = false;
        for (JsonNode component : sbom.get("components")) {
            if ("jackson-databind".equals(component.get("name").asText())) {
                foundJacksonWithLicense = "Apache-2.0"
                        .equals(component.get("licenses").get(0).get("license").get("id").asText());
                assertTrue(component.get("purl").asText().startsWith("pkg:maven/com.fasterxml.jackson.core/jackson-databind@"));
            }
        }
        assertTrue(foundJacksonWithLicense);
    }

    @Test
    void writesValidJsonToDisk() throws Exception {
        ObjectNode sbom = SbomGenerator.generate(
                Path.of("pom.xml"), Path.of("contracts/approved-dependency-manifest.v1.json"));
        Path output = temp.resolve("sbom.json");

        SbomGenerator.writeTo(output, sbom);

        assertTrue(Files.exists(output));
        JsonNode reloaded = new ObjectMapper().readTree(output.toFile());
        assertEquals("CycloneDX", reloaded.get("bomFormat").asText());
    }

    @Test
    void refusesToWriteThroughASymlink() throws Exception {
        Path real = temp.resolve("real-sbom.json");
        Files.writeString(real, "{}");
        Path link = temp.resolve("sbom-link.json");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException e) {
            return; // filesystem doesn't support symlinks in this sandbox; nothing to assert.
        }

        ObjectNode sbom = SbomGenerator.generate(
                Path.of("pom.xml"), Path.of("contracts/approved-dependency-manifest.v1.json"));
        assertThrows(IllegalArgumentException.class, () -> SbomGenerator.writeTo(link, sbom));
    }

    @Test
    void dependencyMissingFromApprovedManifestHasNoLicensesField() throws Exception {
        Path pom = temp.resolve("pom.xml");
        Files.writeString(pom, """
                <project>
                  <dependencies>
                    <dependency><groupId>org.example</groupId><artifactId>unreviewed-lib</artifactId><version>1.0.0</version></dependency>
                  </dependencies>
                </project>
                """);
        Path manifest = temp.resolve("manifest.json");
        Files.writeString(manifest, "{\"contract\":\"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1\",\"approved_dependencies\":[]}");

        ObjectNode sbom = SbomGenerator.generate(pom, manifest);
        JsonNode component = sbom.get("components").get(0);
        assertFalse(component.has("licenses"));
    }
}
