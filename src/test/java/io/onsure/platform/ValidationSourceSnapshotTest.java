package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationSourceSnapshotTest {
    @TempDir Path temp;

    @Test
    void copiesBoundedSourceAndExcludesGeneratedDependencyTrees() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.createDirectories(source.resolve("src/main"));
        Files.writeString(source.resolve("src/main/app.py"), "print('ok')\n");
        Files.createDirectories(source.resolve("node_modules/pkg"));
        Files.writeString(source.resolve("node_modules/pkg/generated.js"), "ignored");
        Files.createDirectories(source.resolve("target"));
        Files.writeString(source.resolve("target/output.jar"), "ignored");
        Files.createDirectories(source.resolve("vscode-extension/.vscode-test/runtime"));
        Files.writeString(source.resolve("vscode-extension/.vscode-test/runtime/package.json"), "{}");
        Files.createDirectories(source.resolve("contracts"));
        Files.writeString(source.resolve("contracts/target-adapter.v1.json"), "{}\n");

        var snapshot = ValidationSourceSnapshot.create(source, temp.resolve("execution-copy"));

        assertEquals(2, snapshot.fileCount());
        assertTrue(Files.isRegularFile(snapshot.snapshotRoot().resolve("src/main/app.py")));
        assertTrue(Files.isRegularFile(
                snapshot.snapshotRoot().resolve("contracts/target-adapter.v1.json")));
        assertFalse(Files.exists(snapshot.snapshotRoot().resolve("node_modules")));
        assertFalse(Files.exists(snapshot.snapshotRoot().resolve("vscode-extension/.vscode-test")));
        assertEquals(snapshot.sourceDigestBefore(), snapshot.snapshotDigest());
        assertTrue(ValidationSourceSnapshot.sourceUnchanged(snapshot));
    }

    @Test
    void detectsSourceMutationAfterSnapshot() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Path file = source.resolve("app.py");
        Files.writeString(file, "before");
        var snapshot = ValidationSourceSnapshot.create(source, temp.resolve("copy"));
        Files.writeString(file, "after");
        assertFalse(ValidationSourceSnapshot.sourceUnchanged(snapshot));
    }

    @Test
    void rejectsSymlinksLimitsAndOverlappingDestinations() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("one"), "1");
        Files.writeString(source.resolve("two"), "2");
        Files.createSymbolicLink(source.resolve("escape"), Path.of("/etc/passwd"));
        assertThrows(IllegalArgumentException.class,
                () -> ValidationSourceSnapshot.create(source, temp.resolve("copy")));
        Files.delete(source.resolve("escape"));
        assertThrows(IllegalArgumentException.class,
                () -> ValidationSourceSnapshot.create(source, temp.resolve("limited"), 1, 100));
        assertThrows(IllegalArgumentException.class,
                () -> ValidationSourceSnapshot.create(source, source.resolve("nested")));
    }

    @Test
    void excludesWorktreeGitPointerFileFromExecutionSnapshot() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve(".git"), "gitdir: /outside/worktree/gitdir\n");
        Files.writeString(source.resolve("program.txt"), "safe");

        var snapshot = ValidationSourceSnapshot.create(source, temp.resolve("snapshot"));

        assertFalse(Files.exists(snapshot.snapshotRoot().resolve(".git")));
        assertTrue(Files.isRegularFile(snapshot.snapshotRoot().resolve("program.txt")));
        assertEquals(1, snapshot.fileCount());
    }
}
