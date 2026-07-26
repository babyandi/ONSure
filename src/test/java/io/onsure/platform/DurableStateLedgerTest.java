package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DurableStateLedgerTest {
    @TempDir Path temp;

    @Test
    void stateAndLedgerRemainBoundAcrossMutations() throws Exception {
        DurableStateLedger store = new DurableStateLedger(
                temp.resolve("entity"), "TEST_STATE_V1", "TEST_EVENT_V1", "entity_id", "entity-001");
        Map<String, Object> initial = new LinkedHashMap<>();
        initial.put("status", "CREATED");
        initial.put("nullable", null);
        store.initialize(initial, "CREATED", "actor-001", Map.of("value", 1));
        store.mutate("UPDATED", "actor-002", state -> {
            state.put("status", "UPDATED");
            return Map.of("value", 2);
        });
        assertTrue(store.verify().valid());
        assertTrue(store.read().containsKey("nullable"));
        assertTrue(((Number) store.read().get("ledger_sequence")).longValue() == 2L);
    }

    @Test
    void stateAndLedgerTamperingAreDetected() throws Exception {
        Path root = temp.resolve("tamper");
        DurableStateLedger store = new DurableStateLedger(
                root, "TEST_STATE_V1", "TEST_EVENT_V1", "entity_id", "entity-001");
        store.initialize(Map.of("status", "CREATED"),
                "CREATED", "actor-001", Map.of("value", 1));

        ObjectMapper mapper = new ObjectMapper();
        Map<String, Object> state = mapper.readValue(
                root.resolve("state.json").toFile(), new TypeReference<>() {});
        state.put("status", "TAMPERED");
        mapper.writeValue(root.resolve("state.json").toFile(), state);
        assertFalse(store.verify().valid());
        assertThrows(IllegalStateException.class, store::read);
    }

    @Test
    void preparedTransactionIsRecoveredBeforeRead() throws Exception {
        Path root = temp.resolve("recover");
        DurableStateLedger store = new DurableStateLedger(
                root, "TEST_STATE_V1", "TEST_EVENT_V1", "entity_id", "entity-001");
        store.initialize(Map.of("status", "CREATED"),
                "CREATED", "actor-001", Map.of("value", 1));
        assertTrue(Files.isRegularFile(root.resolve("state.json")));
        assertTrue(store.verify().valid());
    }
}