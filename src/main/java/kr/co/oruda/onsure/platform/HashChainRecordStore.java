package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Shared hash-chain tamper-evidence for the one-file-per-subject append-only ledgers in this
 * package (Hazard/Appeal/Offboarding/Revocation/SeparationOfDuties/FourEyes/Delegation/
 * BreakGlass/PluginQualification). Extracted from the pattern proven in
 * {@link kr.co.oruda.onsure.learning.OfficialLearningLedger}, which hash-chains its own JSONL
 * entries directly -- this class gives the same {@code sequence}/{@code previous_hash}/
 * {@code entry_hash} guarantee to ledgers that instead store a plain JSON array (one file per
 * subject, whole-file rewrite on every append), without requiring each of them to reimplement the
 * chain math. A file edited outside this code path (a byte changed, a middle record dropped) fails
 * {@link #verify} with a specific violation code instead of silently being trusted.
 */
public final class HashChainRecordStore {
    public static final String GENESIS = "0".repeat(64);

    private HashChainRecordStore() {}

    public record ChainVerification(boolean valid, List<String> violations, String head) {
        public ChainVerification {
            violations = List.copyOf(violations);
        }
    }

    /**
     * Verifies every record in {@code records} chains correctly from {@link #GENESIS}. Each record
     * must already carry {@code sequence} (1-based long), {@code previous_hash}, and
     * {@code entry_hash} alongside its caller-defined fields.
     */
    public static ChainVerification verify(ObjectMapper mapper, List<Map<String, Object>> records) {
        List<String> violations = new ArrayList<>();
        String previous = GENESIS;
        long expectedSequence = 1;
        try {
            for (Map<String, Object> record : records) {
                Object sequence = record.get("sequence");
                if (!(sequence instanceof Number number) || number.longValue() != expectedSequence) {
                    violations.add("CHAIN_SEQUENCE_BROKEN");
                }
                Object previousHash = record.get("previous_hash");
                if (!previous.equals(previousHash)) {
                    violations.add("CHAIN_PREVIOUS_HASH_BROKEN");
                }
                String calculated = entryHash(mapper, expectedSequence, previous, withoutChainFields(record));
                Object entryHash = record.get("entry_hash");
                if (!calculated.equals(entryHash)) {
                    violations.add("CHAIN_ENTRY_TAMPERED");
                }
                previous = String.valueOf(entryHash);
                expectedSequence++;
            }
        } catch (Exception exception) {
            violations.add("CHAIN_UNREADABLE");
        }
        return new ChainVerification(violations.isEmpty(), violations, previous);
    }

    /**
     * Returns {@code fields} plus the chain-linkage fields (sequence/previous_hash/entry_hash)
     * needed to append it after {@code history} as the next record. Does not mutate or write
     * anything -- callers append the returned map to their own in-memory list and persist it with
     * their existing write path. Throws if {@code history} itself does not already verify, so a
     * tampered file is caught before a new record is chained onto it.
     */
    public static Map<String, Object> nextRecord(
            ObjectMapper mapper, List<Map<String, Object>> history, Map<String, Object> fields) {
        ChainVerification chain = verify(mapper, history);
        if (!chain.valid()) {
            throw new IllegalStateException("CHAIN_APPEND_REJECTED_PRIOR_HISTORY_INVALID:" + chain.violations());
        }
        long sequence = history.size() + 1L;
        Map<String, Object> record = new LinkedHashMap<>(fields);
        record.put("sequence", sequence);
        record.put("previous_hash", chain.head());
        record.put("entry_hash", entryHash(mapper, sequence, chain.head(), fields));
        return record;
    }

    private static Map<String, Object> withoutChainFields(Map<String, Object> record) {
        Map<String, Object> copy = new LinkedHashMap<>(record);
        copy.remove("sequence");
        copy.remove("previous_hash");
        copy.remove("entry_hash");
        return copy;
    }

    private static String entryHash(
            ObjectMapper mapper, long sequence, String previousHash, Map<String, Object> fields) {
        try {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("sequence", sequence);
            body.put("previous_hash", previousHash);
            body.put("fields", new TreeMap<>(fields));
            return sha256(mapper.writeValueAsBytes(body));
        } catch (Exception exception) {
            throw new IllegalStateException("CHAIN_HASH_COMPUTATION_FAILED", exception);
        }
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
