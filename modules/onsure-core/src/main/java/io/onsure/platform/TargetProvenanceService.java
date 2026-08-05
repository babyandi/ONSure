package io.onsure.platform;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Captures and verifies immutable target identity without writing to the validation target. */
final class TargetProvenanceService {
    static final String CONTRACT = "ONSURE_TARGET_PROVENANCE_V1";
    private static final Set<String> CLASSIFICATIONS = Set.of(
            "REAL_REPOSITORY", "FIXTURE", "SYNTHETIC_SNAPSHOT", "UNKNOWN");
    private static final Set<String> REQUESTED_CLASSIFICATIONS = Set.of(
            "AUTO", "REAL_REPOSITORY", "FIXTURE", "SYNTHETIC_SNAPSHOT");
    private static final Set<String> ROOT_FIELDS = Set.of(
            "contract", "target_classification", "classification_basis", "repository_type",
            "repository_identity_basis", "repository_identity_sha256", "repository_commit_sha",
            "repository_scope", "worktree_clean", "registration_source_sha256",
            "snapshot_source_sha256", "snapshot_manifest_sha256", "snapshot_file_count",
            "fixture_only", "real_target_universality_eligible", "universality_claim_state",
            "review_required", "runtime_validation", "final_claim_allowed", "provenance_sha256");
    private static final Set<String> FIXTURE_SEGMENTS = Set.of(
            "fixture", "fixtures", "test-fixtures", "testdata", "test-data");
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private final Path storeRoot;

    TargetProvenanceService(Path workspaceRoot) {
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        this.storeRoot = workspace.resolve(".onsure/target-provenance").normalize();
        if (!storeRoot.startsWith(workspace)) throw new IllegalArgumentException("TARGET_PROVENANCE_STORE_INVALID");
    }

    Map<String, Object> capture(
            Path sourceRoot, String registrationSourceSha256, String requestedClassification) throws Exception {
        Path root = requireSourceRoot(sourceRoot);
        requireDigest(registrationSourceSha256, "TARGET_PROVENANCE_REGISTRATION_SOURCE_DIGEST_INVALID");
        String requested = requestedClassification == null || requestedClassification.isBlank()
                ? "AUTO" : requestedClassification;
        if (!REQUESTED_CLASSIFICATIONS.contains(requested)) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_CLASSIFICATION_INVALID");
        }

