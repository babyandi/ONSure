package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
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
import java.util.concurrent.TimeUnit;

/** Builds an evidence-bound static Program Profile from the exact target source tree. */
public final class ProgramLearningService {
    public static final String CONTRACT = "ONSURE_PROGRAM_PROFILE_V1";
    private static final int MAX_FILES = 50_000;
    private static final long MAX_TEXT_BYTES = 2_000_000;
    private static final Map<String, String> LANGUAGE_BY_EXTENSION = Map.ofEntries(
            Map.entry("java", "Java"), Map.entry("kt", "Kotlin"), Map.entry("py", "Python"),
            Map.entry("js", "JavaScript"), Map.entry("ts", "TypeScript"), Map.entry("tsx", "TypeScript"),
            Map.entry("jsx", "JavaScript"), Map.entry("go", "Go"), Map.entry("rs", "Rust"),
            Map.entry("cs", "C#"), Map.entry("cpp", "C++"), Map.entry("c", "C"),
            Map.entry("php", "PHP"), Map.entry("rb", "Ruby"), Map.entry("sql", "SQL"),
            Map.entry("sh", "Shell"));
    private static final List<String> AI_TOKENS = List.of(
            "openai", "anthropic", "gemini", "ollama", "langchain", "llamaindex",
            "prompt", "embedding", "vector", "rag", "agent", "tool_call", "model_id");
    private static final List<String> DATA_TOKENS = List.of(
            "jdbc", "postgres", "mysql", "mariadb", "oracle", "mongodb", "redis", "kafka",
            "rabbitmq", "s3", "minio", "datasource", "database", "repository", "sql");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> learn(
            Path sourceRoot, String projectId, String programId, Path outputFile) throws Exception {
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("PROGRAM_SOURCE_ROOT_INVALID");
        }
        String treeDigest = Hashing.tree(root);
        SourceBaseline baseline = sourceBaseline(root, treeDigest);
        List<Path> files = sourceFiles(root);
        Map<String, Integer> languages = detectLanguages(files);
        List<Map<String, Object>> components = detectComponents(root, files);
        List<Map<String, Object>> dependencies = detectDependencies(root, files);
        List<Map<String, Object>> aiComponents = detectTokenComponents(root, files, AI_TOKENS, "AI_COMPONENT");
        List<Map<String, Object>> dataFlows = detectTokenComponents(root, files, DATA_TOKENS, "DATA_FLOW");
        List<String> unknowns = new ArrayList<>();
        List<String> conflicts = new ArrayList<>();
        if (languages.isEmpty()) unknowns.add("NO_SUPPORTED_SOURCE_LANGUAGE_DETECTED");
        if (components.isEmpty()) unknowns.add("NO_COMPONENT_BOUNDARY_DETECTED");
        if (dependencies.isEmpty()) unknowns.add("DEPENDENCY_MANIFEST_NOT_DETECTED");
        if (!aiComponents.isEmpty() && dataFlows.isEmpty()) {
            conflicts.add("AI_COMPONENT_PRESENT_WITHOUT_DETECTED_DATA_FLOW");
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", CONTRACT);
        profile.put("profile_id", "PP-" + treeDigest.substring(0, 20));
        profile.put("project_id", requireId(projectId, "PROJECT_ID_INVALID"));
        profile.put("program_id", requireId(programId, "PROGRAM_ID_INVALID"));
        profile.put("source_baseline", Map.of(
                "git_commit_sha", baseline.commit(),
                "source_tree_sha256", treeDigest,
                "clean", baseline.clean()));
        profile.put("purpose", detectPurpose(root));
        profile.put("components", components);
        profile.put("dependencies", dependencies);
        profile.put("ai_components", aiComponents);
        profile.put("data_flows", dataFlows);
        profile.put("unknowns", List.copyOf(unknowns));
        profile.put("conflicts", List.copyOf(conflicts));
        profile.put("evidence_refs", List.of(
                "source-tree:sha256:" + treeDigest,
                "inventory:file-count:" + files.size(),
                "language-inventory:sha256:" + sha256(mapper.writeValueAsBytes(new TreeMap<>(languages)))));
        profile.put("revision", 1);
        profile.put("state", "PROFILE_CANDIDATE");
        profile.put("generated_at", Instant.now().toString());
        profile.put("learning_method", "STATIC_REPOSITORY_UNDERSTANDING_V1");
        profile.put("language_inventory", languages);
        profile.put("dynamic_trace", "NOT_RUN");
        profile.put("runtime_verification", "NOT_RUN");
        profile.put("final_claim_allowed", false);
        profile.put("profile_sha256", sha256(mapper.writeValueAsBytes(profile)));
        writeAtomic(outputFile, profile);
        return Map.copyOf(profile);
    }

