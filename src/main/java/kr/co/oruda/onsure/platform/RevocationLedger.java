package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable, append-only ledger for assurance-revocation-event.candidate.v2.schema.json. Revocation
 * events are immutable once issued -- a later revocation can supersede an earlier one for the same
 * subject (supersedes_revocation_sha256), but nothing here ever mutates or deletes a past event.
 * Every event is hash-chained via {@link HashChainRecordStore} as a length-1 chain (Autonomous
 * Development Mode 2026-08-15 tamper-evidence hardening) -- a field flipped outside {@link #issue}
 * fails to read back rather than being trusted.
 */
public final class RevocationLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public RevocationLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public void issue(Map<String, Object> event) throws Exception {
        String revocationId = String.valueOf(event.get("revocation_id"));
        if (!ID_PATTERN.matcher(revocationId).matches()) {
            throw new IllegalArgumentException("REVOCATION_ID_INVALID");
        }
        Path file = root.resolve(revocationId + ".json");
        Path lockFile = root.resolve(".locks").resolve(revocationId + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                throw new IllegalArgumentException("REVOCATION_ID_ALREADY_EXISTS:" + revocationId);
            }
            if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("REVOCATION_RECORD_SYMLINK_PROHIBITED");
            Files.createDirectories(file.getParent());
            Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, List.of(), event);
            mapper.writeValue(file.toFile(), chained);
        });
    }

    private Map<String, Object> readVerified(Path file) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> raw = mapper.readValue(file.toFile(), Map.class);
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, List.of(raw));
        if (!chain.valid()) {
            throw new IllegalStateException("REVOCATION_LEDGER_CHAIN_INVALID:" + chain.violations());
        }
        Map<String, Object> event = new LinkedHashMap<>(raw);
        event.remove("sequence");
        event.remove("previous_hash");
        event.remove("entry_hash");
        return event;
    }

    /**
     * Every persisted revocation event whose subject matches (subject_type, subject_id). Includes
     * events later superseded by another -- callers that only want the currently-active view
     * should exclude any event referenced by another event's supersedes_revocation_sha256.
     */
    public List<Map<String, Object>> forSubject(String subjectType, String subjectId) throws Exception {
        List<Map<String, Object>> matches = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return matches;
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                if (Files.isSymbolicLink(file)) continue;
                Map<String, Object> event = readVerified(file);
                @SuppressWarnings("unchecked")
                Map<String, Object> subject = (Map<String, Object>) event.get("subject");
                if (subject != null && subjectType.equals(subject.get("subject_type"))
                        && subjectId.equals(subject.get("subject_id"))) {
                    matches.add(event);
                }
            }
        }
        return matches;
    }

    public List<Map<String, Object>> all() throws Exception {
        List<Map<String, Object>> events = new ArrayList<>();
        if (!Files.isDirectory(root, LinkOption.NOFOLLOW_LINKS)) return events;
        try (var stream = Files.list(root)) {
            for (Path file : stream.filter(path -> path.getFileName().toString().endsWith(".json")).toList()) {
                if (Files.isSymbolicLink(file)) continue;
                events.add(readVerified(file));
            }
        }
        return events;
    }
}
