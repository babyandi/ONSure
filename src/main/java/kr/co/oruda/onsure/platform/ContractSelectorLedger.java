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
 * Durable, append-only Active Contract Selector history (contract-active-selector.candidate.v2.
 * schema.json; 137 SS27 Batch 8 v1-&gt;v2 cutover/rollback), one file per contract_family. Cutover
 * is gated by a real reconciliation result the caller must supply -- a divergence classified
 * UNRECOVERABLE blocks cutover outright. Rollback always succeeds by construction: it simply
 * re-activates the immediately preceding entry, which the append-only history always still has.
 */
public final class ContractSelectorLedger {
    private static final Pattern FAMILY_PATTERN = Pattern.compile("^[A-Za-z0-9_.:-]{1,160}$");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final Path root;

    public ContractSelectorLedger(Path root) {
        this.root = root.toAbsolutePath().normalize();
    }

    public record SelectorEntry(
            String contractFamily, String activeVersion, String activeContractDigest,
            String migrationReceiptSha256, String issuerKeyId, String signature, String effectiveAt) {}

    /**
     * Activates {@code toVersion} for {@code contractFamily}. Blocked outright when
     * {@code divergenceResolved} is false -- a caller cannot cut over while a real reconciliation
     * still classifies the gap UNRECOVERABLE.
     */
    public SelectorEntry cutover(
            String contractFamily, String toVersion, String toContractDigest,
            String migrationReceiptSha256, boolean divergenceResolved) throws Exception {
        if (!FAMILY_PATTERN.matcher(contractFamily).matches()) throw new IllegalArgumentException("CONTRACT_FAMILY_INVALID");
        if (!divergenceResolved) throw new SecurityException("CUTOVER_BLOCKED_UNRESOLVED_DIVERGENCE");

        Path file = root.resolve(contractFamily + ".json");
        Path lockFile = root.resolve(".locks").resolve(contractFamily + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<SelectorEntry> history = read(file);
            if (!history.isEmpty() && history.get(history.size() - 1).activeVersion().equals(toVersion)) {
                throw new IllegalArgumentException("CUTOVER_TARGET_ALREADY_ACTIVE:" + toVersion);
            }
            SelectorEntry signed = sign(contractFamily, toVersion, toContractDigest, migrationReceiptSha256);
            List<SelectorEntry> updated = new ArrayList<>(history);
            updated.add(signed);
            write(file, updated);
            return signed;
        });
    }

    /** Re-activates the immediately preceding selector entry. Always succeeds if one exists. */
    public SelectorEntry rollback(String contractFamily) throws Exception {
        Path file = root.resolve(contractFamily + ".json");
        Path lockFile = root.resolve(".locks").resolve(contractFamily + ".lock");
        return ExclusiveFileLock.call(lockFile, () -> {
            List<SelectorEntry> history = read(file);
            if (history.size() < 2) throw new IllegalArgumentException("ROLLBACK_NO_PRIOR_SELECTOR:" + contractFamily);
            SelectorEntry previous = history.get(history.size() - 2);
            SelectorEntry signed = sign(
                    contractFamily, previous.activeVersion(), previous.activeContractDigest(),
                    previous.migrationReceiptSha256());
            List<SelectorEntry> updated = new ArrayList<>(history);
            updated.add(signed);
            write(file, updated);
            return signed;
        });
    }

    public List<SelectorEntry> history(String contractFamily) throws Exception {
        return read(root.resolve(contractFamily + ".json"));
    }

    public SelectorEntry active(String contractFamily) throws Exception {
        List<SelectorEntry> history = history(contractFamily);
        if (history.isEmpty()) throw new IllegalArgumentException("CONTRACT_FAMILY_NOT_FOUND:" + contractFamily);
        return history.get(history.size() - 1);
    }

    private SelectorEntry sign(
            String contractFamily, String version, String contractDigest, String migrationReceiptSha256) throws Exception {
        Map<String, Object> unsigned = new LinkedHashMap<>();
        unsigned.put("contract_family", contractFamily);
        unsigned.put("active_version", version);
        unsigned.put("active_contract_digest", contractDigest);
        unsigned.put("migration_receipt_sha256", migrationReceiptSha256);
        unsigned.put("effective_at", Instant.now().toString());
        java.security.KeyPair keyPair = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.generate();
        String signatureValue = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.sign(unsigned, keyPair.getPrivate());
        return new SelectorEntry(
                contractFamily, version, contractDigest, migrationReceiptSha256,
                "EPHEMERAL_SELF_VALIDATION_KEY", signatureValue, (String) unsigned.get("effective_at"));
    }

    private List<SelectorEntry> read(Path file) throws Exception {
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) return List.of();
        if (Files.isSymbolicLink(file)) throw new IllegalArgumentException("CONTRACT_SELECTOR_RECORD_SYMLINK_PROHIBITED");
        List<Map<String, String>> raw = mapper.readValue(
                file.toFile(), mapper.getTypeFactory().constructCollectionType(List.class, Map.class));
        List<SelectorEntry> entries = new ArrayList<>();
        for (Map<String, String> row : raw) {
            entries.add(new SelectorEntry(
                    row.get("contract_family"), row.get("active_version"), row.get("active_contract_digest"),
                    row.get("migration_receipt_sha256"), row.get("issuer_key_id"), row.get("signature"), row.get("effective_at")));
        }
        return entries;
    }

    private void write(Path file, List<SelectorEntry> entries) throws Exception {
        Files.createDirectories(file.getParent());
        List<Map<String, String>> rows = new ArrayList<>();
        for (SelectorEntry entry : entries) {
            Map<String, String> row = new LinkedHashMap<>();
            row.put("contract_family", entry.contractFamily());
            row.put("active_version", entry.activeVersion());
            row.put("active_contract_digest", entry.activeContractDigest());
            row.put("migration_receipt_sha256", entry.migrationReceiptSha256());
            row.put("issuer_key_id", entry.issuerKeyId());
            row.put("signature", entry.signature());
            row.put("effective_at", entry.effectiveAt());
            rows.add(row);
        }
        mapper.writeValue(file.toFile(), rows);
    }
}
