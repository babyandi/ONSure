package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ApprovalAuthorityPathsTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void authorityPathsAreCanonicalAndInsideWorkspace() throws Exception {
        ApprovalAuthorityPaths paths = ApprovalAuthorityPaths.forWorkspace(temp);
        assertTrue(paths.authorityRoot().startsWith(temp.toAbsolutePath().normalize()));
        assertEquals("trusted-key-registry.json", paths.trustedKeyRegistry().getFileName().toString());
        assertEquals("approval-replay-ledger.jsonl", paths.replayLedger().getFileName().toString());
    }

    @Test
    void everyWorkflowRejectsCallerSelectedKeyRegistryOrReplayLedger() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        for (String field : new String[] {
                "trusted_key_registry", "approval_key_registry",
                "approval_replay_ledger", "verification_replay_ledger"}) {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> dispatcher.dispatch("program.learn", mapper.valueToTree(Map.of(
                            field, temp.resolve("attacker-" + field).toString()))));
            assertEquals("APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED:" + field,
                    failure.getMessage());
        }
    }

    @Test
    void symlinkedAuthorityRegistryIsRejected() throws Exception {
        ApprovalAuthorityPaths paths = ApprovalAuthorityPaths.forWorkspace(temp);
        Files.createDirectories(paths.authorityRoot());
        Path target = temp.resolve("attacker-registry.json");
        Files.writeString(target, "{}\n");
        try {
            Files.createSymbolicLink(paths.trustedKeyRegistry(), target);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            return;
        }
        assertThrows(IllegalArgumentException.class, paths::requireTrustedKeyRegistry);
    }
}
