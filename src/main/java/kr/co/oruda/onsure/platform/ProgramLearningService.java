package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Builds a source-bound static Program Profile without claiming runtime understanding. */
public final class ProgramLearningService {
    public static final String CONTRACT = "ONSURE_PROGRAM_PROFILE_V1";
    private static final int MAX_COMPONENTS = 5000;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> learn(
            Path sourceRoot,
            String projectId,
            String programId,
            Path outputFile) throws Exception {
        requireId(projectId, "PROJECT_ID_INVALID");
        requireId(programId, "PROGRAM_ID_INVALID");
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("PROGRAM_SOURCE_ROOT_INVALID");
        }
        Path output = outputFile.toAbsolutePath().normalize();
        ParentProfile parent = readParentProfile(output, projectId, programId);
        List<Path> sourceFiles = Hashing.sourceFiles(root);
        if (sourceFiles.isEmpty()) throw new IllegalStateException("PROGRAM_SOURCE_SET_EMPTY");
        String sourceDigest = Hashing.tree(root);
        List<Map<String, Object>> sourceInventory = sourceInventory(root, sourceFiles);
        Map<String, Object> changeSet = changeSet(parent, sourceInventory);
        Map<String, Object> baseline = sourceBaseline(root, sourceDigest, sourceFiles.size());
        Map<String, Object> gitMetadata = gitMetadataInventory(root);
        Map<String, Integer> languages = languageInventory(sourceFiles);
        List<Map<String, Object>> components = components(root, sourceFiles);
        List<Map<String, Object>> dependencies = dependencies(root, sourceFiles);
        List<Map<String, Object>> aiComponents = new ArrayList<>(aiComponents(root, sourceFiles));
        aiComponents.addAll(toolContracts(root, sourceFiles));
        aiComponents.addAll(executionLogs(root, sourceFiles));
        List<Map<String, Object>> dataFlows = dataFlows(root, sourceFiles);
        Set<String> unknowns = new LinkedHashSet<>();
        Set<String> conflicts = new LinkedHashSet<>();
        if (components.isEmpty()) unknowns.add("NO_COMPONENT_ENTRYPOINT_DETECTED_STATICALLY");
        if (dependencies.isEmpty()) unknowns.add("DEPENDENCY_INVENTORY_EMPTY_OR_UNSUPPORTED");
        if (aiComponents.isEmpty() && containsAiSignals(root, sourceFiles)) {
            unknowns.add("AI_SIGNAL_PRESENT_WITHOUT_STRUCTURED_AI_COMPONENT_METADATA");
        }
        if (Files.isRegularFile(root.resolve("pom.xml")) && Files.isRegularFile(root.resolve("build.gradle"))) {
            conflicts.add("MULTIPLE_PRIMARY_BUILD_DESCRIPTORS");
        }
        if (Files.isRegularFile(root.resolve("package-lock.json"))
                && Files.isRegularFile(root.resolve("yarn.lock"))) {
            conflicts.add("MULTIPLE_NODE_LOCKFILES");
        }
        int revision = parent == null ? 1 : parent.revision() + 1;
        String profileId = "PROFILE-" + sourceDigest.substring(0, 16) + "-R" + revision;
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", CONTRACT);
        profile.put("profile_id", profileId);
        profile.put("project_id", projectId);
        profile.put("program_id", programId);
        profile.put("source_baseline", baseline);
        profile.put("git_metadata", gitMetadata);
        profile.put("source_inventory", sourceInventory);
        profile.put("parent_profile", parent == null ? null : parent.reference());
        profile.put("change_set", changeSet);
        profile.put("purpose", purpose(root));
        profile.put("components", List.copyOf(components));
        profile.put("dependencies", List.copyOf(dependencies));
        profile.put("ai_components", List.copyOf(aiComponents));
        profile.put("data_flows", List.copyOf(dataFlows));
        profile.put("unknowns", List.copyOf(unknowns));
        profile.put("conflicts", List.copyOf(conflicts));
        profile.put("evidence_refs", List.of(
                "source-tree:sha256:" + sourceDigest,
                "source-set-count:" + sourceFiles.size()));
        profile.put("revision", revision);
        profile.put("state", "PROFILE_CANDIDATE");
        profile.put("generated_at", Instant.now().toString());
        profile.put("learning_method", "STATIC_REPOSITORY_UNDERSTANDING_V2");
        profile.put("language_inventory", languages);
        profile.put("dynamic_trace", "NOT_RUN");
        profile.put("runtime_verification", "NOT_RUN");
        profile.put("human_review", "NOT_RUN");
        profile.put("independent_validation", "NOT_RUN");
        profile.put("final_claim_allowed", false);
        profile.put("profile_sha256", sha256(mapper.writeValueAsBytes(profile)));
        writeAtomic(output, profile);
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(profile));
    }

    private List<Map<String, Object>> sourceInventory(Path root, List<Path> sourceFiles) throws Exception {
        List<Map<String, Object>> inventory = new ArrayList<>();
        for (Path file : sourceFiles) {
            inventory.add(Map.of(
                    "path", Hashing.relative(root, file),
                    "sha256", Hashing.file(file)));
        }
        inventory.sort(Comparator.comparing(item -> item.get("path").toString()));
        return List.copyOf(inventory);
    }

    private Map<String, Object> changeSet(
            ParentProfile parent, List<Map<String, Object>> currentInventory) {
        Map<String, String> current = inventoryIndex(currentInventory, "CURRENT_SOURCE_INVENTORY_INVALID");
        if (parent == null) {
            return Map.of(
                    "mode", "FULL",
                    "added", List.copyOf(current.keySet()),
                    "modified", List.of(),
                    "deleted", List.of(),
                    "unchanged", List.of(),
                    "compared_file_count", 0);
        }
        Map<String, String> previous = parent.inventory();
        List<String> added = current.keySet().stream().filter(path -> !previous.containsKey(path)).toList();
        List<String> modified = current.keySet().stream()
                .filter(previous::containsKey)
                .filter(path -> !current.get(path).equals(previous.get(path))).toList();
        List<String> deleted = previous.keySet().stream().filter(path -> !current.containsKey(path)).toList();
        List<String> unchanged = current.keySet().stream()
                .filter(previous::containsKey)
                .filter(path -> current.get(path).equals(previous.get(path))).toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("mode", "INCREMENTAL");
        result.put("added", added);
        result.put("modified", modified);
        result.put("deleted", deleted);
        result.put("unchanged", unchanged);
        result.put("compared_file_count", previous.size());
        return java.util.Collections.unmodifiableMap(new TreeMap<>(result));
    }

    private ParentProfile readParentProfile(
            Path output, String projectId, String programId) throws Exception {
        if (!Files.exists(output, LinkOption.NOFOLLOW_LINKS)) return null;
        if (!Files.isRegularFile(output, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(output)) {
            throw new IllegalStateException("PARENT_PROGRAM_PROFILE_FILE_INVALID");
        }
        JsonNode node = mapper.readTree(output.toFile());
        if (!CONTRACT.equals(node.path("contract").asText())) {
            throw new IllegalStateException("PARENT_PROGRAM_PROFILE_CONTRACT_INVALID");
        }
        if (!projectId.equals(node.path("project_id").asText())
                || !programId.equals(node.path("program_id").asText())) {
            throw new IllegalStateException("PARENT_PROGRAM_PROFILE_IDENTITY_MISMATCH");
        }
        String declaredHash = node.path("profile_sha256").asText();
        ObjectNode hashBody = ((ObjectNode) node.deepCopy());
        hashBody.remove("profile_sha256");
        if (!declaredHash.matches("[0-9a-f]{64}")
                || !declaredHash.equals(sha256(mapper.writeValueAsBytes(hashBody)))) {
            throw new IllegalStateException("PARENT_PROGRAM_PROFILE_HASH_INVALID");
        }
        int revision = node.path("revision").asInt(0);
        if (revision < 1) throw new IllegalStateException("PARENT_PROGRAM_PROFILE_REVISION_INVALID");
        JsonNode baseline = node.path("source_baseline");
        String treeHash = baseline.path("source_tree_sha256").asText();
        if (!treeHash.matches("[0-9a-f]{64}")) {
            throw new IllegalStateException("PARENT_PROGRAM_PROFILE_SOURCE_HASH_INVALID");
        }
        List<Map<String, Object>> inventoryValues = new ArrayList<>();
        if (!node.path("source_inventory").isArray()) {
            throw new IllegalStateException("PARENT_PROGRAM_PROFILE_INVENTORY_MISSING");
        }
        for (JsonNode item : node.path("source_inventory")) {
            inventoryValues.add(Map.of(
                    "path", item.path("path").asText(),
                    "sha256", item.path("sha256").asText()));
        }
        Map<String, String> inventory = inventoryIndex(
                inventoryValues, "PARENT_PROGRAM_PROFILE_INVENTORY_INVALID");
        Map<String, Object> reference = Map.of(
                "profile_id", node.path("profile_id").asText(),
                "profile_sha256", declaredHash,
                "source_tree_sha256", treeHash,
                "revision", revision);
        return new ParentProfile(revision, inventory, reference);
    }

    private static Map<String, String> inventoryIndex(
            List<Map<String, Object>> inventory, String error) {
        Map<String, String> result = new TreeMap<>();
        for (Map<String, Object> item : inventory) {
            String path = String.valueOf(item.get("path"));
            String hash = String.valueOf(item.get("sha256"));
            if (path.isBlank() || path.startsWith("/") || path.contains("..") || path.contains("\\")
                    || !hash.matches("[0-9a-f]{64}") || result.put(path, hash) != null) {
                throw new IllegalStateException(error);
            }
        }
        return java.util.Collections.unmodifiableMap(new TreeMap<>(result));
    }

    private Map<String, Object> sourceBaseline(Path root, String treeDigest, int fileCount) throws Exception {
        GitBaseline git = gitBaseline(root);
        Map<String, Object> baseline = new LinkedHashMap<>();
        baseline.put("reference_type", git == null ? "SOURCE_TREE_SHA256" : "GIT_COMMIT");
        baseline.put("reference_value", git == null ? treeDigest : git.commit());
        baseline.put("git_commit_sha", git == null ? null : git.commit());
        baseline.put("source_tree_sha256", treeDigest);
        baseline.put("worktree_clean", git == null ? null : git.clean());
        baseline.put("snapshot_complete", true);
        baseline.put("source_file_count", fileCount);
        return java.util.Collections.unmodifiableMap(baseline);
    }

    private static Map<String, Object> gitMetadataInventory(Path root) {
        GitMetadataInventory.Inventory inventory = GitMetadataInventory.inspect(root);
        List<Map<String, Object>> submodules = new ArrayList<>();
        for (GitMetadataInventory.Submodule submodule : inventory.submodules()) {
            submodules.add(Map.of(
                    "path", submodule.path(),
                    "commit_sha", submodule.commitSha(),
                    "status", submodule.status().name()));
        }
        List<Map<String, Object>> remotes = new ArrayList<>();
        for (GitMetadataInventory.Remote remote : inventory.remotes()) {
            remotes.add(Map.of(
                    "name", remote.name(),
                    "url", remote.url(),
                    "provider", remote.provider().name()));
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("repository", inventory.repository());
        metadata.put("submodules", List.copyOf(submodules));
        metadata.put("lfs_patterns", List.copyOf(inventory.lfsPatterns()));
        metadata.put("remotes", List.copyOf(remotes));
        return java.util.Collections.unmodifiableMap(metadata);
    }

    private GitBaseline gitBaseline(Path root) throws Exception {
        CommandResult top = git(root, List.of("rev-parse", "--show-toplevel"), 15);
        if (top.exitCode() != 0) return null;
        requireCompleteGitOutput(top);
        CommandResult commit = git(root, List.of("rev-parse", "HEAD"), 15);
        requireCompleteGitOutput(commit);
        if (commit.exitCode() != 0 || !commit.output().strip().matches("[0-9a-f]{40,64}")) {
            throw new IllegalStateException("PROGRAM_GIT_COMMIT_UNVERIFIED");
        }
        CommandResult status = git(root,
                List.of("status", "--porcelain", "--untracked-files=all", "--", "."), 30);
        requireCompleteGitOutput(status);
        if (status.exitCode() != 0) throw new IllegalStateException("PROGRAM_GIT_STATUS_UNVERIFIED");
        return new GitBaseline(commit.output().strip(), status.output().isBlank());
    }

    private static void requireCompleteGitOutput(CommandResult result) {
        if (result.outputTruncated()) throw new IllegalStateException("PROGRAM_GIT_OUTPUT_LIMIT_EXCEEDED");
    }

    private static Map<String, Integer> languageInventory(List<Path> files) {
        Map<String, Integer> languages = new TreeMap<>();
        for (Path file : files) {
            String extension = extension(file.getFileName().toString());
            if (extension != null) languages.merge(extension, 1, Integer::sum);
        }
        return Map.copyOf(languages);
    }

    private List<Map<String, Object>> components(Path root, List<Path> files) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            if (values.size() >= MAX_COMPONENTS) break;
            String relative = Hashing.relative(root, file);
            String name = file.getFileName().toString();
            String kind = componentKind(relative, name);
            if (kind == null) continue;
            values.add(item("COMP-" + Hashing.sha256(relative).substring(0, 12), name, kind,
                    relative, confidence(kind), false));
        }
        values.sort(Comparator.comparing(value -> value.get("id").toString()));
        return List.copyOf(values);
    }

    private List<Map<String, Object>> dependencies(Path root, List<Path> files) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            String relative = Hashing.relative(root, file);
            String lower = file.getFileName().toString().toLowerCase();
            if (!List.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json",
                    "requirements.txt", "pyproject.toml", "go.mod", "cargo.toml",
                    "composer.json", "gemfile").contains(lower)) continue;
            values.add(item("DEP-" + Hashing.sha256(relative).substring(0, 12),
                    file.getFileName().toString(), "DEPENDENCY_DESCRIPTOR", relative, 0.9, false));
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> aiComponents(Path root, List<Path> files) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            String relative = Hashing.relative(root, file);
            String lower = relative.toLowerCase();
            if (lower.contains("prompt") || lower.contains("rag") || lower.contains("agent")
                    || lower.contains("embedding") || lower.contains("vector")
                    || lower.contains("model") || lower.contains("llm")) {
                values.add(item("AI-" + Hashing.sha256(relative).substring(0, 12),
                        file.getFileName().toString(), "AI_RELATED_SOURCE", relative, 0.55, false));
            }
        }
        return List.copyOf(values);
    }

    /**
     * TOOL_CONTRACT detection (FR-02-A: Tool Contract inventory). Uses the same lightweight
     * path/filename signal matching as {@link #aiComponents(Path, List)} -- no deep semantic
     * validation, just structural detection -- but goes one step further for files it matches:
     * it attempts to parse them as JSON and, if the parsed shape looks like a recognizable
     * tool/function-calling schema, extracts the declared tool names. Parsing never throws: a
     * file that matches the path signal but fails to parse (or doesn't look like a tool schema)
     * is still recorded as a TOOL_CONTRACT candidate, just at a lower confidence, mirroring the
     * fail-safe philosophy in {@link GitMetadataInventory} (degrade, never crash the whole scan).
     */
    private List<Map<String, Object>> toolContracts(Path root, List<Path> files) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            String relative = Hashing.relative(root, file);
            if (!isToolContractCandidate(relative.toLowerCase(Locale.ROOT))) continue;
            List<String> declaredToolNames = null;
            double confidence = 0.5;
            try {
                JsonNode node = mapper.readTree(file.toFile());
                declaredToolNames = extractDeclaredToolNames(node);
                if (declaredToolNames != null) confidence = 0.85;
            } catch (Exception ignored) {
                // Not parseable JSON, or an empty/unreadable file -- keep the lower,
                // path-signal-only confidence rather than dropping the candidate.
            }
            Map<String, Object> value = new LinkedHashMap<>(item(
                    "TC-" + Hashing.sha256(relative).substring(0, 12),
                    file.getFileName().toString(), "TOOL_CONTRACT", relative, confidence, false));
            if (declaredToolNames != null) {
                value.put("declared_tool_names", List.copyOf(declaredToolNames));
            }
            values.add(Map.copyOf(value));
        }
        return List.copyOf(values);
    }

    /**
     * Path-segment-aware Tool Contract signal. Well-known conventional filenames (mcp.json,
     * tools.json, tool-contract*.json, *.tool.json) always match. Otherwise, "tool" and a
     * contract/schema/manifest term must co-occur within the SAME path segment (a directory
     * name or the filename) -- not merely anywhere in the full path -- so an unrelated
     * "tools/" utility-scripts directory (e.g. {@code tools/build.sh}, or {@code tools/}
     * paired with an unrelated {@code schema-check.py} several segments away) does not match
     * just because both words appear somewhere below it. As a narrow addition, a "tool"/"tools"
     * directory immediately containing a file whose name itself carries a contract/schema/
     * manifest term (e.g. {@code tools/contract.json}) also matches.
     */
    private static boolean isToolContractCandidate(String relativeLower) {
        String[] segments = relativeLower.split("/");
        String filename = segments[segments.length - 1];
        if (filename.equals("mcp.json") || filename.equals("tools.json")
                || filename.matches("tool-contract.*\\.json")
                || filename.matches(".*\\.tool\\.json")) {
            return true;
        }
        boolean filenameHasContractTerm = filename.contains("contract") || filename.contains("schema")
                || filename.contains("manifest");
        if (segments.length >= 2) {
            String parentDir = segments[segments.length - 2];
            if ((parentDir.equals("tool") || parentDir.equals("tools")) && filenameHasContractTerm) {
                return true;
            }
        }
        for (String segment : segments) {
            if (segment.contains("tool")
                    && (segment.contains("contract") || segment.contains("schema") || segment.contains("manifest"))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recognizes two tool/function-calling schema shapes: an OpenAI-style top-level array of
     * objects each with "name" plus "parameters" or "input_schema", or an MCP-style object with
     * a top-level "tools" array of such objects. Returns null (not a recognizable shape) rather
     * than throwing, so callers can degrade to path-signal-only detection.
     */
    private static List<String> extractDeclaredToolNames(JsonNode node) {
        if (node.isArray()) {
            return toolNamesFromArray(node);
        }
        if (node.isObject() && node.path("tools").isArray()) {
            return toolNamesFromArray(node.path("tools"));
        }
        return null;
    }

    private static List<String> toolNamesFromArray(JsonNode array) {
        List<String> names = new ArrayList<>();
        for (JsonNode entry : array) {
            if (!entry.isObject()) continue;
            JsonNode name = entry.path("name");
            boolean hasSchema = entry.has("parameters") || entry.has("input_schema");
            if (name.isTextual() && !name.asText().isBlank() && hasSchema) {
                names.add(name.asText());
            }
        }
        return names.isEmpty() ? null : List.copyOf(names);
    }

    /**
     * EXECUTION_LOG detection (FR-02-A: execution log inventory). Path-signal-only, no content
     * parsing -- matches an exact "logs"/"log" path segment, or filenames matching *.log,
     * execution-log*, trace*.json, run-log*. Segment/prefix matching (not raw
     * {@code contains("log")}) deliberately avoids false positives on unrelated words that
     * merely contain "log" as a substring, such as "catalog", "login", or "dialogue".
     */
    private List<Map<String, Object>> executionLogs(Path root, List<Path> files) {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            String relative = Hashing.relative(root, file);
            if (!isExecutionLogCandidate(relative.toLowerCase(Locale.ROOT))) continue;
            values.add(item("LOG-" + Hashing.sha256(relative).substring(0, 12),
                    file.getFileName().toString(), "EXECUTION_LOG", relative, 0.6, false));
        }
        return List.copyOf(values);
    }

    private static boolean isExecutionLogCandidate(String relativeLower) {
        String[] segments = relativeLower.split("/");
        for (String segment : segments) {
            if (segment.equals("logs") || segment.equals("log")) return true;
        }
        String filename = segments[segments.length - 1];
        return filename.endsWith(".log")
                || filename.matches("execution-log.*")
                || filename.matches("trace.*\\.json")
                || filename.matches("run-log.*");
    }

    private List<Map<String, Object>> dataFlows(Path root, List<Path> files) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            String relative = Hashing.relative(root, file);
            String lower = relative.toLowerCase();
            String kind = lower.contains("controller") || lower.contains("route") ? "REQUEST_FLOW"
                    : lower.contains("repository") || lower.contains("dao") ? "PERSISTENCE_FLOW"
                    : lower.contains("event") || lower.contains("queue") ? "EVENT_FLOW" : null;
            if (kind != null) {
                values.add(item("FLOW-" + Hashing.sha256(relative).substring(0, 12),
                        file.getFileName().toString(), kind, relative, 0.5, false));
            }
        }
        return List.copyOf(values);
    }

    private String purpose(Path root) {
        for (String candidate : List.of("README.md", "README", "docs/00_PRODUCT_BASELINE.md")) {
            Path file = root.resolve(candidate);
            if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) continue;
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String value = line.replaceFirst("^#+\\s*", "").trim();
                    if (!value.isBlank() && !value.startsWith("![") && !value.startsWith("[!")) {
                        return value.length() > 500 ? value.substring(0, 500) : value;
                    }
                }
            } catch (Exception ignored) {}
        }
        return "Purpose was not explicitly discovered by static repository learning.";
    }

    private static boolean containsAiSignals(Path root, List<Path> files) {
        return files.stream().map(file -> Hashing.relative(root, file).toLowerCase())
                .anyMatch(value -> value.contains("agent") || value.contains("prompt")
                        || value.contains("rag") || value.contains("llm")
                        || value.contains("embedding") || value.contains("vector"));
    }

    private static Map<String, Object> item(
            String id, String name, String kind, String location, double confidence, boolean verified) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("name", name);
        value.put("kind", kind);
        value.put("source_locations", List.of(location));
        value.put("confidence", confidence);
        value.put("verified", verified);
        return Map.copyOf(value);
    }

    private static String componentKind(String relative, String name) {
        String lower = relative.toLowerCase();
        if (lower.endsWith("dockerfile") || lower.contains("docker-compose")) return "DEPLOYMENT";
        if (lower.endsWith("pom.xml") || lower.endsWith("build.gradle") || lower.endsWith("package.json")) return "BUILD";
        if (lower.contains("controller") || lower.contains("route")) return "API_ENTRYPOINT";
        if (lower.contains("service")) return "SERVICE";
        if (lower.contains("repository") || lower.contains("dao")) return "PERSISTENCE";
        if (lower.contains("main.") || name.equals("main.py") || name.equals("index.js") || name.equals("app.py")) return "ENTRYPOINT";
        if (lower.contains("test") || lower.contains("spec")) return "TEST";
        return null;
    }

    private static double confidence(String kind) {
        return switch (kind) {
            case "BUILD", "DEPLOYMENT" -> 0.95;
            case "API_ENTRYPOINT", "SERVICE", "PERSISTENCE", "ENTRYPOINT" -> 0.75;
            default -> 0.6;
        };
    }

    private static String extension(String name) {
        int index = name.lastIndexOf('.');
        if (index < 0 || index == name.length() - 1) return null;
        String value = name.substring(index + 1).toLowerCase();
        return value.matches("[a-z0-9]{1,12}") ? value : null;
    }

    private void writeAtomic(Path outputFile, Object value) throws Exception {
        Path output = outputFile.toAbsolutePath().normalize();
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static CommandResult git(Path root, List<String> arguments, long timeoutSeconds) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(arguments);
        Map<String, String> environment = new LinkedHashMap<>();
        String path = System.getenv("PATH");
        if (path != null) environment.put("PATH", path);
        environment.put("GIT_TERMINAL_PROMPT", "0");
        BoundedProcessRunner.Result result = BoundedProcessRunner.run(
                command, root, Duration.ofSeconds(timeoutSeconds), environment, "PROGRAM_GIT");
        return new CommandResult(result.exitCode(), result.output(), result.outputTruncated());
    }

    private static void requireId(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(error);
        }
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record GitBaseline(String commit, boolean clean) {}
    private record CommandResult(int exitCode, String output, boolean outputTruncated) {}
    private record ParentProfile(
            int revision, Map<String, String> inventory, Map<String, Object> reference) {}
}
