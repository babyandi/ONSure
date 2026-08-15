package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Direct unit coverage for the shared hash-chain utility extracted for the 2026-08-15 tamper-evidence hardening. */
class HashChainRecordStoreTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void anEmptyHistoryVerifiesCleanAtGenesis() {
        var chain = HashChainRecordStore.verify(mapper, List.of());
        assertTrue(chain.valid());
        assertEquals(HashChainRecordStore.GENESIS, chain.head());
    }

    @Test
    void aChainOfAppendedRecordsVerifiesClean() {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "a")));
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "b")));
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "c")));
        var chain = HashChainRecordStore.verify(mapper, history);
        assertTrue(chain.valid());
        assertTrue(chain.violations().isEmpty());
    }

    @Test
    void eachRecordLinksToThePreviousEntryHash() {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "a")));
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "b")));
        assertEquals(HashChainRecordStore.GENESIS, history.get(0).get("previous_hash"));
        assertEquals(history.get(0).get("entry_hash"), history.get(1).get("previous_hash"));
    }

    @Test
    void aFieldValueChangedAfterAppendIsDetected() {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "a")));
        Map<String, Object> tampered = new LinkedHashMap<>(history.get(0));
        tampered.put("value", "tampered");
        var chain = HashChainRecordStore.verify(mapper, List.of(tampered));
        assertFalse(chain.valid());
        assertTrue(chain.violations().contains("CHAIN_ENTRY_TAMPERED"));
    }

    @Test
    void aDroppedMiddleRecordBreaksTheSequence() {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "a")));
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "b")));
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "c")));
        List<Map<String, Object>> withDropped = new ArrayList<>(List.of(history.get(0), history.get(2)));
        var chain = HashChainRecordStore.verify(mapper, withDropped);
        assertFalse(chain.valid());
    }

    @Test
    void appendingOntoAnAlreadyTamperedHistoryIsRejected() {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "a")));
        Map<String, Object> tampered = new LinkedHashMap<>(history.get(0));
        tampered.put("value", "tampered");
        List<Map<String, Object>> tamperedHistory = List.of(tampered);
        IllegalStateException rejected = assertThrows(IllegalStateException.class,
                () -> HashChainRecordStore.nextRecord(mapper, tamperedHistory, Map.of("value", "b")));
        assertTrue(rejected.getMessage().contains("CHAIN_APPEND_REJECTED_PRIOR_HISTORY_INVALID"));
    }

    @Test
    void aReorderedPairOfRecordsBreaksTheChain() {
        List<Map<String, Object>> history = new ArrayList<>();
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "a")));
        history.add(HashChainRecordStore.nextRecord(mapper, history, Map.of("value", "b")));
        List<Map<String, Object>> reordered = List.of(history.get(1), history.get(0));
        var chain = HashChainRecordStore.verify(mapper, reordered);
        assertFalse(chain.valid());
    }
}
