package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.List;

/** Fixed local trust root for every signed approval consumed by product workflows. */
public record ApprovalAuthorityPaths(
        Path authorityRoot,
        Path trustedKeyRegistry,
        Path replayLedger) {

    public static final String AUTHORITY_DIRECTORY = ".onsure/approval-authority";
    public static final String KEY_REGISTRY_FILE = "trusted-key-registry.json";
    public static final String REPLAY_LEDGER_FILE = "approval-replay-ledger.jsonl";
    private static final List<String> OVERRIDE_FIELDS = List.of(
            "trusted_key_registry", "approval_key_registry",
            "approval_replay_ledger", "verification_replay_ledger");

    public ApprovalAuthorityPaths {
        authorityRoot = normalize(authorityRoot, "authorityRoot");
        trustedKeyRegistry = normalize(trustedKeyRegistry, "trustedKeyRegistry");
        replayLedger = normalize(replayLedger, "replayLedger");
        if (!trustedKeyRegistry.getParent().equals(authorityRoot)
                || !replayLedger.getParent().equals(authorityRoot)
                || !KEY_REGISTRY_FILE.equals(trustedKeyRegistry.getFileName().toString())
                || !REPLAY_LEDGER_FILE.equals(replayLedger.getFileName().toString())) {
            throw new IllegalArgumentException("APPROVAL_AUTHORITY_PATHS_NOT_CANONICAL");
        }
    }

    public static ApprovalAuthorityPaths forWorkspace(Path workspaceRoot) {
        Path workspace = normalize(workspaceRoot, "workspaceRoot");
        Path root = workspace.resolve(AUTHORITY_DIRECTORY).normalize();
        if (!root.startsWith(workspace)) {
            throw new IllegalArgumentException("APPROVAL_AUTHORITY_ROOT_OUTSIDE_WORKSPACE");
        }
        return new ApprovalAuthorityPaths(
                root, root.resolve(KEY_REGISTRY_FILE), root.resolve(REPLAY_LEDGER_FILE));
    }

    /** Requests may not select or replace the product trust root. */
    public void rejectRequestOverrides(JsonNode request) {
        for (String field : OVERRIDE_FIELDS) {
            if (request != null && !request.path(field).asText("").isBlank()) {
                throw new IllegalArgumentException(
                        "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED:" + field);
            }
        }
    }

    public Path requireTrustedKeyRegistry() {
        requireRegularFile(trustedKeyRegistry, "APPROVAL_TRUSTED_KEY_REGISTRY_MISSING");
        return trustedKeyRegistry;
    }

    public Path requireReplayLedger() {
        requireRegularFile(replayLedger, "APPROVAL_REPLAY_LEDGER_MISSING");
        return replayLedger;
    }

    public Path replayLedgerForConsumption() {
        requireNoSymlink(authorityRoot, "APPROVAL_AUTHORITY_ROOT_SYMLINK_PROHIBITED");
        requireNoSymlink(replayLedger, "APPROVAL_REPLAY_LEDGER_SYMLINK_PROHIBITED");
        return replayLedger;
    }

    private static void requireRegularFile(Path path, String code) {
        requireNoSymlink(path, code + "_SYMLINK");
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException(code);
        }
    }

    private static void requireNoSymlink(Path path, String code) {
        Path current = path;
        while (current != null) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException(code);
            }
            current = current.getParent();
        }
    }

    private static Path normalize(Path value, String name) {
        if (value == null) throw new IllegalArgumentException(name + " required");
        return value.toAbsolutePath().normalize();
    }
}
