package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import kr.co.oruda.onsure.platform.DependencyManifestVerifier.DeclaredDependency;

/**
 * License verification for direct build dependencies: every dependency declared in pom.xml must
 * be present in the approved dependency manifest with a license that the dependency license
 * policy (contracts/dependency-license-policy.v1.json) marks ALLOWED. Fails closed on FORBIDDEN
 * and on any license id the policy does not list.
 */
public final class LicensePolicyVerifier {

    public record LicenseViolation(String code, String coordinate, String detail) {}

    public record VerificationResult(boolean passed, List<LicenseViolation> violations) {
        public VerificationResult {
            violations = List.copyOf(violations);
        }
    }

    private LicensePolicyVerifier() {}

    public static VerificationResult verify(Path pomXmlPath, Path approvedManifestPath, Path licensePolicyPath)
            throws Exception {
        List<DeclaredDependency> declared = DependencyManifestVerifier.parsePom(pomXmlPath);
        Map<String, String> licenseByCoordinate = readApprovedManifestLicenses(approvedManifestPath);
        Map<String, String> decisionBySpdxId = readLicensePolicy(licensePolicyPath);
        String defaultDecision = readDefaultDecision(licensePolicyPath);

        List<LicenseViolation> violations = new ArrayList<>();
        for (DeclaredDependency dependency : declared) {
            String license = licenseByCoordinate.get(dependency.coordinate());
            if (license == null) {
                continue; // not in approved manifest at all: DependencyManifestVerifier's concern, not license policy's.
            }
            String decision = decisionBySpdxId.getOrDefault(license, defaultDecision);
            if ("FORBIDDEN".equals(decision)) {
                violations.add(new LicenseViolation("LICENSE_FORBIDDEN", dependency.coordinate(),
                        license + " is forbidden by dependency license policy"));
            } else if (!"ALLOWED".equals(decision)) {
                violations.add(new LicenseViolation("LICENSE_POLICY_DECISION_INVALID", dependency.coordinate(),
                        "unrecognized policy decision '" + decision + "' for license " + license));
            }
        }
        return new VerificationResult(violations.isEmpty(), violations);
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

    private static Map<String, String> readLicensePolicy(Path licensePolicyPath) throws IOException {
        JsonNode root = new ObjectMapper().readTree(licensePolicyPath.toFile());
        if (!"ONSURE_DEPENDENCY_LICENSE_POLICY_V1".equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("DEPENDENCY_LICENSE_POLICY_CONTRACT_INVALID");
        }
        Map<String, String> decisionBySpdxId = new LinkedHashMap<>();
        for (JsonNode entry : root.path("decisions")) {
            decisionBySpdxId.put(entry.path("spdx_id").asText(), entry.path("decision").asText());
        }
        return decisionBySpdxId;
    }

    private static String readDefaultDecision(Path licensePolicyPath) throws IOException {
        JsonNode root = new ObjectMapper().readTree(licensePolicyPath.toFile());
        return root.path("default_decision_for_unlisted_license").asText("FORBIDDEN");
    }
}
