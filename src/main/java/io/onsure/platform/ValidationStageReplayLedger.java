package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Fail-closed stage idempotency ledger. It removes only files created by an interrupted stage. */
final class ValidationStageReplayLedger {
    static final String CONTRACT = "ONSURE_VALIDATION_STAGE_REPLAY_LEDGER_V1";
    static final String FILE_NAME = "stage-replay-ledger.json";
    private static final TypeReference<LinkedHashMap<String, Object>> MAP = new TypeReference<>() {};
    private static final Set<String> CONTROL_FILES = Set.of(
            FILE_NAME, ValidationStageCheckpointJournal.FILE_NAME, ValidationContextSnapshotStore.FILE_NAME);

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path runRoot;
    private final Path file;

    ValidationStageReplayLedger(Path runRoot, String jobId, String targetId) throws Exception {
        this.runRoot = validRoot(runRoot);
        this.file = this.runRoot.resolve(FILE_NAME);
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            Map<String, Object> initial = new LinkedHashMap<>();
            initial.put("contract", CONTRACT);
            initial.put("version", 1);
            initial.put("job_id", id(jobId));
            initial.put("target_id", id(targetId));
            initial.put("entries", List.of());
            initial.put("completed_stage_reexecution_allowed", false);
            initial.put("interrupted_stage_new_file_cleanup_allowed", true);
            initial.put("preexisting_file_repair_allowed", false);
            initial.put("final_claim_allowed", false);
            write(initial);
        } else {
            verify(jobId, targetId);
        }
    }

    void stageStarted(String stageId, int stageIndex) throws Exception {
        Map<String, Object> ledger = read();
        List<Map<String, Object>> entries = entries(ledger);
        long attempt = entries.stream().filter(value -> stageId.equals(value.get("stage_id"))).count() + 1L;
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("stage_id", id(stageId));
        entry.put("stage_index", stageIndex);
        entry.put("attempt", attempt);
        entry.put("state", "STARTED");
        entry.put("started_at", Instant.now().toString());
        entry.put("before_files", fileManifest());
        entry.put("before_directories", directoryManifest());
        entry.put("replay_disposition", "NOT_APPLICABLE");
        entries.add(entry);
        ledger.put("entries", List.copyOf(entries));
        write(ledger);
    }

    void stageCompleted(String stageId) throws Exception {
        Map<String, Object> ledger = read();
        List<Map<String, Object>> entries = entries(ledger);
        Map<String, Object> entry = current(entries, stageId);
        if (!"STARTED".equals(entry.get("state"))) throw new IllegalStateException("STAGE_REPLAY_ENTRY_NOT_STARTED");
        entry.put("state", "COMPLETED");
        entry.put("completed_at", Instant.now().toString());
        entry.put("after_files_sha256", manifestDigest(fileManifest()));
        ledger.put("entries", List.copyOf(entries));
        write(ledger);
    }

    void stageFailed(String stageId) throws Exception {
        Map<String, Object> ledger = read();
        List<Map<String, Object>> entries = entries(ledger);
        Map<String, Object> entry = current(entries, stageId);
        if ("STARTED".equals(entry.get("state"))) {
            entry.put("state", "INTERRUPTED");
            entry.put("interrupted_at", Instant.now().toString());
            ledger.put("entries", List.copyOf(entries));
            write(ledger);
        }
    }

    void prepareReplay(String stageId, int stageIndex) throws Exception {
        Map<String, Object> ledger = read();
        List<Map<String, Object>> entries = entries(ledger);
        Map<String, Object> entry = current(entries, stageId);
        if (((Number) entry.get("stage_index")).intValue() != stageIndex
                || !List.of("STARTED", "INTERRUPTED").contains(String.valueOf(entry.get("state")))) {
            throw new IllegalStateException("STAGE_REPLAY_BOUNDARY_INVALID");
        }
        Map<String, String> before = stringMap(entry.get("before_files"));
        Map<String, String> current = fileManifest();
        List<String> beforeDirectories = stringList(entry.get("before_directories"));
        List<String> currentDirectories = directoryManifest();
        for (Map.Entry<String, String> expected : before.entrySet()) {
            if (!expected.getValue().equals(current.get(expected.getKey()))) {
                throw new IllegalStateException("STAGE_REPLAY_PREEXISTING_FILE_CHANGED:" + expected.getKey());
            }
        }
        List<Path> created = current.keySet().stream().filter(path -> !before.containsKey(path))
                .map(runRoot::resolve).sorted(Comparator.reverseOrder()).toList();
        for (Path path : created) {
            if (!path.normalize().startsWith(runRoot) || Files.isSymbolicLink(path)) {
                throw new IllegalStateException("STAGE_REPLAY_CREATED_PATH_INVALID");
            }
            Files.deleteIfExists(path);
        }
        for (String relative : beforeDirectories) {
            Path path = runRoot.resolve(relative).normalize();
            if (!path.startsWith(runRoot) || !Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                    || Files.isSymbolicLink(path)) {
                throw new IllegalStateException("STAGE_REPLAY_PREEXISTING_DIRECTORY_CHANGED:" + relative);
            }
        }
        List<Path> createdDirectories = currentDirectories.stream()
                .filter(path -> !beforeDirectories.contains(path))
                .map(runRoot::resolve).sorted(Comparator.reverseOrder()).toList();
        for (Path path : createdDirectories) {
            if (!path.normalize().startsWith(runRoot) || Files.isSymbolicLink(path)) {
                throw new IllegalStateException("STAGE_REPLAY_CREATED_DIRECTORY_INVALID");
            }
            Files.deleteIfExists(path);
        }
        entry.put("state", "ROLLED_BACK_FOR_REPLAY");
        entry.put("replayed_at", Instant.now().toString());
        entry.put("removed_new_file_count", created.size());
        entry.put("removed_new_directory_count", createdDirectories.size());
        entry.put("replay_disposition", "NEW_FILES_REMOVED_PREEXISTING_FILES_UNCHANGED");
        ledger.put("entries", List.copyOf(entries));
        write(ledger);
    }

    private void verify(String jobId, String targetId) throws Exception {
        Map<String, Object> value = read();
        if (!id(jobId).equals(value.get("job_id")) || !id(targetId).equals(value.get("target_id"))) {
            throw new IllegalStateException("STAGE_REPLAY_LEDGER_BINDING_INVALID");
        }
    }

    private Map<String, String> fileManifest() throws Exception {
        Map<String, String> result = new TreeMap<>();
        try (var paths = Files.walk(runRoot)) {
            for (Path path : paths.toList()) {
                if (Files.isSymbolicLink(path)) {
                    throw new IllegalStateException("STAGE_REPLAY_SYMBOLIC_LINK_FORBIDDEN");
                }
                if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)) continue;
                String relative = runRoot.relativize(path).toString().replace('\\', '/');
                if (!CONTROL_FILES.contains(relative)) result.put(relative, Hashing.file(path));
            }
        }
        return result;
    }

    private String manifestDigest(Map<String, String> manifest) throws Exception {
        return Hashing.sha256(mapper.writeValueAsBytes(new TreeMap<>(manifest)));
    }

    private List<String> directoryManifest() throws Exception {
        try (var paths = Files.walk(runRoot)) {
            List<Path> values = paths.toList();
            if (values.stream().anyMatch(Files::isSymbolicLink)) {
                throw new IllegalStateException("STAGE_REPLAY_SYMBOLIC_LINK_FORBIDDEN");
            }
            return values.stream().filter(value -> !value.equals(runRoot)
                            && Files.isDirectory(value, LinkOption.NOFOLLOW_LINKS))
                    .map(runRoot::relativize)
                    .map(value -> value.toString().replace('\\', '/'))
                    .sorted()
                    .toList();
        }
    }

    private Map<String, Object> read() throws Exception {
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(file)) {
            throw new IllegalStateException("STAGE_REPLAY_LEDGER_FILE_INVALID");
        }
        Map<String, Object> value = mapper.readValue(file.toFile(), MAP);
        if (!CONTRACT.equals(value.get("contract"))) throw new IllegalStateException("STAGE_REPLAY_LEDGER_CONTRACT_INVALID");
        String declared = String.valueOf(value.remove("ledger_sha256"));
        String actual = Hashing.sha256(mapper.writeValueAsBytes(new TreeMap<>(value)));
        if (!declared.equals(actual)) throw new IllegalStateException("STAGE_REPLAY_LEDGER_DIGEST_INVALID");
        return value;
    }

    private void write(Map<String, Object> value) throws Exception {
        value.remove("ledger_sha256");
        value.put("updated_at", Instant.now().toString());
        value.put("ledger_sha256", Hashing.sha256(mapper.writeValueAsBytes(new TreeMap<>(value))));
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            mapper.writeValue(temporary.toFile(), value);
            try {
                Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> entries(Map<String, Object> ledger) {
        Object raw = ledger.get("entries");
        if (!(raw instanceof List<?> list)) throw new IllegalStateException("STAGE_REPLAY_ENTRIES_INVALID");
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : list) {
            if (!(item instanceof Map<?, ?> map)) throw new IllegalStateException("STAGE_REPLAY_ENTRY_INVALID");
            Map<String, Object> copy = new LinkedHashMap<>();
            map.forEach((key, value) -> copy.put(String.valueOf(key), value));
            result.add(copy);
        }
        return result;
    }

    private static Map<String, Object> current(List<Map<String, Object>> entries, String stageId) {
        for (int index = entries.size() - 1; index >= 0; index--) {
            if (stageId.equals(entries.get(index).get("stage_id"))) return entries.get(index);
        }
        throw new IllegalStateException("STAGE_REPLAY_ENTRY_MISSING");
    }

    private static Map<String, String> stringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) throw new IllegalStateException("STAGE_REPLAY_MANIFEST_INVALID");
        Map<String, String> result = new TreeMap<>();
        map.forEach((key, value) -> result.put(String.valueOf(key), String.valueOf(value)));
        return result;
    }

    private static List<String> stringList(Object raw) {
        if (!(raw instanceof List<?> list)) throw new IllegalStateException("STAGE_REPLAY_DIRECTORY_MANIFEST_INVALID");
        return list.stream().map(String::valueOf).sorted().toList();
    }

    private static Path validRoot(Path value) {
        Path path = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("STAGE_REPLAY_RUN_ROOT_INVALID");
        }
        return path;
    }

    private static String id(String value) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,160}")) throw new IllegalArgumentException("STAGE_REPLAY_ID_INVALID");
        return value;
    }
}
