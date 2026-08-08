package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extends DependencyManifestVerifier's direct-dependency-only check to the full resolved
 * dependency graph (transitive included), by shelling out to Maven's own dependency resolution
 * (`mvn -o dependency:list`) instead of re-implementing POM inheritance, mediation and exclusion
 * rules. Every resolved groupId:artifactId:version must be present in the approved dependency
 * manifest; fails closed otherwise.
 */
public final class TransitiveDependencyVerifier {

    private static final Pattern RESOLVED_LINE = Pattern.compile(
            "^\\s*([^:\\s]+):([^:\\s]+):[^:\\s]+:([^:\\s]+):([^:\\s]+)\\s*$");

    public record ResolvedDependency(String groupId, String artifactId, String version, String scope) {
        public String coordinate() { return groupId + ":" + artifactId; }
    }

    public record Violation(String code, String coordinate, String detail) {}

    public record VerificationResult(boolean passed, List<ResolvedDependency> resolved, List<Violation> violations) {
        public VerificationResult {
            resolved = List.copyOf(resolved);
            violations = List.copyOf(violations);
        }
    }

    private TransitiveDependencyVerifier() {}

    /** Parses the plain-text output of {@code mvn dependency:list -DoutputFile=<path>}. */
    public static List<ResolvedDependency> parseDependencyListOutput(String output) {
        if (output == null) return List.of();
        List<ResolvedDependency> resolved = new ArrayList<>();
        for (String line : output.split("\\R")) {
            Matcher matcher = RESOLVED_LINE.matcher(line);
            if (matcher.matches()) {
                resolved.add(new ResolvedDependency(
                        matcher.group(1), matcher.group(2), matcher.group(3), matcher.group(4)));
            }
        }
        return resolved;
    }

    public static VerificationResult verify(List<ResolvedDependency> resolved, Path approvedManifestPath)
            throws IOException {
        Map<String, String> approvedVersionByCoordinate = readApprovedManifest(approvedManifestPath);
        List<Violation> violations = new ArrayList<>();
        for (ResolvedDependency dependency : resolved) {
            String approvedVersion = approvedVersionByCoordinate.get(dependency.coordinate());
            if (approvedVersion == null) {
                violations.add(new Violation("UNAPPROVED_TRANSITIVE_DEPENDENCY", dependency.coordinate(),
                        "resolved dependency not present in approved dependency manifest"));
            } else if (!approvedVersion.equals(dependency.version())) {
                violations.add(new Violation("TRANSITIVE_DEPENDENCY_VERSION_DRIFT", dependency.coordinate(),
                        "resolved " + dependency.version() + " but manifest approves " + approvedVersion));
            }
        }
        return new VerificationResult(violations.isEmpty(), resolved, violations);
    }

    /** Runs `mvn -o dependency:list` against {@code projectRoot} and returns the resolved dependencies. */
    public static List<ResolvedDependency> resolveViaMaven(Path projectRoot, Duration timeout) throws Exception {
        Path outputFile = Files.createTempFile("onsure-dependency-list-", ".txt");
        try {
            Map<String, String> environment = new LinkedHashMap<>();
            String path = System.getenv("PATH");
            if (path != null) environment.put("PATH", path);
            String home = System.getenv("HOME");
            if (home != null) environment.put("HOME", home);
            List<String> command = List.of(
                    "mvn", "-q", "-o", "dependency:list", "-Dsort=true", "-DoutputFile=" + outputFile);
            BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                    command, projectRoot, timeout, environment, "MAVEN_DEPENDENCY_LIST");
            if (result.exitCode() != 0) {
                throw new IllegalStateException("MAVEN_DEPENDENCY_LIST_FAILED:" + result.output().strip());
            }
            return parseDependencyListOutput(Files.readString(outputFile));
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    private static Map<String, String> readApprovedManifest(Path approvedManifestPath) throws IOException {
        JsonNode root = new ObjectMapper().readTree(approvedManifestPath.toFile());
        if (!"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1".equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("APPROVED_DEPENDENCY_MANIFEST_CONTRACT_INVALID");
        }
        Map<String, String> byCoordinate = new LinkedHashMap<>();
        for (JsonNode entry : root.path("approved_dependencies")) {
            String coordinate = entry.path("groupId").asText() + ":" + entry.path("artifactId").asText();
            byCoordinate.put(coordinate, entry.path("version").asText());
        }
        return byCoordinate;
    }
}
