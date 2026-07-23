package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.FailureMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Reusable cross-run registry of observed failure modes. */
public final class FailureModeRegistry {
    private static final ConcurrentHashMap<Path, Object> FILE_LOCKS = new ConcurrentHashMap<>();

    public record RegistryEntry(
            String code,
            String title,
            String trigger,
            String impact,
            long observationCount,
            List<String> findingIds) {
        public RegistryEntry { findingIds = List.copyOf(findingIds); }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules().enable(SerializationFeature.INDENT_OUTPUT);
    private final Path file;

    public FailureModeRegistry(Path file) {
        this.file = file.toAbsolutePath().normalize();
    }

    public void register(List<FailureMode> modes) throws Exception {
        synchronized (lock()) {
            Map<String, RegistryEntry> values = new LinkedHashMap<>();
            for (RegistryEntry entry : readUnlocked()) values.put(entry.code(), entry);
            for (FailureMode mode : modes) {
                RegistryEntry prior = values.get(mode.code());
                List<String> findings = new ArrayList<>();
                long count = 1;
                if (prior != null) {
                    findings.addAll(prior.findingIds());
                    count = prior.observationCount() + 1;
                }
                for (String findingId : mode.findingIds()) {
                    if (!findings.contains(findingId)) findings.add(findingId);
                }
                values.put(mode.code(), new RegistryEntry(
                        mode.code(), mode.title(), mode.trigger(), mode.impact(), count, findings));
            }
            writeUnlocked(new ArrayList<>(values.values()));
        }
    }

    public List<RegistryEntry> read() throws Exception {
        synchronized (lock()) {
            return readUnlocked();
        }
    }

    private List<RegistryEntry> readUnlocked() throws Exception {
        if (!Files.exists(file)) return List.of();
        return List.copyOf(mapper.readValue(file.toFile(), new TypeReference<List<RegistryEntry>>() {}));
    }

    private void writeUnlocked(Object value) throws Exception {
        Path parent = file.getParent();
        if (parent != null) Files.createDirectories(parent);
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private Object lock() {
        return FILE_LOCKS.computeIfAbsent(file, ignored -> new Object());
    }
}
