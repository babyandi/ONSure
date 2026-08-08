package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Produces the "build" assurance lane's required_outputs (contracts/assurance-lanes.v1.json:
 * build_receipt, artifact_digest, sbom, provenance, reproducibility_result) as one JSON document,
 * by running every dependency verifier already built (DependencyManifestVerifier,
 * TransitiveDependencyVerifier, LicensePolicyVerifier, VulnerabilityDenylistVerifier,
 * SbomGenerator) against the real resolved dependency graph and embedding their results --
 * instead of leaving SbomGenerator's output disconnected from any actual build receipt.
 *
 * <p>provenance and reproducibility_result are honestly recorded as NOT_RUN: this service does not
 * attempt build provenance attestation or a second reproducible build/diff, so it never claims
 * those checks passed.
 */
public final class BuildLaneReceiptService {
    public static final String CONTRACT = "ONSURE_BUILD_LANE_RECEIPT_V1";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private BuildLaneReceiptService() {}

    public static Map<String, Object> generate(
            Path projectRoot,
            Path pomXmlPath,
            Path approvedManifestPath,
            Path licensePolicyPath,
            Path denylistPath,
            Path artifactJarPath,
            Path outputFile) throws Exception {
        List<TransitiveDependencyVerifier.ResolvedDependency> resolved =
                TransitiveDependencyVerifier.resolveViaMaven(projectRoot, Duration.ofSeconds(120));

        DependencyManifestVerifier.VerificationResult directResult =
                DependencyManifestVerifier.verify(pomXmlPath, approvedManifestPath);
        TransitiveDependencyVerifier.VerificationResult transitiveResult =
                TransitiveDependencyVerifier.verify(resolved, approvedManifestPath);
        LicensePolicyVerifier.VerificationResult licenseResult =
                LicensePolicyVerifier.verifyResolved(resolved, approvedManifestPath, licensePolicyPath);
        VulnerabilityDenylistVerifier.VerificationResult vulnerabilityResult =
                VulnerabilityDenylistVerifier.verifyResolved(resolved, denylistPath);
        var sbom = SbomGenerator.generate(pomXmlPath, approvedManifestPath);

        boolean passed = directResult.passed() && transitiveResult.passed()
                && licenseResult.passed() && vulnerabilityResult.passed();

        String artifactDigest = "NOT_PROVIDED";
        if (artifactJarPath != null) {
            if (!Files.isRegularFile(artifactJarPath, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(artifactJarPath)) {
                throw new IllegalArgumentException("BUILD_ARTIFACT_PATH_INVALID");
            }
            artifactDigest = Hashing.file(artifactJarPath);
        }

        Map<String, Object> receipt = new LinkedHashMap<>();
        receipt.put("contract", CONTRACT);
        receipt.put("decision", passed ? "PASS" : "FAIL");
        receipt.put("direct_dependency_violations", directResult.violations());
        receipt.put("transitive_dependency_violations", transitiveResult.violations());
        receipt.put("license_violations", licenseResult.violations());
        receipt.put("vulnerability_denylist_matches", vulnerabilityResult.matches());
        receipt.put("resolved_dependency_count", resolved.size());
        receipt.put("sbom", sbom);
        receipt.put("artifact_digest", artifactDigest);
        receipt.put("provenance", "NOT_RUN");
        receipt.put("reproducibility_result", "NOT_RUN");
        receipt.put("generated_at", Instant.now().toString());
        receipt.put("final_claim_allowed", false);

        writeAtomic(outputFile, receipt);
        return Map.copyOf(receipt);
    }

    private static void writeAtomic(Path outputFile, Object value) throws Exception {
        Path output = outputFile.toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(output)) {
            throw new IllegalArgumentException("BUILD_LANE_RECEIPT_OUTPUT_SYMLINK_PROHIBITED");
        }
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        MAPPER.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
