package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    void authorityPathsAreCanonicalAndPhysicallyOutsideWorkspace() {
        Path workspace = temp.resolve("workspace");
        Path authorityBase = temp.resolve("authority-base");
        ApprovalAuthorityPaths paths = ApprovalAuthorityPaths.forWorkspace(workspace, authorityBase);
        assertTrue(paths.authorityRoot().startsWith(authorityBase.toAbsolutePath().normalize()));
        assertFalse(paths.authorityRoot().startsWith(workspace.toAbsolutePath().normalize()));
        assertFalse(workspace.toAbsolutePath().normalize().startsWith(paths.authorityRoot()));
        assertEquals("trusted-key-registry.json", paths.trustedKeyRegistry().getFileName().toString());
        assertEquals("approval-replay-ledger.jsonl", paths.replayLedger().getFileName().toString());
    }

    @Test
    void authorityBaseInsideWorkspaceIsRejected() {
        Path workspace = temp.resolve("workspace");
        assertThrows(IllegalArgumentException.class,
                () -> ApprovalAuthorityPaths.forWorkspace(workspace, workspace.resolve(".onsure-authority")));
    }

    @Test
    void everyWorkflowRejectsCallerSelectedAuthorityPaths() throws Exception {
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(workspace);
        String previous = System.getProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
        System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY,
                temp.resolve("authority-base").toString());
        try {
            LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(workspace);
            for (String field : new String[] {
                    "trusted_key_registry", "approval_key_registry",
                    "approval_replay_ledger", "verification_replay_ledger",
                    "approval_authority_root", "approval_authority_base"}) {
                IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                        () -> dispatcher.dispatch("program.learn", mapper.valueToTree(Map.of(
                                field, workspace.resolve("attacker-" + field).toString()))));
                assertEquals("APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED:" + field,
                        failure.getMessage());
            }
        } finally {
            if (previous == null) System.clearProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
            else System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY, previous);
        }
    }

    @Test
    void symlinkedAuthorityRegistryIsRejected() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path authorityBase = temp.resolve("authority-base");
        ApprovalAuthorityPaths paths = ApprovalAuthorityPaths.forWorkspace(workspace, authorityBase);
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