    private static List<Path> sourceFiles(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !excluded(root, path))
                    .sorted(Comparator.comparing(path -> Hashing.relative(root, path)))
                    .limit(MAX_FILES + 1L)
                    .forEach(files::add);
        }
        if (files.size() > MAX_FILES) throw new IllegalStateException("PROGRAM_SOURCE_FILE_LIMIT_EXCEEDED");
        return List.copyOf(files);
    }

    private static boolean excluded(Path root, Path path) {
        String relative = Hashing.relative(root, path);
        return relative.startsWith(".git/") || relative.startsWith("target/")
                || relative.startsWith("node_modules/") || relative.startsWith(".onsure/")
                || relative.startsWith("receipts/") || relative.contains("/__pycache__/")
                || relative.endsWith(".class") || relative.endsWith(".jar")
                || relative.endsWith(".zip") || relative.endsWith(".png")
                || relative.endsWith(".jpg") || relative.endsWith(".pdf");
    }

    private static Map<String, Integer> detectLanguages(List<Path> files) {
        Map<String, Integer> result = new TreeMap<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            int dot = name.lastIndexOf('.');
            if (dot < 0) continue;
            String language = LANGUAGE_BY_EXTENSION.get(name.substring(dot + 1).toLowerCase(Locale.ROOT));
            if (language != null) result.merge(language, 1, Integer::sum);
        }
        return Map.copyOf(result);
    }

    private static List<Map<String, Object>> detectComponents(Path root, List<Path> files) throws Exception {
        Map<String, List<String>> byBoundary = new TreeMap<>();
        for (Path file : files) {
            String relative = Hashing.relative(root, file);
            String[] parts = relative.split("/");
            String boundary = parts.length > 1 ? parts[0] : "root";
            if (boundary.startsWith(".")) continue;
            byBoundary.computeIfAbsent(boundary, ignored -> new ArrayList<>()).add(relative);
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, List<String>> entry : byBoundary.entrySet()) {
            List<String> locations = entry.getValue().stream().limit(50).toList();
            result.add(evidenceBoundItem(
                    "CMP-" + sha256(entry.getKey()).substring(0, 12),
                    entry.getKey(), "SOURCE_BOUNDARY", locations,
                    entry.getValue().size() > 1 ? 0.80 : 0.55, false));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> detectDependencies(Path root, List<Path> files) throws Exception {
        Set<String> manifestNames = Set.of(
                "pom.xml", "build.gradle", "build.gradle.kts", "package.json", "package-lock.json",
                "requirements.txt", "pyproject.toml", "poetry.lock", "go.mod", "Cargo.toml",
                "composer.json", "Gemfile", "gradle.properties");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            if (!manifestNames.contains(name)) continue;
            String relative = Hashing.relative(root, file);
            result.add(evidenceBoundItem(
                    "DEP-" + sha256(relative).substring(0, 12),
                    name, "DEPENDENCY_MANIFEST", List.of(relative), 0.95, true));
        }
        return List.copyOf(result);
    }

    private static List<Map<String, Object>> detectTokenComponents(
            Path root, List<Path> files, List<String> tokens, String kind) throws Exception {
        Map<String, Set<String>> matches = new TreeMap<>();
        for (Path file : files) {
            if (!isTextCandidate(file)) continue;
            if (Files.size(file) > MAX_TEXT_BYTES) continue;
            String content;
            try { content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT); }
            catch (Exception ignored) { continue; }
            for (String token : tokens) {
                if (content.contains(token)) {
                    matches.computeIfAbsent(token, ignored -> new LinkedHashSet<>())
                            .add(Hashing.relative(root, file));
                }
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, Set<String>> entry : matches.entrySet()) {
            result.add(evidenceBoundItem(
                    kind.substring(0, Math.min(3, kind.length())) + "-"
                            + sha256(entry.getKey()).substring(0, 12),
                    entry.getKey(), kind,
                    entry.getValue().stream().limit(50).toList(), 0.72, false));
        }
        return List.copyOf(result);
    }

    private static boolean isTextCandidate(Path file) {
        String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.matches(".*\\.(java|kt|py|js|ts|tsx|jsx|go|rs|cs|cpp|c|h|php|rb|sql|sh|json|yaml|yml|xml|toml|properties|md|txt)$")
                || !name.contains(".");
    }

    private static Map<String, Object> evidenceBoundItem(
            String id, String name, String kind, List<String> locations,
            double confidence, boolean verified) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("id", id);
        value.put("name", name);
        value.put("kind", kind);
        value.put("source_locations", List.copyOf(locations));
        value.put("confidence", confidence);
        value.put("verified", verified);
        return Map.copyOf(value);
    }

    private static String detectPurpose(Path root) {
        for (String candidate : List.of("README.md", "README", "docs/00_PRODUCT_BASELINE.md")) {
            Path file = root.resolve(candidate);
            if (!Files.isRegularFile(file)) continue;
            try {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    String value = line.replaceFirst("^#+\\s*", "").trim();
                    if (!value.isBlank() && !value.startsWith("!") && value.length() >= 3) {
                        return value.length() > 300 ? value.substring(0, 300) : value;
                    }
                }
            } catch (Exception ignored) {}
        }
        return "Purpose requires owner confirmation; static repository understanding completed.";
    }

    private static SourceBaseline sourceBaseline(Path root, String treeDigest) throws Exception {
        Process process = new ProcessBuilder("git", "-C", root.toString(), "rev-parse", "HEAD")
                .redirectErrorStream(true).start();
        boolean completed = process.waitFor(10, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            return new SourceBaseline(treeDigest, false);
        }
        String value = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        if (process.exitValue() != 0 || !value.matches("(?:[0-9a-f]{40}|[0-9a-f]{64})")) {
            return new SourceBaseline(treeDigest, true);
        }
        Process status = new ProcessBuilder(
                "git", "-C", root.toString(), "status", "--porcelain", "--untracked-files=all", "--", ".")
                .redirectErrorStream(true).start();
        status.waitFor(10, TimeUnit.SECONDS);
        String dirty = new String(status.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return new SourceBaseline(value, status.exitValue() == 0 && dirty.isBlank());
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

    private static String requireId(String value, String error) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(error);
        }
        return value;
    }

    private static String sha256(String value) throws Exception {
        return sha256(value.getBytes(StandardCharsets.UTF_8));
    }

    private static String sha256(byte[] value) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    }

    private record SourceBaseline(String commit, boolean clean) {}
}
