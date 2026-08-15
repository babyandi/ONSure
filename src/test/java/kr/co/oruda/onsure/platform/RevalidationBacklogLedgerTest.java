package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** LC-P0-011 RevalidationBacklog: a stale decision's reevaluation must be a real, trackable queue item. */
class RevalidationBacklogLedgerTest {
    @TempDir Path temp;

    @Test
    void anEnqueuedItemStartsPending() throws Exception {
        RevalidationBacklogLedger ledger = new RevalidationBacklogLedger(temp);
        var entry = ledger.enqueue("reeval-1", "decision-1");
        assertEquals("PENDING", entry.status());
        assertNull(entry.completedAt());
    }

    @Test
    void anEnqueuedItemAppearsInThePendingList() throws Exception {
        RevalidationBacklogLedger ledger = new RevalidationBacklogLedger(temp);
        ledger.enqueue("reeval-2", "decision-2");
        var pending = ledger.pending();
        assertEquals(1, pending.size());
        assertEquals("reeval-2", pending.get(0).reevaluationRef());
    }

    @Test
    void completingAnItemRemovesItFromThePendingList() throws Exception {
        RevalidationBacklogLedger ledger = new RevalidationBacklogLedger(temp);
        ledger.enqueue("reeval-3", "decision-3");
        ledger.complete("reeval-3");
        assertTrue(ledger.pending().isEmpty());
        assertEquals("COMPLETED", ledger.find("reeval-3").status());
    }

    @Test
    void completingAnAlreadyCompletedItemIsRejected() throws Exception {
        RevalidationBacklogLedger ledger = new RevalidationBacklogLedger(temp);
        ledger.enqueue("reeval-4", "decision-4");
        ledger.complete("reeval-4");
        assertThrows(IllegalArgumentException.class, () -> ledger.complete("reeval-4"));
    }

    @Test
    void completingAnUnknownItemFailsClosed() {
        RevalidationBacklogLedger ledger = new RevalidationBacklogLedger(temp);
        assertThrows(IllegalArgumentException.class, () -> ledger.complete("reeval-never-queued"));
    }

    @Test
    void duplicateEnqueueOfTheSameReevaluationRefIsRejected() throws Exception {
        RevalidationBacklogLedger ledger = new RevalidationBacklogLedger(temp);
        ledger.enqueue("reeval-5", "decision-5");
        assertThrows(IllegalArgumentException.class, () -> ledger.enqueue("reeval-5", "decision-5-again"));
    }

    @Test
    void multiplePendingItemsAreAllVisibleIndependently() throws Exception {
        RevalidationBacklogLedger ledger = new RevalidationBacklogLedger(temp);
        ledger.enqueue("reeval-6", "decision-6");
        ledger.enqueue("reeval-7", "decision-7");
        ledger.complete("reeval-6");
        var pending = ledger.pending();
        assertEquals(1, pending.size());
        assertEquals("reeval-7", pending.get(0).reevaluationRef());
    }
}
