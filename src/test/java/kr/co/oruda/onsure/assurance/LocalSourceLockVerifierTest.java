package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSourceLockVerifierTest {
    @TempDir Path temp;

    @Test
    void acceptsImmutableCleanSourceLock() throws Exception {
        Path lock = temp.resolve("source-lock.json");
        Files.writeString(lock, "{\"digest_contract\":\"ONSURE_SOURCE_DIGEST_V1\",\"commit_sha\":\"" + "a".repeat(40)
                + "\",\"tree_sha256\":\"" + "b".repeat(64)
                + "\",\"policy_sha256\":\"" + "c".repeat(64)
                + "\",\"worktree_clean\":true}");
        assertEquals(Decision.PASS, new LocalSourceLockVerifier().verify(lock).decision());
    }

    @Test
    void rejectsMutableDirtyOrUnversionedSourceLock() throws Exception {
        Path lock = temp.resolve("source-lock.json");
        Files.writeString(lock, "{\"commit_sha\":\"main\",\"tree_sha256\":\"bad\","
                + "\"policy_sha256\":\"bad\",\"worktree_clean\":false}");
        ValidationResult result = new LocalSourceLockVerifier().verify(lock);
        assertTrue(result.violations().contains("SOURCE_DIGEST_CONTRACT_MISMATCH"));
        assertTrue(result.violations().contains("INVALID_SOURCE_COMMIT"));
        assertTrue(result.violations().contains("DIRTY_SOURCE_WORKTREE"));
    }

    @Test
    void explicitRepositoryVerificationRequiresRepositoryRoot() throws Exception {
        Path lock = temp.resolve("source-lock.json");
        Files.writeString(lock, "{\"digest_contract\":\"ONSURE_SOURCE_DIGEST_V1\",\"commit_sha\":\"" + "a".repeat(40)
                + "\",\"tree_sha256\":\"" + "b".repeat(64)
                + "\",\"policy_sha256\":\"" + "c".repeat(64)
                + "\",\"worktree_clean\":true}");
        ValidationResult result = new LocalSourceLockVerifier().verifyAgainstRepository(lock, null);
        assertTrue(result.violations().contains("SOURCE_REPOSITORY_ROOT_MISSING"));
    }

    @Test
    void digestBindsCanonicalPathAndContentBoundaries() throws Exception {
        Path first = temp.resolve("ab");
        Path second = temp.resolve("a");
        Files.writeString(first, "c");
        Files.writeString(second, "bc");
        String separate = LocalSourceLockVerifier.digestFileList(temp, List.of(first, second));
        String reversed = LocalSourceLockVerifier.digestFileList(temp, List.of(second, first));
        assertNotEquals(separate, reversed);

        Files.writeString(first, "changed");
        assertNotEquals(separate, LocalSourceLockVerifier.digestFileList(temp, List.of(first, second)));
    }
}
