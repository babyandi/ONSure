package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable last-known-qualification record, one file per plugin_id (plugin-manifest.v1.schema.json
 * SS5: "any artifact digest change requires requalification"). This is the memory that makes that
 * rule real: without it, a re-qualification call has no prior state to compare against and every
 * call would trivially look like a first qualification.
 */
public final class PluginQualificationLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public PluginQualificationLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Record(String pluginId, String artifactDigest, String qualificationState, String qualifiedAt) {}

    public Record last(String pluginId) throws Exception {
        if (!ID_PATTERN.matcher(pluginId).matches()) throw new IllegalArgumentException("PLUGIN_ID_INVALID");
        Path file = root.resolve(pluginId + ".json");
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return null;
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("PLUGIN_QUALIFICATION_RECORD_SYMLINK_PROHIBITED");
        @SuppressWarnings("unchecked")
        Map<String, String> row = mapper.readValue(file.toFile(), Map.class);
        return new Record(row.get("plugin_id"), row.get("artifact_digest"), row.get("qualification_state"), row.get("qualified_at"));
    }

    public void save(Record record) throws Exception {
        Path file = root.resolve(record.pluginId() + ".json");
        Path lockFile = root.resolve(".locks").resolve(record.pluginId() + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            Files.createDirectories(file.getParent());
            Map<String, String> row = new LinkedHashMap<>();
            row.put("plugin_id", record.pluginId());
            row.put("artifact_digest", record.artifactDigest());
            row.put("qualification_state", record.qualificationState());
            row.put("qualified_at", record.qualifiedAt());
            mapper.writeValue(file.toFile(), row);
        });
    }
}
