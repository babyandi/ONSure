package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Durable, append-only override history (doc 158 contradiction class 9 "Human Override vs
 * Self-confirmation", LC-P0-009's HumanOverrideTrendReport half), one file per candidate_ref
 * holding every override disposition ever recorded for that candidate. Real aggregate figures
 * (promotion rate, self-confirmation-rejection rate) are computed from this actual history at
 * report time, never a caller-supplied summary. Hash-chained via {@link HashChainRecordStore} from
 * the start.
 */
public final class HumanOverrideTrendLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public HumanOverrideTrendLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record Entry(String overrideId, boolean promoted, boolean selfConfirmationRejected, String recordedAt) {}

    public record TrendReport(
            String candidateRef, int totalOverrides, int promotedCount,
            int selfConfirmationRejectedCount, double promotionRate) {}

    public void record(String candidateRef, String overrideId, boolean promoted, boolean selfConfirmationRejected)
            throws Exception {
        if (!ID_PATTERN.matcher(candidateRef).matches()) throw new IllegalArgumentException("OVERRIDE_TREND_CANDIDATE_REF_INVALID");
        if (!ID_PATTERN.matcher(overrideId).matches()) throw new IllegalArgumentException("OVERRIDE_TREND_OVERRIDE_ID_INVALID");
        Path file = root.resolve(candidateRef + ".json");
        Path lockFile = root.resolve(".locks").resolve(candidateRef + ".lock");
        ExclusiveFileLock.run(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw(file);
            if (raw.stream().anyMatch(row -> overrideId.equals(row.get("override_id")))) {
                throw new IllegalArgumentException("OVERRIDE_TREND_OVERRIDE_ID_REPLAY:" + overrideId);
            }
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("override_id", overrideId);
            fields.put("promoted", promoted);
            fields.put("self_confirmation_rejected", selfConfirmationRejected);
            fields.put("recorded_at", Instant.now().toString());
            Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, raw, fields);
            List<Map<String, Object>> updated = new ArrayList<>(raw);
            updated.add(chained);
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), updated);
        });
    }

    /** Real aggregate computed from the actual recorded history -- never a caller-supplied claim. */
    public TrendReport report(String candidateRef) throws Exception {
        Path file = root.resolve(candidateRef + ".json");
        List<Map<String, Object>> raw = readRaw(file);
        int total = raw.size();
        long promoted = raw.stream().filter(row -> Boolean.TRUE.equals(row.get("promoted"))).count();
        long selfRejected = raw.stream().filter(row -> Boolean.TRUE.equals(row.get("self_confirmation_rejected"))).count();
        double rate = total == 0 ? 0.0 : (double) promoted / total;
        return new TrendReport(candidateRef, total, (int) promoted, (int) selfRejected, rate);
    }

    private List<Map<String, Object>> readRaw(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("OVERRIDE_TREND_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, Object>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, raw);
        if (!chain.valid()) {
            throw new IllegalStateException("OVERRIDE_TREND_LEDGER_CHAIN_INVALID:" + chain.violations());
        }
        return raw;
    }
}
