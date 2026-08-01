package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramLearningServiceTest {
    @TempDir Path temp;

    @Test
    void nonGitDirectoryUsesTreeReferenceWithoutInventedCommit() throws Exception {
        Path source = temp.resolve("archive-source");
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("README.md"), "# Archive Sample\n");
        Files.writeString(source.resolve("src/main.py"), "print('hello')\n");
        Map<String, Object> profile = new ProgramLearningService().learn(
                source, "project-archive", "program-archive", temp.resolve("archive-profile.json"));
        @SuppressWarnings("unchecked")
        Map<String, Object> baseline = (Map<String, Object>) profile.get("source_baseline");
        assertEquals("SOURCE_TREE_SHA256", baseline.get("reference_type"));
        assertEquals(baseline.get("source_tree_sha256"), baseline.get("reference_value"));
        assertNull(baseline.get("git_commit_sha"));
        assertNull(baseline.get("worktree_clean"));
        assertEquals(2, ((Number) baseline.get("source_file_count")).intValue());
        assertEquals("STATIC_REPOSITORY_UNDERSTANDING_V2", profile.get("learning_method"));
        assertEquals("NOT_RUN", profile.get("dynamic_trace"));
        assertNull(profile.get("parent_profile"));
        @SuppressWarnings("unchecked")
        Map<String, Object> changeSet = (Map<String, Object>) profile.get("change_set");
        assertEquals("FULL", changeSet.get("mode"));
        assertEquals(List.of("README.md", "src/main.py"), changeSet.get("added"));
    }

    @Test
    void gitDirectoryUsesCommitAndCanonicalTrackedSet() throws Exception {
        Path source = temp.resolve("git-source");
        Files.createDirectories(source.resolve("src"));
        git(source, "init");
        git(source, "config", "user.email", "test@example.invalid");
        git(source, "config", "user.name", "ONSure Test");
        Files.writeString(source.resolve("README.md"), "# Git Sample\n");
        Files.writeString(source.resolve("src/App.java"), "class App {}\n");
        git(source, "add", ".");
        git(source, "commit", "-m", "baseline");
        Files.writeString(source.resolve("untracked.txt"), "ignored but worktree dirty\n");
        Map<String, Object> profile = new ProgramLearningService().learn(
                source, "project-git", "program-git", temp.resolve("git-profile.json"));
        @SuppressWarnings("unchecked")
        Map<String, Object> baseline = (Map<String, Object>) profile.get("source_baseline");
        assertEquals("GIT_COMMIT", baseline.get("reference_type"));
        assertTrue(baseline.get("git_commit_sha").toString().matches("[0-9a-f]{40,64}"));
        assertEquals(false, baseline.get("worktree_clean"));
        assertEquals(2, ((Number) baseline.get("source_file_count")).intValue());
        assertEquals(Hashing.tree(source), baseline.get("source_tree_sha256"));
    }

    @Test
    void repeatedLearningBindsParentAndReportsAddedModifiedDeletedAndUnchanged() throws Exception {
        Path source = temp.resolve("incremental-source");
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("README.md"), "# Incremental Sample\n");
        Files.writeString(source.resolve("src/main.py"), "print('v1')\n");
        Files.writeString(source.resolve("src/stable.py"), "STABLE = True\n");
        Path output = temp.resolve("program-profile.json");
        Map<String, Object> first = new ProgramLearningService().learn(
                source, "project-incremental", "program-incremental", output);

        Files.delete(source.resolve("README.md"));
        Files.writeString(source.resolve("src/main.py"), "print('v2')\n");
        Files.writeString(source.resolve("src/added.py"), "ADDED = True\n");
        Map<String, Object> second = new ProgramLearningService().learn(
                source, "project-incremental", "program-incremental", output);

        assertEquals(2, second.get("revision"));
        @SuppressWarnings("unchecked")
        Map<String, Object> parent = (Map<String, Object>) second.get("parent_profile");
        assertEquals(first.get("profile_id"), parent.get("profile_id"));
        assertEquals(first.get("profile_sha256"), parent.get("profile_sha256"));
        assertEquals(1, parent.get("revision"));
        @SuppressWarnings("unchecked")
        Map<String, Object> changeSet = (Map<String, Object>) second.get("change_set");
        assertEquals("INCREMENTAL", changeSet.get("mode"));
        assertEquals(List.of("src/added.py"), changeSet.get("added"));
        assertEquals(List.of("src/main.py"), changeSet.get("modified"));
        assertEquals(List.of("README.md"), changeSet.get("deleted"));
        assertEquals(List.of("src/stable.py"), changeSet.get("unchanged"));
        assertEquals(3, changeSet.get("compared_file_count"));
    }

    @Test
    void tamperedParentProfileFailsClosed() throws Exception {
        Path source = temp.resolve("tampered-parent-source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("main.py"), "print('safe')\n");
        Path output = temp.resolve("tampered-profile.json");
        new ProgramLearningService().learn(
                source, "project-tamper", "program-tamper", output);
        Files.writeString(output, Files.readString(output).replace(
                "PROFILE_CANDIDATE", "PROFILE_REVIEWED"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new ProgramLearningService().learn(
                        source, "project-tamper", "program-tamper", output));
        assertEquals("PARENT_PROGRAM_PROFILE_HASH_INVALID", failure.getMessage());
    }

    private static void git(Path root, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git"); command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }
}
