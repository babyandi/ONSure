package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
        return learn(sourceRoot, projectId, programId, outputFile, null);
    }

    Map<String, Object> learn(
            Path sourceRoot,
            String projectId,
            String programId,
            Path outputFile,
            Map<String, Object> targetProvenance) throws Exception {
        requireId(projectId, "PROJECT_ID_INVALID");
        requireId(programId, "PROGRAM_ID_INVALID");
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("PROGRAM_SOURCE_ROOT_INVALID");
        }
        List<Path> sourceFiles = Hashing.sourceFiles(root);
        if (sourceFiles.isEmpty()) throw new IllegalStateException("PROGRAM_SOURCE_SET_EMPTY");
        String sourceDigest = Hashing.tree(root);
        Map<String, Object> baseline = sourceBaseline(root, sourceDigest, sourceFiles.size());
        Map<String, Integer> languages = languageInventory(sourceFiles);
        List<Map<String, Object>> components = components(root, sourceFiles);
        List<Map<String, Object>> dependencies = dependencies(root, sourceFiles);
        List<Map<String, Object>> aiComponents = aiComponents(root, sourceFiles);
        List<Map<String, Object>> dataFlows = dataFlows(root, sourceFiles);
        Map<String, Object> workflowInventory = StaticWorkflowInventory.detect(root, sourceFiles);
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
        String profileId = "PROFILE-" + sourceDigest.substring(0, 16);
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", CONTRACT);
        profile.put("profile_id", profileId);
        profile.put("project_id", projectId);
        profile.put("program_id", programId);
        profile.put("source_baseline", baseline);
        if (targetProvenance != null) {
            TargetProvenanceService.validate(targetProvenance);
            if (!sourceDigest.equals(targetProvenance.get("snapshot_source_sha256"))) {
                throw new IllegalArgumentException("PROGRAM_PROFILE_TARGET_PROVENANCE_SOURCE_MISMATCH");
            }
            profile.put("target_provenance", java.util.Collections.unmodifiableMap(
                    new LinkedHashMap<>(targetProvenance)));
        }
        profile.put("purpose", purpose(root));
        profile.put("components", List.copyOf(components));
        profile.put("dependencies", List.copyOf(dependencies));
        profile.put("ai_components", List.copyOf(aiComponents));
        profile.put("data_flows", List.copyOf(dataFlows));
        profile.put("workflow_inventory", workflowInventory);
        Map<String, Object> programUnderstanding = ProgramUnderstandingEngine.infer(
                workflowInventory, sourceDigest);
        profile.put("program_understanding", programUnderstanding);
        profile.put("business_semantic_hypotheses", BusinessSemanticHypothesisEngine.infer(
                programUnderstanding, components));
        profile.put("unknowns", List.copyOf(unknowns));
        profile.put("conflicts", List.copyOf(conflicts));
        profile.put("evidence_refs", List.of(
                "source-tree:sha256:" + sourceDigest,
                "source-set-count:" + sourceFiles.size()));
        profile.put("revision", 1);
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
        writeAtomic(outputFile, profile);
        return Map.copyOf(profile);
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
}
