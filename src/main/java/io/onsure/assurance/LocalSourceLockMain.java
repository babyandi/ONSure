package io.onsure.assurance;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.LinkedHashMap;
import java.util.Map;

public final class LocalSourceLockMain {
    private LocalSourceLockMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: LocalSourceLockMain <repository-root> <output-file>");
            System.exit(64);
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path output = Path.of(args[1]).toAbsolutePath().normalize();
        String commit = LocalSourceLockVerifier.currentCommit(root);
        if (!commit.matches("[0-9a-f]{40}|[0-9a-f]{64}")) {
            System.err.println("invalid git commit");
            System.exit(71);
        }
        if (!LocalSourceLockVerifier.isTrackedWorktreeClean(root)) {
            System.err.println("worktree is dirty or contains untracked files");
            System.exit(72);
        }
        Map<String, Object> lock = new LinkedHashMap<>();
        lock.put("digest_contract", LocalSourceLockVerifier.DIGEST_CONTRACT);
        lock.put("commit_sha", commit);
        lock.put("tree_sha256", LocalSourceLockVerifier.digestTrackedFiles(root));
        lock.put("policy_sha256", LocalSourceLockVerifier.digestPolicyFiles(root));
        lock.put("source_scope", "GIT_TRACKED_FILES_ONLY");
        lock.put("policy_scope", "GIT_TRACKED_POLICY_FILES_ONLY");
        lock.put("untracked_files_blocked", true);
        lock.put("worktree_clean", true);
        Files.createDirectories(output.getParent());
        Path temporary = output.resolveSibling(output.getFileName() + ".tmp");
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(temporary.toFile(), lock);
        try {
            Files.move(temporary, output, StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
