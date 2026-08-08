package kr.co.oruda.onsure.platform;

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
        assertFalse(paths.replayExternalAnchor().startsWith(paths.authorityRoot()));
        assertTrue(paths.replayExternalAnchor().startsWith(authorityBase.toAbsolutePath().normalize()));
    }

    @Test
    void authorityBaseInsideWorkspaceIsRejected() {
        Path workspace = temp.resolve("workspace");
        assertThrows(IllegalArgumentException.class,
                () -> ApprovalAuthorityPaths.forWorkspace(workspace, workspace.resolve(".onsure-authority")));
    }

    @Test
    void containedWorktreeDiscoversExactlyOneFixedWorkspaceAuthority() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path worktree = workspace.resolve(".onsure/worktrees/approved-patch");
        Files.createDirectories(worktree);
        String previous = System.getProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
        System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY,
                temp.resolve("authority-base").toString());
        try {
            ApprovalAuthorityPaths expected = ApprovalAuthorityPaths.forWorkspace(workspace);
            Files.createDirectories(expected.authorityRoot());
            Files.writeString(expected.trustedKeyRegistry(), "{}\n");
            Files.writeString(expected.replayLedger(), "\n");
            assertEquals(expected, ApprovalAuthorityPaths.discoverForContainedPath(worktree));
        } finally {
            if (previous == null) System.clearProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
            else System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY, previous);
        }
    }

    @Test
    void containedPathWithoutAuthorityIsRejected() throws Exception {
        Path worktree = temp.resolve("workspace/.onsure/worktrees/missing-authority");
        Files.createDirectories(worktree);
        String previous = System.getProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
        System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY,
                temp.resolve("authority-base").toString());
        try {
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> ApprovalAuthorityPaths.discoverForContainedPath(worktree));
            assertEquals("APPROVAL_AUTHORITY_NOT_DISCOVERABLE_FROM_PATH", failure.getMessage());
        } finally {
            if (previous == null) System.clearProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
            else System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY, previous);
        }
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
                    "approval_replay_external_anchor",
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
    void productOwnedStateAndOutputPathsCannotBeForkedOrPointAtSourceFiles() throws Exception {
        Path workspace = temp.resolve("workspace");
        Files.createDirectories(workspace);
        String previous = System.getProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
        System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY,
                temp.resolve("authority-base").toString());
        try {
            LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(workspace);
            for (String field : new String[] {
                    "catalog_root", "store_root", "license_store_root", "case_store_root",
                    "memory_root", "project_memory_root", "reusable_pattern_root",
                    "jobs_root", "checkpoint_root", "queue_root",
                    "output_file", "approved_plan_file", "evidence_root", "rollback_receipt_file"}) {
                IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                        () -> dispatcher.dispatch("program.learn", mapper.valueToTree(Map.of(
                                field, workspace.resolve("source.txt").toString()))));
                assertEquals("PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED:" + field,
                        failure.getMessage());
            }
            Map<String, Object> patchRequest = Map.of(
                    "repository_root", workspace.toString(),
                    "patch_plan_file", workspace.resolve("patch-plan.json").toString(),
                    "approval_receipt_file", workspace.resolve("approval.json").toString(),
                    "worktree_root", workspace.resolve("attacker-worktree").toString());
            IllegalArgumentException patchFailure = assertThrows(IllegalArgumentException.class,
                    () -> dispatcher.dispatch("patch.apply", mapper.valueToTree(patchRequest)));
            assertEquals("PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED:worktree_root",
                    patchFailure.getMessage());
        } finally {
            if (previous == null) System.clearProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY);
            else System.setProperty(ApprovalAuthorityPaths.AUTHORITY_BASE_PROPERTY, previous);
        }
    }

    @Test
    void symlinkedWorkspaceAliasCannotForkApprovalAuthority() throws Exception {
        Path workspace = temp.resolve("real-workspace");
        Files.createDirectories(workspace);
        Path alias = temp.resolve("workspace-alias");
        try {
            Files.createSymbolicLink(alias, workspace);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            return;
        }
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ApprovalAuthorityPaths.forWorkspace(alias, temp.resolve("authority-base")));
        assertEquals("APPROVAL_AUTHORITY_WORKSPACE_SYMLINK_PROHIBITED", failure.getMessage());
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
