package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HashingSourceSetTest {
    @TempDir Path temp;

    @Test
    void gitSubdirectoryUsesOnlyTrackedFilesInsideThatSubdirectory() throws Exception {
        Path repository = temp.resolve("repository");
        Path app = repository.resolve("apps/demo");
        Files.createDirectories(app);
        Files.createDirectories(repository.resolve("other"));
        git(repository, "init");
        git(repository, "config", "user.email", "test@example.invalid");
        git(repository, "config", "user.name", "ONSure Test");
        Files.writeString(app.resolve("a.txt"), "A\n");
        Files.writeString(repository.resolve("other/b.txt"), "B\n");
        git(repository, "add", ".");
        git(repository, "commit", "-m", "baseline");

        List<Path> files = Hashing.sourceFiles(app);
        assertEquals(List.of(app.resolve("a.txt").toAbsolutePath().normalize()), files);
        String baseline = Hashing.tree(app);
        Files.writeString(app.resolve("untracked.txt"), "untracked\n");
        assertEquals(baseline, Hashing.tree(app));
        Files.writeString(app.resolve("a.txt"), "changed\n");
        assertNotEquals(baseline, Hashing.tree(app));
    }

    private static void git(Path root, String... arguments) throws Exception {
        java.util.ArrayList<String> command = new java.util.ArrayList<>();
        command.add("git"); command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }
}