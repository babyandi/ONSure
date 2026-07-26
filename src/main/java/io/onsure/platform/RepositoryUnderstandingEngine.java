package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/** Generates an evidence-bound Program Profile candidate from actual repository bytes. */
public final class RepositoryUnderstandingEngine {
    public static final String CONTRACT = "ONSURE_PROGRAM_PROFILE_V1";
    private static final int MAX_TEXT_BYTES = 1_000_000;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Map<String, Object> learn(
            Path sourceRoot, String projectId, String programId, Path output) throws Exception {
        requireId(projectId, "projectId");
        requireId(programId, "programId");
        Path root = sourceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("PROGRAM_SOURCE_ROOT_MISSING");

        String sourceDigest = Hashing.tree(root);
        SourceIdentity identity = sourceIdentity(root, sourceDigest);
        List<Path> files = inventory(root);
        if (files.isEmpty()) throw new IllegalStateException("PROGRAM_SOURCE_INVENTORY_EMPTY");

        List<Map<String, Object>> components = components(root, files);
        List<Map<String, Object>> dependencies = dependencies(root, files);
        List<Map<String, Object>> aiComponents = aiComponents(root, files);
        List<Map<String, Object>> dataFlows = dataFlows(root, files);
        List<String> unknowns = unknowns(root, files, components);
        List<String> conflicts = conflicts(files);
        String purpose = discoverPurpose(root, files);

        String profileId = "PROFILE-" + Hashing.sha256(
                projectId + "|" + programId + "|" + sourceDigest).substring(0, 20);
        List<String> evidence = new ArrayList<>();
        evidence.add("SOURCE_TREE_SHA256:" + sourceDigest);
        for (Path file : files.stream().limit(200).toList()) {
            evidence.add("FILE:" + Hashing.relative(root, file) + ":" + Hashing.file(file));
        }

        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("contract", CONTRACT);
        profile.put("profile_id", profileId);
        profile.put("project_id", projectId);
        profile.put("program_id", programId);
        profile.put("source_baseline", Map.of(
                "source_type", identity.sourceType(),
                "source_ref", identity.sourceRef(),
                "git_commit_sha", identity.gitCommitSha(),
                "source_tree_sha256", sourceDigest,
                "clean", identity.clean()));
        profile.put("purpose", purpose);
        profile.put("components", components);
        profile.put("dependencies", dependencies);
        profile.put("ai_components", aiComponents);
        profile.put("data_flows", dataFlows);
        profile.put("unknowns", unknowns);
        profile.put("conflicts", conflicts);
        profile.put("evidence_refs", evidence);
        profile.put("revision", 1);
        profile.put("state", "PROFILE_CANDIDATE");
        profile.put("generated_at", Instant.now().toString());
        profile.put("learning_scope", "STATIC_REPOSITORY_UNDERSTANDING_CANDIDATE");
        profile.put("dynamic_trace_state", "NOT_RUN");
        profile.put("verification_state", "NOT_RUN");
        write(output, profile);
        return Map.copyOf(profile);
    }

