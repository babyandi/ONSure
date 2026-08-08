package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.co.oruda.onsure.platform.DependencyManifestVerifier.DeclaredDependency;

/**
 * Generates a CycloneDX 1.5 JSON software bill of materials from a pom.xml's direct dependencies,
 * enriched with the license recorded in the approved dependency manifest
 * (contracts/approved-dependency-manifest.v1.json). The build lane's required_outputs
 * (contracts/assurance-lanes.v1.json) include "sbom"; this is the direct-dependency slice of it --
 * it does not resolve or list transitive dependencies.
 */
public final class SbomGenerator {

    private SbomGenerator() {}

    public static ObjectNode generate(Path pomXmlPath, Path approvedManifestPath) throws Exception {
        List<DeclaredDependency> declared = DependencyManifestVerifier.parsePom(pomXmlPath);
        Map<String, String> licenseByCoordinate = readApprovedManifestLicenses(approvedManifestPath);

        ObjectMapper mapper = new ObjectMapper();
        ObjectNode bom = mapper.createObjectNode();
        bom.put("bomFormat", "CycloneDX");
        bom.put("specVersion", "1.5");
        bom.put("version", 1);

        ArrayNode components = bom.putArray("components");
        for (DeclaredDependency dependency : declared) {
            ObjectNode component = components.addObject();
            component.put("type", "library");
            component.put("group", dependency.groupId());
            component.put("name", dependency.artifactId());
            component.put("version", dependency.version());
            component.put("scope", "test".equals(dependency.scope()) ? "optional" : "required");
            component.put("purl", "pkg:maven/" + dependency.groupId() + "/" + dependency.artifactId()
                    + "@" + dependency.version());

            String license = licenseByCoordinate.get(dependency.coordinate());
            if (license != null) {
                ArrayNode licenses = component.putArray("licenses");
                licenses.addObject().putObject("license").put("id", license);
            }
        }
        return bom;
    }

    public static void writeTo(Path outputPath, ObjectNode sbom) throws IOException {
        if (Files.isSymbolicLink(outputPath)) {
            throw new IllegalArgumentException("SBOM_OUTPUT_PATH_MUST_NOT_BE_A_SYMLINK");
        }
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outputPath.toFile(), sbom);
    }

    private static Map<String, String> readApprovedManifestLicenses(Path approvedManifestPath) throws IOException {
        JsonNode root = new ObjectMapper().readTree(approvedManifestPath.toFile());
        if (!"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1".equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("APPROVED_DEPENDENCY_MANIFEST_CONTRACT_INVALID");
        }
        Map<String, String> byCoordinate = new LinkedHashMap<>();
        for (JsonNode entry : root.path("approved_dependencies")) {
            String coordinate = entry.path("groupId").asText() + ":" + entry.path("artifactId").asText();
            byCoordinate.put(coordinate, entry.path("license").asText());
        }
        return byCoordinate;
    }
}
