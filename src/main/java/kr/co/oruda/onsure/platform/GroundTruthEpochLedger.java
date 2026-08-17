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
 * Durable, append-only global ground-truth knowledge-epoch declaration history (doc 158
 * contradiction class 11 "Ground-truth Drift vs Historical Immutability", LC-P0-011's
 * GroundTruthAuthority half). A single, global, hash-chained sequence: declaring a new "current"
 * epoch is a real authority-gated act (only AUDITOR/ADMIN, checked against the caller's actual
 * roles, never a caller-declared claim -- same shape as HazardLedger's safety-authority check),
 * recorded permanently, never overwritten. This is what makes
 * decision-time-knowledge-snapshot.v1.schema.json's current_knowledge_epoch trustworthy: it can be
 * validated against a real declared epoch instead of accepted as an arbitrary caller-supplied
 * string.
 */
public final class GroundTruthEpochLedger {
    private static final Pattern ID_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path file;
    private final Path lockFile;

    public GroundTruthEpochLedger(Path root) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        this.file = normalizedRoot.resolve("ground-truth-epochs.json");
        this.lockFile = normalizedRoot.resolve(".locks").resolve("ground-truth-epochs.lock");
    }

    public record EpochDeclaration(String epochId, String declaredBy, String declaredAt) {}

    /** authorityConfirmed must be computed from the caller's real roles, never trusted from a request field. */
    public EpochDeclaration declare(String epochId, String declaredBy, boolean authorityConfirmed) throws Exception {
        if (!ID_PATTERN.matcher(epochId).matches()) throw new IllegalArgumentException("GROUND_TRUTH_EPOCH_ID_INVALID");
        if (!authorityConfirmed) throw new SecurityException("GROUND_TRUTH_EPOCH_DECLARATION_REQUIRES_AUTHORITY");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<Map<String, Object>> raw = readRaw();
            if (raw.stream().anyMatch(row -> epochId.equals(row.get("epoch_id")))) {
                throw new IllegalArgumentException("GROUND_TRUTH_EPOCH_ALREADY_DECLARED:" + epochId);
            }
            EpochDeclaration declaration = new EpochDeclaration(epochId, declaredBy, Instant.now().toString());
            Map<String, Object> fields = new LinkedHashMap<>();
            fields.put("epoch_id", declaration.epochId());
            fields.put("declared_by", declaration.declaredBy());
            fields.put("declared_at", declaration.declaredAt());
            Map<String, Object> chained = HashChainRecordStore.nextRecord(mapper, raw, fields);
            List<Map<String, Object>> updated = new ArrayList<>(raw);
            updated.add(chained);
            Files.createDirectories(file.getParent());
            mapper.writeValue(file.toFile(), updated);
            return declaration;
        });
    }

    public boolean isDeclaredEpoch(String epochId) throws Exception {
        return readRaw().stream().anyMatch(row -> epochId.equals(row.get("epoch_id")));
    }

    public String latestEpoch() throws Exception {
        List<Map<String, Object>> raw = readRaw();
        if (raw.isEmpty()) return null;
        return (String) raw.get(raw.size() - 1).get("epoch_id");
    }

    private List<Map<String, Object>> readRaw() throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("GROUND_TRUTH_EPOCH_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, Object>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        HashChainRecordStore.ChainVerification chain = HashChainRecordStore.verify(mapper, raw);
        if (!chain.valid()) {
            throw new IllegalStateException("GROUND_TRUTH_EPOCH_LEDGER_CHAIN_INVALID:" + chain.violations());
        }
        return raw;
    }
}
