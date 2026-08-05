package io.onsure.platform;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Package-local token registry and least-privilege role model for the loopback API. */
final class LocalAccessControl {
    enum Role { VIEWER, OPERATOR, ADMIN, APPROVER }
    enum Permission { VIEW, OPERATE_PROGRAMS, DISPATCH_WORKFLOW, CONTROL, REQUEST_SETTINGS,
        APPROVE_SETTINGS, REQUEST_PROGRAM_APPROVAL, APPROVE_PROGRAM_APPROVAL }
    record Identity(String actor, Role role, String tokenSha256) {}

    private final List<Entry> entries;

    LocalAccessControl(String primaryToken, Map<String, String> environment) {
        if (primaryToken == null || primaryToken.length() < 32 || primaryToken.length() > 4096) {
            throw new IllegalArgumentException("LOCAL_API_TOKEN_LENGTH_INVALID");
        }
        List<Entry> values = new ArrayList<>();
        add(values, "local-admin", Role.ADMIN, primaryToken);
        Map<String, String> safe = environment == null ? Map.of() : environment;
        optional(values, "local-viewer", Role.VIEWER, safe.get("ONSURE_LOCAL_API_VIEWER_TOKEN"));
        optional(values, "local-operator", Role.OPERATOR, safe.get("ONSURE_LOCAL_API_OPERATOR_TOKEN"));
        optional(values, "local-approver", Role.APPROVER, safe.get("ONSURE_LOCAL_API_APPROVER_TOKEN"));
        this.entries = List.copyOf(values);
    }

    Identity authenticate(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) return null;
        byte[] candidate = digest(authorization.substring("Bearer ".length()));
        Entry matched = null;
        for (Entry entry : entries) {
            if (MessageDigest.isEqual(candidate, entry.tokenDigest())) matched = entry;
        }
        return matched == null ? null : new Identity(
                matched.actor(), matched.role(), HexFormat.of().formatHex(matched.tokenDigest()));
    }

    static boolean allowed(Identity identity, Permission permission) {
        if (identity == null) return false;
        Set<Permission> permissions = switch (identity.role()) {
            case VIEWER -> Set.of(Permission.VIEW);
            case OPERATOR -> Set.of(Permission.VIEW, Permission.OPERATE_PROGRAMS,
                    Permission.REQUEST_PROGRAM_APPROVAL);
            case ADMIN -> Set.of(Permission.VIEW, Permission.OPERATE_PROGRAMS,
                    Permission.DISPATCH_WORKFLOW, Permission.CONTROL, Permission.REQUEST_SETTINGS,
                    Permission.REQUEST_PROGRAM_APPROVAL);
            case APPROVER -> Set.of(Permission.VIEW, Permission.APPROVE_SETTINGS,
                    Permission.APPROVE_PROGRAM_APPROVAL);
        };
        return permissions.contains(permission);
    }

    private static void optional(List<Entry> entries, String actor, Role role, String token) {
        if (token == null || token.isBlank()) return;
        if (token.length() < 32 || token.length() > 4096) {
            throw new IllegalArgumentException("LOCAL_API_ROLE_TOKEN_LENGTH_INVALID:" + role);
        }
        add(entries, actor, role, token);
    }

    private static void add(List<Entry> entries, String actor, Role role, String token) {
        byte[] value = digest(token);
        if (entries.stream().anyMatch(entry -> MessageDigest.isEqual(entry.tokenDigest(), value))) {
            throw new IllegalArgumentException("LOCAL_API_ROLE_TOKEN_REUSE_PROHIBITED");
        }
        entries.add(new Entry(actor, role, value));
    }

    private static byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (Exception impossible) {
            throw new IllegalStateException(impossible);
        }
    }

    private record Entry(String actor, Role role, byte[] tokenDigest) {
        private Entry {
            tokenDigest = tokenDigest.clone();
        }
        @Override public byte[] tokenDigest() { return tokenDigest.clone(); }
    }
}