        GitIdentity git = gitIdentity(root);
        boolean fixtureLocation = git != null && fixtureScope(git.scope());
        String classification = classification(requested, git, fixtureLocation);
        List<Path> snapshotFiles = Hashing.sourceFiles(root);
        String snapshotSourceSha256 = Hashing.tree(root, snapshotFiles);
        String manifestSha256 = manifestDigest(root, snapshotFiles);
        boolean eligible = "REAL_REPOSITORY".equals(classification)
                && git != null && git.clean() && !fixtureLocation && !snapshotFiles.isEmpty();

        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("target_classification", classification);
        value.put("classification_basis", classificationBasis(requested, git, fixtureLocation));
        value.put("repository_type", git == null ? "NONE" : "GIT");
        value.put("repository_identity_basis", git == null ? "NO_GIT_REPOSITORY"
                : git.remoteIdentity() ? "GIT_REMOTE_ORIGIN_HASH" : "LOCAL_GIT_ROOT_HASH");
        value.put("repository_identity_sha256", git == null
                ? Hashing.sha256("NO_GIT_REPOSITORY\u0000" + root)
                : git.repositoryIdentitySha256());
        value.put("repository_commit_sha", git == null ? null : git.commit());
        value.put("repository_scope", git == null ? "." : git.scope());
        value.put("worktree_clean", git == null ? null : git.clean());
        value.put("registration_source_sha256", registrationSourceSha256);
        value.put("snapshot_source_sha256", snapshotSourceSha256);
        value.put("snapshot_manifest_sha256", manifestSha256);
        value.put("snapshot_file_count", snapshotFiles.size());
        value.put("fixture_only", "FIXTURE".equals(classification));
        value.put("real_target_universality_eligible", eligible);
        value.put("universality_claim_state", claimState(classification, eligible));
        value.put("review_required", true);
        value.put("runtime_validation", "NOT_RUN");
        value.put("final_claim_allowed", false);
        value.put("provenance_sha256", canonicalDigest(value));
        Map<String, Object> result = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));
        validate(result);
        return result;
    }

    void persist(String targetId, Map<String, Object> provenance) throws Exception {
        requireTargetId(targetId);
        validate(provenance);
        Files.createDirectories(storeRoot);
        Path destination = file(targetId);
        Path temporary = destination.resolveSibling(destination.getFileName() + "." + UUID.randomUUID() + ".tmp");
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), provenance);
        try {
            Files.move(temporary, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, destination, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    Map<String, Object> load(String targetId) throws Exception {
        requireTargetId(targetId);
        Path file = file(targetId);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_NOT_FOUND");
        }
        Map<String, Object> value = MAPPER.readValue(file.toFile(), new TypeReference<>() {});
        validate(value);
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    Map<String, Object> requireCurrent(
            String targetId, Path sourceRoot, String registrationSourceSha256) throws Exception {
        Map<String, Object> stored = load(targetId);
        String requested = switch (stored.get("target_classification").toString()) {
            case "REAL_REPOSITORY", "FIXTURE", "SYNTHETIC_SNAPSHOT" ->
                    stored.get("target_classification").toString();
            default -> "AUTO";
        };
        Map<String, Object> current = capture(sourceRoot, registrationSourceSha256, requested);
        for (String field : List.of(
                "target_classification", "repository_type", "repository_identity_sha256",
                "repository_commit_sha", "repository_scope", "worktree_clean",
                "registration_source_sha256", "snapshot_source_sha256",
                "snapshot_manifest_sha256", "snapshot_file_count", "fixture_only",
                "real_target_universality_eligible")) {
            if (!java.util.Objects.equals(stored.get(field), current.get(field))) {
                throw new IllegalArgumentException("TARGET_PROVENANCE_BINDING_DRIFT:" + field);
            }
        }
        return stored;
    }

    static void validate(Map<String, Object> value) {
        if (value == null || !CONTRACT.equals(value.get("contract"))) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_CONTRACT_INVALID");
        }
        if (!value.keySet().equals(ROOT_FIELDS)) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_STRUCTURE_INVALID");
        }
        String classification = String.valueOf(value.get("target_classification"));
        if (!CLASSIFICATIONS.contains(classification)) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_CLASSIFICATION_INVALID");
        }
        for (String field : List.of(
                "repository_identity_sha256", "registration_source_sha256", "snapshot_source_sha256",
                "snapshot_manifest_sha256", "provenance_sha256")) {
            requireDigest(String.valueOf(value.get(field)), "TARGET_PROVENANCE_DIGEST_INVALID:" + field);
        }
        Object commit = value.get("repository_commit_sha");
        if (commit != null && !commit.toString().matches("[0-9a-f]{40,64}")) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_COMMIT_INVALID");
        }
        if (!(value.get("snapshot_file_count") instanceof Number count) || count.longValue() < 0) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_FILE_COUNT_INVALID");
        }
        if (!Boolean.TRUE.equals(value.get("review_required"))
                || !Boolean.FALSE.equals(value.get("final_claim_allowed"))
                || !"NOT_RUN".equals(value.get("runtime_validation"))) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_AUTHORITY_INVALID");
        }
        boolean fixture = "FIXTURE".equals(classification);
        boolean eligible = Boolean.TRUE.equals(value.get("real_target_universality_eligible"));
        if (!java.util.Objects.equals(fixture, value.get("fixture_only")) || fixture && eligible) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_FIXTURE_CLAIM_INVALID");
        }
        String repositoryType = String.valueOf(value.get("repository_type"));
        Object clean = value.get("worktree_clean");
        if ("GIT".equals(repositoryType)) {
            if (commit == null || !(clean instanceof Boolean)) {
                throw new IllegalArgumentException("TARGET_PROVENANCE_GIT_BINDING_INVALID");
            }
        } else if (!"NONE".equals(repositoryType) || commit != null || clean != null) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_REPOSITORY_BINDING_INVALID");
        }
        if (eligible && (!"REAL_REPOSITORY".equals(classification) || !"GIT".equals(repositoryType)
                || !Boolean.TRUE.equals(clean) || count.longValue() == 0)) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_ELIGIBILITY_INVALID");
        }
        String expectedClaim = claimState(classification, eligible);
        if (!expectedClaim.equals(value.get("universality_claim_state"))) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_CLAIM_STATE_INVALID");
        }
        Map<String, Object> unsigned = new LinkedHashMap<>(value);
        String claimed = unsigned.remove("provenance_sha256").toString();
        if (!claimed.equals(canonicalDigest(unsigned))) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_DIGEST_MISMATCH");
        }
    }

    private static String classification(String requested, GitIdentity git, boolean fixtureLocation) {
        if ("FIXTURE".equals(requested)) return "FIXTURE";
        if ("SYNTHETIC_SNAPSHOT".equals(requested)) return "SYNTHETIC_SNAPSHOT";
        if ("REAL_REPOSITORY".equals(requested)) {
            if (git == null) throw new IllegalArgumentException("TARGET_PROVENANCE_REAL_REPOSITORY_GIT_REQUIRED");
            if (fixtureLocation) throw new IllegalArgumentException("TARGET_PROVENANCE_FIXTURE_CANNOT_BE_REAL");
            return "REAL_REPOSITORY";
        }
        if (fixtureLocation) return "FIXTURE";
        return git == null ? "UNKNOWN" : "REAL_REPOSITORY";
    }

    private static String classificationBasis(String requested, GitIdentity git, boolean fixtureLocation) {
        if (!"AUTO".equals(requested)) return "OPERATOR_DECLARATION_VALIDATED_" + requested;
        if (fixtureLocation) return "GIT_SCOPE_FIXTURE_SEGMENT";
        if (git != null) return "GIT_REPOSITORY_DISCOVERED";
        return "NO_VERIFIABLE_REPOSITORY_IDENTITY";
    }

    private static String claimState(String classification, boolean eligible) {
        if ("FIXTURE".equals(classification)) return "PROHIBITED_FIXTURE_ONLY";
        if (eligible) return "ELIGIBLE_CANDIDATE_REQUIRES_ACTUAL_EXECUTION";
        return "NOT_ELIGIBLE_PROVENANCE_INCOMPLETE_OR_DIRTY";
    }

    private static GitIdentity gitIdentity(Path sourceRoot) throws Exception {
        GitResult top = git(sourceRoot, List.of("rev-parse", "--show-toplevel"), false);
        if (top.exitCode() != 0) return null;
        Path gitRoot = Path.of(top.output().strip()).toAbsolutePath().normalize();
        if (!sourceRoot.startsWith(gitRoot)) throw new IllegalArgumentException("TARGET_PROVENANCE_GIT_SCOPE_INVALID");
        GitResult commit = git(sourceRoot, List.of("rev-parse", "HEAD"), true);
        String commitSha = commit.output().strip();
        if (!commitSha.matches("[0-9a-f]{40,64}")) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_GIT_COMMIT_INVALID");
        }
        GitResult status = git(sourceRoot,
                List.of("status", "--porcelain", "--untracked-files=all", "--", "."), true);
        GitResult remote = git(sourceRoot, List.of("config", "--get", "remote.origin.url"), false);
        String scope = gitRoot.equals(sourceRoot) ? "." : Hashing.relative(gitRoot, sourceRoot);
        boolean hasRemote = remote.exitCode() == 0 && !remote.output().strip().isBlank();
        String identity = hasRemote ? "GIT_REMOTE_ORIGIN\u0000" + remote.output().strip()
                : "LOCAL_GIT_ROOT\u0000" + gitRoot;
        return new GitIdentity(Hashing.sha256(identity), commitSha, scope, status.output().isBlank(), hasRemote);
    }

    private static GitResult git(Path root, List<String> arguments, boolean required) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(arguments);
        Map<String, String> environment = Map.of(
                "PATH", System.getenv().getOrDefault("PATH", "/usr/bin:/bin"),
                "GIT_TERMINAL_PROMPT", "0", "GIT_CONFIG_NOSYSTEM", "1");
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                command, root, Duration.ofSeconds(30), 4 * 1024 * 1024, environment,
                "TARGET_PROVENANCE_GIT");
        if (result.outputTruncated()) throw new IllegalArgumentException("TARGET_PROVENANCE_GIT_OUTPUT_LIMIT");
        if (required && result.exitCode() != 0) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_GIT_COMMAND_FAILED");
        }
        return new GitResult(result.exitCode(), result.output());
    }

    private static String manifestDigest(Path root, List<Path> files) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        for (Path file : files) {
            bytes.write(Hashing.relative(root, file).getBytes(StandardCharsets.UTF_8));
            bytes.write(0);
            bytes.write(Long.toString(Files.size(file)).getBytes(StandardCharsets.UTF_8));
            bytes.write(0);
            bytes.write(Hashing.file(file).getBytes(StandardCharsets.UTF_8));
            bytes.write(0);
        }
        return Hashing.sha256(bytes.toByteArray());
    }

    private static boolean fixtureScope(String scope) {
        if (".".equals(scope)) return false;
        for (String segment : scope.replace('\\', '/').split("/")) {
            if (FIXTURE_SEGMENTS.contains(segment.toLowerCase(java.util.Locale.ROOT))) return true;
        }
        return false;
    }

    private Path file(String targetId) {
        Path result = storeRoot.resolve(targetId + ".json").normalize();
        if (!result.startsWith(storeRoot)) throw new IllegalArgumentException("TARGET_PROVENANCE_PATH_ESCAPE");
        return result;
    }

    private static Path requireSourceRoot(Path value) {
        if (value == null) throw new IllegalArgumentException("TARGET_PROVENANCE_SOURCE_ROOT_REQUIRED");
        Path root = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_SOURCE_ROOT_INVALID");
        }
        return root;
    }

    private static void requireTargetId(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_TARGET_ID_INVALID");
        }
    }

    private static void requireDigest(String value, String reason) {
        if (value == null || !value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException(reason);
    }

    private static String canonicalDigest(Map<String, Object> value) {
        try {
            return Hashing.sha256(MAPPER.writeValueAsBytes(value));
        } catch (Exception error) {
            throw new IllegalArgumentException("TARGET_PROVENANCE_CANONICALIZATION_FAILED", error);
        }
    }

    private record GitIdentity(
            String repositoryIdentitySha256, String commit, String scope, boolean clean, boolean remoteIdentity) {}
    private record GitResult(int exitCode, String output) {}
}
