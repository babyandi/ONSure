package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;

/** Fixed local trust root for every signed approval consumed by product workflows. */
public record ApprovalAuthorityPaths(
        Path authorityRoot,
        Path trustedKeyRegistry,
        Path replayLedger) {

    public static final String AUTHORITY_BASE_PROPERTY = "onsure.approvalAuthorityBase";
    public static final String AUTHORITY_BASE_ENV = "ONSURE_APPROVAL_AUTHORITY_BASE";
    public static final String DEFAULT_AUTHORITY_BASE = ".onsure-authority/approval-authority";
    public static final String KEY_REGISTRY_FILE = "trusted-key-registry.json";
    public static final String REPLAY_LEDGER_FILE = "approval-replay-ledger.jsonl";
    private static final List<String> OVERRIDE_FIELDS = List.of(
            "trusted_key_registry", "approval_key_registry",
            "approval_replay_ledger", "verification_replay_ledger",
            "approval_authority_root", "approval_authority_base");

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
        return forWorkspace(workspaceRoot, configuredAuthorityBase());
    }

    static ApprovalAuthorityPaths forWorkspace(Path workspaceRoot, Path authorityBase) {
        Path workspace = normalize(workspaceRoot, "workspaceRoot");
        Path base = normalize(authorityBase, "authorityBase");
        String workspaceId = sha256(workspace.toString()).substring(0, 24);
        Path root = base.resolve(workspaceId).normalize();
        if (root.startsWith(workspace) || workspace.startsWith(root)) {
            throw new IllegalArgumentException("APPROVAL_AUTHORITY_MUST_BE_OUTSIDE_TARGET_WORKSPACE");
        }
        requireNoSymlink(base, "APPROVAL_AUTHORITY_BASE_SYMLINK_PROHIBITED");
        requireNoSymlink(root, "APPROVAL_AUTHORITY_ROOT_SYMLINK_PROHIBITED");
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

    private static Path configuredAuthorityBase() {
        String configured = System.getProperty(AUTHORITY_BASE_PROPERTY);
        if (configured == null || configured.isBlank()) configured = System.getenv(AUTHORITY_BASE_ENV);
        if (configured != null && !configured.isBlank()) return Path.of(configured);
        String home = System.getProperty("user.home");
        if (home == null || home.isBlank()) {
            throw new IllegalStateException("APPROVAL_AUTHORITY_USER_HOME_UNAVAILABLE");
        }
        return Path.of(home).resolve(DEFAULT_AUTHORITY_BASE);
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

    private static String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception failure) {
            throw new IllegalStateException("APPROVAL_AUTHORITY_WORKSPACE_ID_FAILED", failure);
        }
    }
}
