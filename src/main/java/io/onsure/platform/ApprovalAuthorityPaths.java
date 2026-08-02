package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import io.onsure.assurance.ApprovalReceiptVerifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fixed local trust root and product-owned path policy for local workflows. */
public record ApprovalAuthorityPaths(
        Path authorityRoot,
        Path trustedKeyRegistry,
        Path replayLedger) {

    public static final String AUTHORITY_BASE_PROPERTY = "onsure.approvalAuthorityBase";
    public static final String AUTHORITY_BASE_ENV = "ONSURE_APPROVAL_AUTHORITY_BASE";
    public static final String DEFAULT_AUTHORITY_BASE = ".onsure-authority/approval-authority";
    public static final String KEY_REGISTRY_FILE = "trusted-key-registry.json";
    public static final String REPLAY_LEDGER_FILE = "approval-replay-ledger.jsonl";
    private static final Set<String> AUTHORITY_OVERRIDE_FIELDS = Set.of(
            "trusted_key_registry", "approval_key_registry",
            "approval_replay_ledger", "verification_replay_ledger",
            "approval_replay_external_anchor",
            "approval_authority_root", "approval_authority_base");
    private static final Set<String> PRODUCT_STATE_OVERRIDE_FIELDS = Set.of(
            "catalog_root", "store_root", "license_store_root", "case_store_root",
            "memory_root", "project_memory_root", "reusable_pattern_root",
            "jobs_root", "checkpoint_root", "queue_root",
            "output_file", "approved_plan_file", "evidence_root", "rollback_receipt_file");
    private static final Map<String, Set<String>> OPERATION_STATE_OVERRIDE_FIELDS = Map.of(
            "patch.apply", Set.of("worktree_root"));

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
        requireNoSymlink(workspace, "APPROVAL_AUTHORITY_WORKSPACE_SYMLINK_PROHIBITED");
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

    /** Finds exactly one fixed authority belonging to an ancestor workspace. */
    public static ApprovalAuthorityPaths discoverForContainedPath(Path containedPath) {
        Path contained = normalize(containedPath, "containedPath");
        requireNoSymlink(contained, "APPROVAL_AUTHORITY_CONTAINED_PATH_SYMLINK_PROHIBITED");
        List<ApprovalAuthorityPaths> matches = new ArrayList<>();
        for (Path candidateWorkspace = contained; candidateWorkspace != null;
                candidateWorkspace = candidateWorkspace.getParent()) {
            try {
                ApprovalAuthorityPaths candidate = forWorkspace(candidateWorkspace);
                if (Files.isRegularFile(candidate.trustedKeyRegistry, LinkOption.NOFOLLOW_LINKS)
                        && Files.isRegularFile(candidate.replayLedger, LinkOption.NOFOLLOW_LINKS)) {
                    matches.add(candidate);
                }
            } catch (IllegalArgumentException ignored) {
                // A parent that contains the configured authority base cannot be a target workspace.
            }
        }
        if (matches.isEmpty()) {
            throw new IllegalArgumentException("APPROVAL_AUTHORITY_NOT_DISCOVERABLE_FROM_PATH");
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException("APPROVAL_AUTHORITY_AMBIGUOUS_FOR_PATH");
        }
        ApprovalAuthorityPaths authority = matches.get(0);
        authority.requireTrustedKeyRegistry();
        authority.requireReplayLedger();
        return authority;
    }

    public void rejectRequestOverrides(JsonNode request) {
        rejectRequestOverrides("UNKNOWN", request);
    }

    /** Requests may not select trust roots or product-owned state/output locations. */
    public void rejectRequestOverrides(String operation, JsonNode request) {
        if (request == null || !request.isObject()) return;
        for (String field : AUTHORITY_OVERRIDE_FIELDS) {
            if (!request.path(field).asText("").isBlank()) {
                throw new IllegalArgumentException(
                        "APPROVAL_AUTHORITY_PATH_OVERRIDE_PROHIBITED:" + field);
            }
        }
        for (String field : PRODUCT_STATE_OVERRIDE_FIELDS) {
            if (!request.path(field).asText("").isBlank()) {
                throw new IllegalArgumentException(
                        "PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED:" + field);
            }
        }
        boolean patchApplyShape = !request.path("repository_root").asText("").isBlank()
                && !request.path("patch_plan_file").asText("").isBlank()
                && !request.path("approval_receipt_file").asText("").isBlank();
        if (("patch.apply".equals(operation) || patchApplyShape)
                && !request.path("worktree_root").asText("").isBlank()) {
            throw new IllegalArgumentException(
                    "PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED:worktree_root");
        }
        for (String field : OPERATION_STATE_OVERRIDE_FIELDS.getOrDefault(operation, Set.of())) {
            if (!request.path(field).asText("").isBlank()) {
                throw new IllegalArgumentException(
                        "PRODUCT_STATE_PATH_OVERRIDE_PROHIBITED:" + field);
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

    public Path replayExternalAnchor() {
        Path anchor = ApprovalReceiptVerifier.externalAnchorFor(replayLedger);
        if (anchor.startsWith(authorityRoot) || authorityRoot.startsWith(anchor)) {
            throw new IllegalArgumentException("APPROVAL_REPLAY_EXTERNAL_ANCHOR_NOT_SEPARATE");
        }
        requireNoSymlink(anchor, "APPROVAL_REPLAY_EXTERNAL_ANCHOR_SYMLINK_PROHIBITED");
        return anchor;
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
