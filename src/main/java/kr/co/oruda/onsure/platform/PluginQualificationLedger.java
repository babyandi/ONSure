package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable last-known-qualification record, one file per plugin_id (plugin-manifest.v1.schema.json
 * SS5: "any artifact digest change requires requalification"). This is the memory that makes that
 * rule real: without it, a re-qualification call has no prior state to compare against and every
 * call would trivially look like a first qualification. By design this stores only the latest
 * state (a requalification overwrites, it does not accumulate history) -- but the current record
 * is still hash-chained via {@link HashChainRecordStore} as a length-1 chain (Autonomous
 * Development Mode 2026-08-15 tamper-evidence hardening) so a qualification_state flipped outside
 * {@link #save} fails to read back rather than being trusted.
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
        Map<String, Object> row = mapper.readValue(file.toFile(), Map.class);
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, List.of(row));
        if (!chain.valid()) {
            throw new IllegalStateException("PLUGIN_QUALIFICATION_LEDGER_CHAIN_INVALID:" + chain.violations());
        }
        return new Record(
                (String) row.get("plugin_id"), (String) row.get("artifact_digest"),
                (String) row.get("qualification_state"), (String) row.get("qualified_at"));
    }

    public void save(Record record) throws Exception {
        Path file = root.resolve(record.pluginId() + ".json");
        Path lockFile = root.resolve(".locks").resolve(record.pluginId() + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            Files.createDirectories(file.getParent());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("plugin_id", record.pluginId());
            fields.put("artifact_digest", record.artifactDigest());
            fields.put("qualification_state", record.qualificationState());
            fields.put("qualified_at", record.qualifiedAt());
            Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, List.of(), fields);
            mapper.writeValue(file.toFile(), chained);
        });
    }
}
