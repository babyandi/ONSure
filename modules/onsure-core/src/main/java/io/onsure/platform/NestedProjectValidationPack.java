package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.platform.UniversalValidationProfile.EnvironmentRequirement;
import io.onsure.platform.UniversalValidationProfile.Phase;
import io.onsure.platform.UniversalValidationProfile.RequirementKind;
import io.onsure.platform.UniversalValidationProfile.Step;
import io.onsure.platform.UniversalValidationProfile.StepKind;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Bounded discovery of independent build roots below the registered target root. */
public final class NestedProjectValidationPack implements ValidationPack {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_ENTRIES = 50_000;
    private static final int MAX_PROJECT_ROOTS = 128;
    private final ObjectMapper mapper = new ObjectMapper();

    private enum Ecosystem { MAVEN, GRADLE, PYTHON, NODE }

    @Override public String id() { return "nested"; }

    @Override
    public Contribution detect(Path root) throws Exception {
        Map<Path, Set<Ecosystem>> discovered = discover(root);
        Set<String> technologies = new LinkedHashSet<>();
        List<EnvironmentRequirement> requirements = new ArrayList<>();
        List<Step> steps = new ArrayList<>();
        for (Map.Entry<Path, Set<Ecosystem>> entry : discovered.entrySet()) {
            Path relative = root.relativize(entry.getKey());
            for (Ecosystem ecosystem : entry.getValue().stream().sorted().toList()) {
                if (hasOwningAncestor(root, entry.getKey(), ecosystem)) continue;
                String token = ecosystem.name().toLowerCase(java.util.Locale.ROOT) + "-"
                        + Hashing.sha256(relative.toString().replace('\\', '/') + "\u0000" + ecosystem)
                                .substring(0, 12);
                technologies.add("NESTED_PROJECTS");
                technologies.add(switch (ecosystem) {
                    case MAVEN -> "MAVEN"; case GRADLE -> "GRADLE";
                    case PYTHON -> "PYTHON"; case NODE -> "NODE";
                });
                switch (ecosystem) {
                    case MAVEN -> {
                        StandardValidationPackSupport.readConfig(entry.getKey().resolve("pom.xml"));
                        steps.add(step(token + ".clean-verify", StepKind.BUILD,
                                List.of("mvn", "-B", "-ntp", "-o", "clean", "verify"), relative,
                                StandardValidationPackSupport.BUILD_TIMEOUT, List.of("validator.meta-check")));
                    }
                    case GRADLE -> {
                        Path build = safeFile(entry.getKey().resolve("build.gradle"))
                                ? entry.getKey().resolve("build.gradle")
                                : entry.getKey().resolve("build.gradle.kts");
                        StandardValidationPackSupport.readConfig(build);
                        requirements.add(new EnvironmentRequirement("nested." + token + ".wrapper",
                                RequirementKind.EXECUTABLE_SOURCE_FILE,
                                relative.resolve("gradlew").toString().replace('\\', '/'), true));
                        steps.add(step(token + ".clean-test", StepKind.BUILD,
                                List.of("bash", "gradlew", "--offline", "clean", "test"), relative,
                                StandardValidationPackSupport.BUILD_TIMEOUT, List.of("validator.meta-check")));
                    }
                    case PYTHON -> addPython(entry.getKey(), relative, token, steps);
                    case NODE -> addNode(entry.getKey(), relative, token, requirements, steps);
                }
            }
        }
        return new Contribution(technologies, requirements, steps);
    }

