package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** NFR-REL (멱등성, 재시도, 중복 이벤트 방어): idempotency and duplicate-event defense half. */
class IdempotencyStoreTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    private JsonNode request(Map<String, Object> body) throws Exception {
        return mapper.valueToTree(body);
    }

    @Test
    void firstCallExecutesAndCachesTheResponse() throws Exception {
        IdempotencyStore store = new IdempotencyStore(temp);
        AtomicInteger executions = new AtomicInteger();
        var lookup = store.resolve("tenant-a", "case.open", "key-00000001", request(Map.of("case_id", "case-1")),
                () -> { executions.incrementAndGet(); return Map.of("decision", "OPENED"); });
        assertEquals(IdempotencyStore.Outcome.FIRST_SEEN, lookup.outcome());
        assertEquals(1, executions.get());
        assertEquals("OPENED", lookup.cachedResponse().get("decision"));
    }

    @Test
    void sameKeySameRequestReplaysWithoutReexecuting() throws Exception {
        IdempotencyStore store = new IdempotencyStore(temp);
        AtomicInteger executions = new AtomicInteger();
        Map<String, Object> body = Map.of("case_id", "case-1");
        store.resolve("tenant-a", "case.open", "key-00000002", request(body),
                () -> { executions.incrementAndGet(); return Map.of("decision", "OPENED"); });
        var replay = store.resolve("tenant-a", "case.open", "key-00000002", request(body),
                () -> { executions.incrementAndGet(); return Map.of("decision", "SHOULD_NOT_RUN"); });
        assertEquals(IdempotencyStore.Outcome.REPLAY, replay.outcome());
        assertEquals(1, executions.get(), "the second call must not re-execute the supplier");
        assertEquals("OPENED", replay.cachedResponse().get("decision"));
    }

    @Test
    void sameKeyDifferentRequestIsAConflictNotASilentReplay() throws Exception {
        IdempotencyStore store = new IdempotencyStore(temp);
        store.resolve("tenant-a", "case.open", "key-00000003", request(Map.of("case_id", "case-1")),
                () -> Map.of("decision", "OPENED"));
        var conflict = store.resolve("tenant-a", "case.open", "key-00000003", request(Map.of("case_id", "case-2")),
                () -> Map.of("decision", "SHOULD_NOT_RUN"));
        assertEquals(IdempotencyStore.Outcome.CONFLICT, conflict.outcome());
    }

    @Test
    void differentTenantsWithTheSameKeyAndOperationDoNotCollide() throws Exception {
        IdempotencyStore store = new IdempotencyStore(temp);
        Map<String, Object> body = Map.of("case_id", "case-1");
        var tenantA = store.resolve("tenant-a", "case.open", "key-00000004", request(body), () -> Map.of("tenant", "a"));
        var tenantB = store.resolve("tenant-b", "case.open", "key-00000004", request(body), () -> Map.of("tenant", "b"));
        assertEquals(IdempotencyStore.Outcome.FIRST_SEEN, tenantA.outcome());
        assertEquals(IdempotencyStore.Outcome.FIRST_SEEN, tenantB.outcome());
        assertEquals("a", tenantA.cachedResponse().get("tenant"));
        assertEquals("b", tenantB.cachedResponse().get("tenant"));
    }

    @Test
    void differentOperationsWithTheSameKeyDoNotCollide() throws Exception {
        IdempotencyStore store = new IdempotencyStore(temp);
        Map<String, Object> body = Map.of("case_id", "case-1");
        var open = store.resolve("tenant-a", "case.open", "key-00000005", request(body), () -> Map.of("op", "open"));
        var close = store.resolve("tenant-a", "case.close", "key-00000005", request(body), () -> Map.of("op", "close"));
        assertEquals(IdempotencyStore.Outcome.FIRST_SEEN, open.outcome());
        assertEquals(IdempotencyStore.Outcome.FIRST_SEEN, close.outcome());
    }

    @Test
    void malformedKeyIsRejected() throws Exception {
        IdempotencyStore store = new IdempotencyStore(temp);
        assertThrows(IllegalArgumentException.class, () -> store.resolve(
                "tenant-a", "case.open", "short", request(Map.of()), () -> Map.of()));
        assertThrows(IllegalArgumentException.class, () -> store.resolve(
                "tenant-a", "case.open", "has a space and $ymbol!", request(Map.of()), () -> Map.of()));
    }

    @Test
    void recordIsPersistedAcrossStoreInstances() throws Exception {
        Map<String, Object> body = Map.of("case_id", "case-1");
        new IdempotencyStore(temp).resolve("tenant-a", "case.open", "key-00000006", request(body), () -> Map.of("decision", "OPENED"));

        IdempotencyStore reopened = new IdempotencyStore(temp);
        AtomicInteger executions = new AtomicInteger();
        var replay = reopened.resolve("tenant-a", "case.open", "key-00000006", request(body),
                () -> { executions.incrementAndGet(); return Map.of("decision", "SHOULD_NOT_RUN"); });
        assertEquals(IdempotencyStore.Outcome.REPLAY, replay.outcome());
        assertEquals(0, executions.get());
        assertTrue(replay.cachedResponse().get("decision").equals("OPENED"));
    }
}
