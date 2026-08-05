package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NestedProjectValidationPackTest {
    @TempDir Path temp;

    @Test
    void detectsIndependentNestedMavenGradlePythonAndNodeRoots() throws Exception {
        Path java = Files.createDirectories(temp.resolve("java"));
        Files.writeString(java.resolve("pom.xml"), "<project/>");
        Path gradle = Files.createDirectories(temp.resolve("services/worker"));
        Files.writeString(gradle.resolve("build.gradle.kts"), "plugins { java }");
        Files.writeString(gradle.resolve("gradlew"), "#!/bin/sh\n");
        gradle.resolve("gradlew").toFile().setExecutable(true);
        Path python = Files.createDirectories(temp.resolve("tools/checker/tests"));
        Files.writeString(python.getParent().resolve("pyproject.toml"), "[project]\nname='checker'\n");
        Path node = Files.createDirectories(temp.resolve("web"));
        Files.writeString(node.resolve("package.json"), """
                {"scripts":{"test":"node --test","build":"tsc"},"devDependencies":{"typescript":"1"}}
                """);
        Files.writeString(node.resolve("package-lock.json"), "{}");

        var profile = new StandardValidationProfileDetector(List.of()).detect("nested-products", temp);

        assertTrue(profile.technologies().containsAll(
                Set.of("NESTED_PROJECTS", "MAVEN", "GRADLE", "PYTHON", "NODE")));
        assertTrue(hasCommandAt(profile, "java", List.of("mvn", "-B", "-ntp", "-o", "clean", "verify")));
        assertTrue(hasCommandAt(profile, "services/worker",
                List.of("bash", "gradlew", "--offline", "clean", "test")));
        assertTrue(hasCommandAt(profile, "tools/checker",
                List.of("python3", "-m", "compileall", "-q", ".")));
        assertTrue(hasCommandAt(profile, "tools/checker",
                List.of("python3", "-m", "unittest", "discover", "-s", "tests")));
        assertTrue(hasCommandAt(profile, "web", List.of("npm", "--offline", "test")));
        assertTrue(hasCommandAt(profile, "web", List.of("npm", "--offline", "run", "build")));
        assertTrue(profile.environmentRequirements().stream().anyMatch(requirement ->
                requirement.value().equals("web/package-lock.json") && requirement.required()));
        assertTrue(profile.environmentRequirements().stream().anyMatch(requirement ->
                requirement.value().equals("services/worker/gradlew") && requirement.required()));
    }

    @Test
    void selectsPytestOnlyWhenNestedProjectDeclaresIt() throws Exception {
        Path python = Files.createDirectories(temp.resolve("tools/checker/tests"));
        Files.writeString(python.getParent().resolve("pyproject.toml"),
                "[project]\nname='checker'\n[tool.pytest.ini_options]\n");

        var profile = new StandardValidationProfileDetector(List.of()).detect("nested-pytest", temp);

        assertTrue(hasCommandAt(profile, "tools/checker",
                List.of("python3", "-m", "pytest", "-q")));
        assertFalse(hasCommandAt(profile, "tools/checker",
                List.of("python3", "-m", "unittest", "discover", "-s", "tests")));
    }

    @Test
    void detectsOnGuardStyleJavaPomWithoutTreatingItAsExecuted() throws Exception {
        Path javaSource = Files.createDirectories(temp.resolve("java/src/main/java/example"));
        Files.writeString(temp.resolve("java/pom.xml"), "<project/>");

        var profile = new StandardValidationProfileDetector(List.of()).detect("onguard-read-only", temp);
        var step = profile.steps().stream().filter(value ->
                value.workingDirectory().equals(Path.of("java")) && value.command().get(0).equals("mvn"))
                .findFirst().orElseThrow();

        assertEquals(UniversalValidationProfile.Outcome.NOT_RUN,
                profile.phaseOutcomes(java.util.Map.of()).get(
                        UniversalValidationProfile.Phase.COMPONENT_AND_NEGATIVE));
        assertEquals(List.of("validator.meta-check", "environment.preflight"), step.dependsOn());
        assertFalse(Files.exists(temp.resolve(".onsure")));
        assertTrue(Files.isDirectory(javaSource));
    }

    @Test
    void suppressesNestedMavenModulesOwnedByAnAncestorReactor() throws Exception {
        Files.writeString(temp.resolve("pom.xml"),
                "<project><modules><module>modules/core</module></modules></project>");
        Path module = Files.createDirectories(temp.resolve("modules/core"));
        Files.writeString(module.resolve("pom.xml"), "<project/>");

        var profile = new StandardValidationProfileDetector(List.of()).detect("reactor", temp);

        assertEquals(1, profile.steps().stream()
                .filter(step -> !step.command().isEmpty() && step.command().get(0).equals("mvn")).count());
        assertFalse(profile.steps().stream().anyMatch(step -> step.workingDirectory().equals(Path.of("modules/core"))));
    }

    @Test
    void retainsIndependentNestedMavenRootNotDeclaredByAncestor() throws Exception {
        Files.writeString(temp.resolve("pom.xml"), "<project/>");
        Path independent = Files.createDirectories(temp.resolve("java"));
        Files.writeString(independent.resolve("pom.xml"), "<project/>");

        var profile = new StandardValidationProfileDetector(List.of()).detect("independent-nested", temp);

        assertTrue(profile.steps().stream().anyMatch(step ->
                step.workingDirectory().equals(Path.of("java"))
                        && step.command().equals(List.of("mvn", "-B", "-ntp", "-o", "clean", "verify"))));
    }

    @Test
    void skipsSymlinkedTreesAndFailsClosedOnInvalidNestedNodeManifest() throws Exception {
        Path outside = Files.createDirectory(temp.resolve("outside"));
        Files.writeString(outside.resolve("pom.xml"), "<project/>");
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.createSymbolicLink(source.resolve("linked"), outside);
        var safe = new StandardValidationProfileDetector(List.of()).detect("symlink-safe", source);
        assertFalse(safe.technologies().contains("NESTED_PROJECTS"));

        Path node = Files.createDirectory(source.resolve("web"));
        Files.writeString(node.resolve("package.json"), "[]");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new StandardValidationProfileDetector(List.of()).detect("invalid-node", source));
        assertEquals("NESTED_NODE_MANIFEST_INVALID", error.getMessage());
    }

    @Test
    void failsClosedOnOversizedNestedBuildConfiguration() throws Exception {
        Path javaProject = Files.createDirectory(temp.resolve("java"));
        Path pom = javaProject.resolve("pom.xml");
        try (var channel = Files.newByteChannel(pom,
                java.nio.file.StandardOpenOption.CREATE_NEW, java.nio.file.StandardOpenOption.WRITE)) {
            channel.position(5L * 1024 * 1024);
            channel.write(java.nio.ByteBuffer.wrap(new byte[] {'>'}));
        }

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new StandardValidationProfileDetector(List.of()).detect("oversized-nested", temp));
        assertTrue(error.getMessage().startsWith("VALIDATION_CONFIG_INVALID_OR_TOO_LARGE"));
    }

    private static boolean hasCommandAt(UniversalValidationProfile.Profile profile,
            String relative, List<String> command) {
        return profile.steps().stream().anyMatch(step ->
                step.workingDirectory().equals(Path.of(relative)) && step.command().equals(command));
    }
}
