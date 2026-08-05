package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
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
        @SuppressWarnings("unchecked")
        Map<String, Object> workflow = (Map<String, Object>) profile.get("workflow_inventory");
        assertEquals("ONSURE_STATIC_WORKFLOW_INVENTORY_V1", workflow.get("contract"));
        assertEquals(false, workflow.get("auto_execute"));
        @SuppressWarnings("unchecked")
        Map<String, Object> understanding = (Map<String, Object>) profile.get("program_understanding");
        assertEquals("ONSURE_PROGRAM_UNDERSTANDING_CANDIDATE_V1", understanding.get("contract"));
        assertEquals(false, understanding.get("inferences_are_pass_evidence"));
        assertEquals("NOT_RUN_REVIEW_REQUIRED", understanding.get("automatic_execution"));
        @SuppressWarnings("unchecked") Map<String, Object> semantics =
                (Map<String, Object>) profile.get("business_semantic_hypotheses");
        assertEquals(BusinessSemanticHypothesisEngine.CONTRACT, semantics.get("contract"));
        assertEquals(false, semantics.get("score_eligible"));
        assertEquals("NOT_RUN_REVIEW_REQUIRED", semantics.get("automatic_execution"));
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

    private static void git(Path root, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git"); command.addAll(java.util.List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }
}