    private Map<Path, Set<Ecosystem>> discover(Path root) throws IOException {
        Map<Path, Set<Ecosystem>> found = new java.util.TreeMap<>(Comparator.comparing(path ->
                root.relativize(path).toString().replace('\\', '/')));
        int[] entries = {0};
        Files.walkFileTree(root, Set.of(), MAX_DEPTH, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) {
                if (++entries[0] > MAX_ENTRIES)
                    throw new IllegalArgumentException("NESTED_PROJECT_DETECTION_ENTRY_LIMIT_EXCEEDED");
                if (!directory.equals(root) && (Files.isSymbolicLink(directory)
                        || GeneratedPathPolicy.excludes(directory.getFileName().toString())))
                    return FileVisitResult.SKIP_SUBTREE;
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                if (++entries[0] > MAX_ENTRIES)
                    throw new IllegalArgumentException("NESTED_PROJECT_DETECTION_ENTRY_LIMIT_EXCEEDED");
                if (!attributes.isRegularFile() || Files.isSymbolicLink(file)) return FileVisitResult.CONTINUE;
                Path parent = file.getParent();
                if (parent == null || parent.equals(root)) return FileVisitResult.CONTINUE;
                String name = file.getFileName().toString();
                Ecosystem ecosystem = switch (name) {
                    case "pom.xml" -> Ecosystem.MAVEN;
                    case "build.gradle", "build.gradle.kts" -> Ecosystem.GRADLE;
                    case "pyproject.toml", "pytest.ini", "requirements.txt" -> Ecosystem.PYTHON;
                    case "package.json" -> Ecosystem.NODE;
                    default -> null;
                };
                if (ecosystem != null) {
                    found.computeIfAbsent(parent, ignored -> new LinkedHashSet<>()).add(ecosystem);
                    if (found.size() > MAX_PROJECT_ROOTS)
                        throw new IllegalArgumentException("NESTED_PROJECT_ROOT_LIMIT_EXCEEDED");
                }
                return FileVisitResult.CONTINUE;
            }
        });
        return Collections.unmodifiableMap(new java.util.LinkedHashMap<>(found));
    }

    private static boolean hasOwningAncestor(Path root, Path project, Ecosystem ecosystem) {
        if (ecosystem != Ecosystem.MAVEN) return false;
        Path ancestor = project.getParent();
        while (ancestor != null && ancestor.startsWith(root)) {
            Path pom = ancestor.resolve("pom.xml");
            if (safeFile(pom)) {
                try {
                    String relative = ancestor.relativize(project).toString().replace('\\', '/');
                    String expression = "(?s).*<module>\\s*"
                            + java.util.regex.Pattern.quote(relative) + "\\s*</module>.*";
                    if (StandardValidationPackSupport.readConfig(pom).matches(expression)) return true;
                } catch (Exception error) {
                    throw new IllegalArgumentException("NESTED_MAVEN_REACTOR_DETECTION_FAILED", error);
                }
            }
            if (ancestor.equals(root)) break;
            ancestor = ancestor.getParent();
        }
        return false;
    }

    private static boolean safeFile(Path file) {
        return Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(file);
    }

    private static void addPython(Path project, Path relative, String token, List<Step> steps) {
        steps.add(step(token + ".compile", StepKind.BUILD,
                List.of("python3", "-m", "compileall", "-q", "."), relative,
                StandardValidationPackSupport.TEST_TIMEOUT, List.of("validator.meta-check")));
        if (Files.isDirectory(project.resolve("tests"), LinkOption.NOFOLLOW_LINKS)
                && !Files.isSymbolicLink(project.resolve("tests"))) {
            boolean pytest = safeFile(project.resolve("pytest.ini"))
                    || StandardValidationPackSupport.contains(project.resolve("pyproject.toml"), "pytest")
                    || StandardValidationPackSupport.contains(project.resolve("requirements.txt"), "pytest");
            List<String> command = pytest
                    ? List.of("python3", "-m", "pytest", "-q")
                    : List.of("python3", "-m", "unittest", "discover", "-s", "tests");
            steps.add(step(token + ".tests", StepKind.UNIT_TEST,
                    command, relative,
                    StandardValidationPackSupport.TEST_TIMEOUT, List.of("nested." + token + ".compile")));
        }
    }

    private void addNode(Path project, Path relative, String token,
            List<EnvironmentRequirement> requirements, List<Step> steps) throws Exception {
        JsonNode body = mapper.readTree(StandardValidationPackSupport.readConfig(project.resolve("package.json")));
        if (body == null || !body.isObject()) throw new IllegalArgumentException("NESTED_NODE_MANIFEST_INVALID");
        JsonNode scripts = body.path("scripts");
        boolean dependencies = body.path("dependencies").size() > 0
                || body.path("devDependencies").size() > 0 || body.path("optionalDependencies").size() > 0;
        String gate = "validator.meta-check";
        if (dependencies) {
            String requirementId = "nested." + token + ".lockfile";
            requirements.add(new EnvironmentRequirement(requirementId, RequirementKind.SOURCE_FILE,
                    relative.resolve("package-lock.json").toString().replace('\\', '/'), true));
            String stepId = token + ".dependencies";
            steps.add(step(stepId, StepKind.BUILD,
                    List.of("npm", "--offline", "ci", "--ignore-scripts", "--engine-strict"), relative,
                    StandardValidationPackSupport.BUILD_TIMEOUT, List.of("validator.meta-check")));
            gate = "nested." + stepId;
        }
        if (scripts.hasNonNull("test")) steps.add(step(token + ".tests", StepKind.UNIT_TEST,
                List.of("npm", "--offline", "test"), relative,
                StandardValidationPackSupport.TEST_TIMEOUT, List.of(gate)));
        if (scripts.hasNonNull("build")) steps.add(step(token + ".build", StepKind.BUILD,
                List.of("npm", "--offline", "run", "build"), relative,
                StandardValidationPackSupport.BUILD_TIMEOUT, List.of(gate)));
    }

    private static Step step(String suffix, StepKind kind, List<String> command, Path workingDirectory,
            Duration timeout, List<String> dependencies) {
        return new Step("nested." + suffix, Phase.COMPONENT_AND_NEGATIVE, kind, true,
                command, workingDirectory, timeout, dependencies);
    }
}
