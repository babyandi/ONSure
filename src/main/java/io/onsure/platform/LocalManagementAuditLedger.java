package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/** Append-only content-free audit ledger for authenticated local management mutations. */
final class LocalManagementAuditLedger {
    static final String CONTRACT = "ONSURE_LOCAL_MANAGEMENT_AUDIT_V1";
    private static final String ZERO = "0".repeat(64);
    private static final long MAX_BYTES = 64L * 1024L * 1024L;
    private static final Set<String> PROHIBITED_KEYS = Set.of(
            "token", "password", "secret", "api_key", "authorization", "content", "prompt", "completion");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path file;

    LocalManagementAuditLedger(Path workspaceRoot) throws Exception {
        Path root = workspaceRoot.toAbsolutePath().normalize().resolve(".onsure/management");
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("MANAGEMENT_AUDIT_ROOT_SYMLINK");
        }
        Files.createDirectories(root);
        this.file = root.resolve("audit.jsonl");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) Files.createFile(file);
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalArgumentException("MANAGEMENT_AUDIT_FILE_INVALID");
        }
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) {
            // Process umask is the fallback on non-POSIX hosts.
        }
        readAndVerify();
    }

    synchronized Map<String, Object> append(
            LocalAccessControl.Identity identity, String action, String outcome, Map<String, Object> details)
            throws Exception {
        if (identity == null) throw new IllegalArgumentException("AUDIT_IDENTITY_REQUIRED");
        if (!id(action, 120) || !id(outcome, 40) || containsProhibited(details)) {
            throw new IllegalArgumentException("AUDIT_EVENT_INVALID");
        }
        if (Files.size(file) >= MAX_BYTES) throw new IllegalStateException("MANAGEMENT_AUDIT_SIZE_LIMIT");
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ, StandardOpenOption.WRITE);
             var ignored = channel.lock()) {
            List<Map<String, Object>> current = readAndVerify();
            String previous = current.isEmpty() ? ZERO
                    : String.valueOf(current.get(current.size() - 1).get("entry_sha256"));
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("contract", CONTRACT);
            entry.put("sequence", current.size() + 1L);
            entry.put("previous_entry_sha256", previous);
            entry.put("observed_at", Instant.now().toString());
            entry.put("actor", identity.actor());
            entry.put("role", identity.role().name());
            entry.put("action", action);
            entry.put("outcome", outcome);
            entry.put("details", Map.copyOf(details == null ? Map.of() : details));
            entry.put("final_claim_allowed", false);
            entry.put("entry_sha256", digest(entry));
            byte[] line = (mapper.writeValueAsString(entry) + "\n").getBytes(StandardCharsets.UTF_8);
            channel.position(channel.size());
            ByteBuffer buffer = ByteBuffer.wrap(line);
            while (buffer.hasRemaining()) channel.write(buffer);
            channel.force(true);
            return Collections.unmodifiableMap(entry);
        }
    }

    synchronized Map<String, Object> recent(int limit) throws Exception {
        int bounded = Math.max(1, Math.min(limit, 200));
        List<Map<String, Object>> all = readAndVerify();
        int from = Math.max(0, all.size() - bounded);
        List<Map<String, Object>> events = new ArrayList<>(all.subList(from, all.size()));
        Collections.reverse(events);
        return Map.of(
                "contract", CONTRACT,
                "event_count", all.size(),
                "chain_valid", true,
                "chain_head_sha256", all.isEmpty() ? ZERO : all.get(all.size() - 1).get("entry_sha256"),
                "events", List.copyOf(events),
                "final_claim_allowed", false);
    }

    private List<Map<String, Object>> readAndVerify() throws Exception {
        if (Files.size(file) > MAX_BYTES) throw new IllegalStateException("MANAGEMENT_AUDIT_SIZE_LIMIT");
        List<Map<String, Object>> result = new ArrayList<>();
        String previous = ZERO;
        long sequence = 0;
        for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
            if (line.isBlank()) throw new IllegalStateException("MANAGEMENT_AUDIT_BLANK_LINE");
            Map<String, Object> entry = mapper.readValue(line, new TypeReference<>() {});
            sequence++;
            if (!CONTRACT.equals(entry.get("contract"))
                    || !(entry.get("sequence") instanceof Number number) || number.longValue() != sequence
                    || !previous.equals(entry.get("previous_entry_sha256"))
                    || containsProhibited(entry.get("details"))) {
                throw new IllegalStateException("MANAGEMENT_AUDIT_CHAIN_INVALID");
            }
            String claimed = String.valueOf(entry.get("entry_sha256"));
            if (!claimed.matches("[0-9a-f]{64}") || !claimed.equals(digest(entry))) {
                throw new IllegalStateException("MANAGEMENT_AUDIT_DIGEST_INVALID");
            }
            previous = claimed;
            result.add(Collections.unmodifiableMap(new LinkedHashMap<>(entry)));
        }
        return List.copyOf(result);
    }

    private String digest(Map<String, Object> value) throws Exception {
        Map<String, Object> copy = new java.util.TreeMap<>(value);
        copy.remove("entry_sha256");
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(copy)));
    }

    private static boolean containsProhibited(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = String.valueOf(entry.getKey()).toLowerCase(Locale.ROOT);
                if (PROHIBITED_KEYS.contains(key) || containsProhibited(entry.getValue())) return true;
            }
        } else if (value instanceof Iterable<?> values) {
            for (Object item : values) if (containsProhibited(item)) return true;
        }
        return false;
    }

    private static boolean id(String value, int maximum) {
        return value != null && value.matches("[A-Za-z0-9._:-]{1," + maximum + "}");
    }
}