    private List<Path> inventory(Path root) throws Exception {
        List<Path> files = new ArrayList<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !Files.isSymbolicLink(path))
                    .filter(path -> !excluded(root.relativize(path)))
                    .sorted(Comparator.comparing(path -> Hashing.relative(root, path)))
                    .forEach(files::add);
        }
        return List.copyOf(files);
    }

    private static boolean excluded(Path relative) {
        for (Path segment : relative) {
            if (Set.of(".git", ".onsure", "target", "build", "dist", "node_modules", "receipts")
                    .contains(segment.toString())) return true;
        }
        return false;
    }

    private List<Map<String, Object>> components(Path root, List<Path> files) throws Exception {
        Map<String, List<Path>> groups = new TreeMap<>();
        for (Path file : files) {
            Path relative = root.relativize(file);
            String key = relative.getNameCount() > 1 ? relative.getName(0).toString() : "ROOT";
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(file);
        }
        List<Map<String, Object>> values = new ArrayList<>();
        for (Map.Entry<String, List<Path>> entry : groups.entrySet()) {
            List<String> locations = entry.getValue().stream()
                    .limit(100).map(path -> Hashing.relative(root, path)).toList();
            values.add(evidenceBound(
                    "COMPONENT-" + Hashing.sha256(entry.getKey()).substring(0, 12),
                    entry.getKey(), componentKind(entry.getValue()), locations,
                    confidence(entry.getValue().size()), true));
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> dependencies(Path root, List<Path> files) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!Set.of("pom.xml", "build.gradle", "build.gradle.kts", "package.json",
                    "requirements.txt", "pyproject.toml", "go.mod", "cargo.toml")
                    .contains(name)) continue;
            String location = Hashing.relative(root, file);
            values.add(evidenceBound(
                    "DEPENDENCY-" + Hashing.sha256(location).substring(0, 12),
                    name, "BUILD_DEPENDENCY_MANIFEST", List.of(location), 0.98, true));
        }
        return List.copyOf(values);
    }

    private List<Map<String, Object>> aiComponents(Path root, List<Path> files) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            if (!isSmallText(file)) continue;
            String relative = Hashing.relative(root, file);
            String searchable = relative.toLowerCase(Locale.ROOT) + "\n"
                    + Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> candidate : Map.of(
                    "PROMPT", List.of("prompt", "system message", "instruction"),
                    "RAG", List.of("rag", "retrieval", "embedding", "vector"),
                    "AGENT", List.of("agent", "tool call", "function calling"),
                    "MODEL_PROVIDER", List.of("openai", "anthropic", "gemini", "ollama", "model_id"),
                    "MEMORY", List.of("conversation memory", "long term memory", "checkpoint"))
                    .entrySet()) {
                if (candidate.getValue().stream().anyMatch(searchable::contains)) {
                    String id = "AI-" + Hashing.sha256(candidate.getKey() + "|" + relative)
                            .substring(0, 12);
                    values.add(evidenceBound(id, candidate.getKey() + "@" + relative,
                            candidate.getKey(), List.of(relative), 0.70, false));
                }
            }
        }
        return deduplicate(values);
    }

    private List<Map<String, Object>> dataFlows(Path root, List<Path> files) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        for (Path file : files) {
            if (!isSmallText(file)) continue;
            String relative = Hashing.relative(root, file);
            String content = Files.readString(file, StandardCharsets.UTF_8).toLowerCase(Locale.ROOT);
            for (Map.Entry<String, List<String>> candidate : Map.of(
                    "DATABASE", List.of("jdbc:", "datasource", "postgres", "mysql", "mongodb"),
                    "HTTP_API", List.of("http://", "https://", "@restcontroller", "fetch("),
                    "EVENT", List.of("kafka", "rabbitmq", "eventbus", "publish("),
                    "FILESYSTEM", List.of("files.read", "files.write", "open(", "path.of("),
                    "MODEL_EGRESS", List.of("chat.completions", "messages.create", "generatecontent"))
                    .entrySet()) {
                if (candidate.getValue().stream().anyMatch(content::contains)) {
                    String id = "FLOW-" + Hashing.sha256(candidate.getKey() + "|" + relative)
                            .substring(0, 12);
                    values.add(evidenceBound(id, candidate.getKey() + "@" + relative,
                            candidate.getKey(), List.of(relative), 0.65, false));
                }
            }
        }
        return deduplicate(values);
    }

    private static List<Map<String, Object>> deduplicate(List<Map<String, Object>> values) {
        Map<String, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> value : values) unique.putIfAbsent(value.get("id").toString(), value);
        return List.copyOf(unique.values());
    }

    private static List<String> unknowns(
            Path root, List<Path> files, List<Map<String, Object>> components) {
        Set<String> names = new LinkedHashSet<>();
        files.forEach(path -> names.add(path.getFileName().toString().toLowerCase(Locale.ROOT)));
        List<String> values = new ArrayList<>();
        if (names.stream().noneMatch(name -> name.contains("test") || name.contains("spec"))) {
            values.add("TEST_ENTRYPOINT_NOT_DISCOVERED");
        }
        if (names.stream().noneMatch(name -> Set.of("pom.xml", "package.json", "pyproject.toml",
                "requirements.txt", "build.gradle", "go.mod", "cargo.toml").contains(name))) {
            values.add("BUILD_SYSTEM_NOT_DISCOVERED");
        }
        if (components.isEmpty()) values.add("COMPONENT_BOUNDARY_NOT_DISCOVERED");
        if (!Files.isRegularFile(root.resolve("README.md"))) values.add("PURPOSE_DOCUMENT_NOT_DISCOVERED");
        return List.copyOf(values);
    }

    private static List<String> conflicts(List<Path> files) {
        Set<String> buildSystems = new LinkedHashSet<>();
        for (Path file : files) {
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (Set.of("pom.xml", "build.gradle", "package.json", "pyproject.toml",
                    "requirements.txt", "go.mod", "cargo.toml").contains(name)) {
                buildSystems.add(name);
            }
        }
        return buildSystems.size() > 3
                ? List.of("MULTIPLE_BUILD_SYSTEMS_REQUIRE_REVIEW:" + String.join(",", buildSystems))
                : List.of();
    }

    private static String discoverPurpose(Path root, List<Path> files) throws Exception {
        Path readme = root.resolve("README.md");
        if (Files.isRegularFile(readme)) {
            for (String line : Files.readAllLines(readme, StandardCharsets.UTF_8)) {
                String value = line.replaceFirst("^#+\\s*", "").trim();
                if (!value.isBlank()) return value;
            }
        }
        return "Purpose not yet confirmed; repository contains " + files.size() + " inventoried files.";
    }

    private static String componentKind(List<Path> files) {
        Set<String> extensions = new LinkedHashSet<>();
        for (Path file : files) {
            String name = file.getFileName().toString();
            int index = name.lastIndexOf('.');
            if (index >= 0) extensions.add(name.substring(index + 1).toLowerCase(Locale.ROOT));
        }
        return extensions.isEmpty() ? "RESOURCE_GROUP" : "SOURCE_GROUP:" + String.join(",", extensions);
    }

    private static double confidence(int evidenceCount) {
        return Math.min(0.99, 0.55 + Math.log10(Math.max(1, evidenceCount)) / 5.0);
    }

    private static Map<String, Object> evidenceBound(
            String id, String name, String kind, List<String> sourceLocations,
            double confidence, boolean verified) {
        return Map.of(
                "id", id,
                "name", name,
                "kind", kind,
                "source_locations", sourceLocations,
                "confidence", confidence,
                "verified", verified);
    }

    private static boolean isSmallText(Path file) {
        try {
            if (Files.size(file) > MAX_TEXT_BYTES) return false;
            String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
            return name.endsWith(".java") || name.endsWith(".kt") || name.endsWith(".py")
                    || name.endsWith(".js") || name.endsWith(".ts") || name.endsWith(".json")
                    || name.endsWith(".yaml") || name.endsWith(".yml") || name.endsWith(".md")
                    || name.endsWith(".xml") || name.endsWith(".properties") || name.endsWith(".sh");
        } catch (Exception ignored) {
            return false;
        }
    }

    private SourceIdentity sourceIdentity(Path root, String sourceDigest) {
        try {
            Process process = new ProcessBuilder("git", "-C", root.toString(),
                    "rev-parse", "HEAD").redirectErrorStream(true).start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
            if (process.waitFor() == 0 && output.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
                return new SourceIdentity("GIT", "git:" + output, output, true);
            }
        } catch (Exception ignored) {
            // Archive source is represented by its content digest.
        }
        return new SourceIdentity("ARCHIVE", "sha256:" + sourceDigest, null, true);
    }

    private void write(Path output, Object value) throws Exception {
        Path normalized = output.toAbsolutePath().normalize();
        Files.createDirectories(normalized.getParent());
        Path temporary = normalized.resolveSibling(normalized.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException exception) {
            Files.move(temporary, normalized, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static void requireId(String value, String field) {
        if (value == null || !value.matches("[A-Za-z0-9._:-]{3,160}")) {
            throw new IllegalArgumentException(field);
        }
    }

    private record SourceIdentity(
            String sourceType, String sourceRef, String gitCommitSha, boolean clean) {}
}
