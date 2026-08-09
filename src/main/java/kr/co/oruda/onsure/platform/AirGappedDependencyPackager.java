package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Builds a self-contained, offline-installable local Maven repository (NFR-01: local/offline
 * execution, Enterprise Offline Mode) by copying the real jar files for every dependency listed in
 * contracts/approved-dependency-manifest.v1.json out of a local Maven repository
 * (typically ~/.m2/repository) into an output directory that preserves the same Maven repository
 * directory layout -- so the output directory is itself a valid, minimal local Maven repository a
 * build can point {@code -Dmaven.repo.local=} at with no network access. This packages the
 * dependency jars specifically; it does not build or sign the application itself (see
 * {@link DeploymentPackageBuilder} for that), and it does not bundle the vscode-extension's
 * npm/package-lock.json dependencies -- different toolchain/ecosystem, left as a remaining gap for
 * future work.
 */
public final class AirGappedDependencyPackager {
    public static final String MANIFEST_CONTRACT = "ONSURE_AIR_GAPPED_DEPENDENCY_PACK_V1";
    private static final String MANIFEST_FILE_NAME = "air-gapped-dependency-pack-manifest.json";

    public record ApprovedDependency(String groupId, String artifactId, String version, String scope) {
        public ApprovedDependency {
            if (groupId == null || groupId.isBlank()) throw new IllegalArgumentException("groupId");
            if (artifactId == null || artifactId.isBlank()) throw new IllegalArgumentException("artifactId");
            if (version == null || version.isBlank()) throw new IllegalArgumentException("version");
            scope = scope == null || scope.isBlank() ? "compile" : scope;
        }

        /** Standard Maven local-repository layout: {@code <groupId-with-slashes>/<artifactId>/<version>/<artifactId>-<version>.jar}. */
        String relativeJarPath() {
            return groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version + ".jar";
        }

        String coordinate() { return groupId + ":" + artifactId + ":" + version; }
    }

    public record PackResult(int packedCount, Path manifestFile) {}

    public record VerifyResult(boolean valid, List<String> violations) {
        public VerifyResult { violations = List.copyOf(violations); }
    }

    private static final ObjectMapper WRITER = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);
    private static final ObjectMapper READER = new ObjectMapper();

    private AirGappedDependencyPackager() {}

    public static PackResult pack(Path approvedManifestPath, Path localMavenRepository, Path outputDir) throws Exception {
        Objects.requireNonNull(approvedManifestPath, "approvedManifestPath");
        Objects.requireNonNull(localMavenRepository, "localMavenRepository");
        Objects.requireNonNull(outputDir, "outputDir");

        Path repository = localMavenRepository.toAbsolutePath().normalize();
        if (!Files.isDirectory(repository, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("AIR_GAPPED_PACK_LOCAL_MAVEN_REPOSITORY_MISSING:" + repository);
        }
        Path destination = outputDir.toAbsolutePath().normalize();
        requireNoSymlink(destination, "AIR_GAPPED_PACK_OUTPUT_SYMLINK_PROHIBITED");
        if (hasEntries(destination)) throw new IllegalStateException("AIR_GAPPED_PACK_OUTPUT_NOT_EMPTY");

        List<ApprovedDependency> approved = readApprovedManifest(approvedManifestPath);
        if (approved.isEmpty()) throw new IllegalStateException("AIR_GAPPED_PACK_APPROVED_MANIFEST_EMPTY");

        List<String> missing = new ArrayList<>();
        for (ApprovedDependency dependency : approved) {
            Path jar = repository.resolve(dependency.relativeJarPath());
            if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(jar)) {
                missing.add(dependency.coordinate());
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException("AIR_GAPPED_PACK_JAR_MISSING_FROM_LOCAL_REPOSITORY:" + missing);
        }

        Files.createDirectories(destination);
        List<Map<String, Object>> entries = new ArrayList<>();
        for (ApprovedDependency dependency : approved) {
            String relativePath = dependency.relativeJarPath();
            Path source = repository.resolve(relativePath);
            String digest = Hashing.file(source);
            Path copyTarget = destination.resolve(relativePath);
            Files.createDirectories(copyTarget.getParent());
            Files.copy(source, copyTarget, StandardCopyOption.COPY_ATTRIBUTES);

            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("coordinate", dependency.coordinate());
            entry.put("groupId", dependency.groupId());
            entry.put("artifactId", dependency.artifactId());
            entry.put("version", dependency.version());
            entry.put("scope", dependency.scope());
            entry.put("relative_path", relativePath);
            entry.put("sha256", digest);
            entries.add(entry);
        }

        Map<String, Object> manifest = new LinkedHashMap<>();
        manifest.put("contract", MANIFEST_CONTRACT);
        manifest.put("packed_count", entries.size());
        manifest.put("entries", entries);
        manifest.put("packed_at", Instant.now().toString());

        Path manifestFile = destination.resolve(MANIFEST_FILE_NAME);
        WRITER.writeValue(manifestFile.toFile(), manifest);
        return new PackResult(entries.size(), manifestFile);
    }

    public static VerifyResult verify(Path packDir) throws Exception {
        Path source = packDir.toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        Path manifestFile = source.resolve(MANIFEST_FILE_NAME);
        if (!Files.isRegularFile(manifestFile, LinkOption.NOFOLLOW_LINKS)) {
            return new VerifyResult(false, List.of("AIR_GAPPED_PACK_MANIFEST_MISSING"));
        }
        JsonNode root = READER.readTree(manifestFile.toFile());
        if (!MANIFEST_CONTRACT.equals(root.path("contract").asText())) {
            return new VerifyResult(false, List.of("AIR_GAPPED_PACK_MANIFEST_CONTRACT_INVALID"));
        }
        for (JsonNode entry : root.path("entries")) {
            String coordinate = entry.path("coordinate").asText();
            String relativePath = entry.path("relative_path").asText();
            String expectedSha256 = entry.path("sha256").asText();
            Path jar = source.resolve(relativePath);
            if (!Files.isRegularFile(jar, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(jar)) {
                violations.add("AIR_GAPPED_PACK_JAR_MISSING:" + coordinate);
                continue;
            }
            if (!expectedSha256.equals(Hashing.file(jar))) {
                violations.add("AIR_GAPPED_PACK_JAR_INTEGRITY_MISMATCH:" + coordinate);
            }
        }
        return new VerifyResult(violations.isEmpty(), violations);
    }

    private static List<ApprovedDependency> readApprovedManifest(Path approvedManifestPath) throws Exception {
        JsonNode root = READER.readTree(approvedManifestPath.toFile());
        if (!"ONSURE_APPROVED_DEPENDENCY_MANIFEST_V1".equals(root.path("contract").asText())) {
            throw new IllegalArgumentException("APPROVED_DEPENDENCY_MANIFEST_CONTRACT_INVALID");
        }
        List<ApprovedDependency> result = new ArrayList<>();
        for (JsonNode entry : root.path("approved_dependencies")) {
            result.add(new ApprovedDependency(
                    entry.path("groupId").asText(),
                    entry.path("artifactId").asText(),
                    entry.path("version").asText(),
                    entry.path("scope").asText(null)));
        }
        return result;
    }

    private static boolean hasEntries(Path directory) throws Exception {
        if (!Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)) return false;
        try (var stream = Files.list(directory)) {
            return stream.findAny().isPresent();
        }
    }

    private static void requireNoSymlink(Path path, String code) {
        for (Path current = path; current != null; current = current.getParent()) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
        }
    }
}
