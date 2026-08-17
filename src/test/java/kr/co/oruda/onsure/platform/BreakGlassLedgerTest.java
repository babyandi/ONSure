package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BreakGlassLedgerTest {
    @TempDir Path temp;
    private final Instant now = Instant.parse("2026-08-14T00:00:00Z");

    @Test
    void everyInvocationStartsAsRequiringReviewAndNotYetReviewed() throws Exception {
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        var event = ledger.invoke("bg-1", "actor-a", "production outage", now);
        assertTrue(event.reviewRequired());
        assertTrue(!event.reviewCompleted());
    }

    @Test
    void justificationIsMandatory() {
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        assertThrows(IllegalArgumentException.class, () -> ledger.invoke("bg-2", "actor-a", "", now));
    }

    @Test
    void theInvokerCannotReviewTheirOwnEvent() throws Exception {
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        ledger.invoke("bg-3", "actor-a", "production outage", now);
        SecurityException denied = assertThrows(SecurityException.class,
                () -> ledger.recordReview("bg-3", "actor-a", "looks fine", now.plusSeconds(60)));
        assertEquals("BREAK_GLASS_REVIEWER_CANNOT_BE_THE_INVOKER", denied.getMessage());
    }

    @Test
    void aDistinctReviewerClosesTheEvent() throws Exception {
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        ledger.invoke("bg-4", "actor-a", "production outage", now);
        var reviewed = ledger.recordReview("bg-4", "actor-b", "confirmed necessary", now.plusSeconds(60));
        assertTrue(reviewed.reviewCompleted());
        assertEquals("actor-b", reviewed.reviewerActorId());
    }

    @Test
    void anAlreadyReviewedEventCannotBeReviewedAgain() throws Exception {
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        ledger.invoke("bg-5", "actor-a", "production outage", now);
        ledger.recordReview("bg-5", "actor-b", "confirmed necessary", now.plusSeconds(60));
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordReview("bg-5", "actor-c", "second look", now.plusSeconds(120)));
    }

    @Test
    void reviewingAnUnknownEventFailsClosed() {
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        assertThrows(IllegalArgumentException.class,
                () -> ledger.recordReview("bg-never-invoked", "actor-b", "n/a", now));
    }

    @Test
    void theOriginalInvokedRecordSurvivesUnchangedAfterReview() throws Exception {
        // Autonomous Development Mode (2026-08-15) tamper-evidence hardening changed storage from
        // a single mutable record to two hash-chained append-only records -- guard that the
        // pre-review facts (invoker, justification, invoked_at) are still exactly reconstructible
        // after review, not merged away or lost the way the old overwrite-in-place design would.
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        var invoked = ledger.invoke("bg-6", "actor-a", "production outage", now);
        var reviewed = ledger.recordReview("bg-6", "actor-b", "confirmed necessary", now.plusSeconds(60));
        assertEquals(invoked.invokerActorId(), reviewed.invokerActorId());
        assertEquals(invoked.justification(), reviewed.justification());
        assertEquals(invoked.invokedAt(), reviewed.invokedAt());
    }

    // Autonomous Development Mode (2026-08-15) tamper-evidence hardening.
    @Test
    void aTamperedJustificationIsDetectedOnNextRead() throws Exception {
        BreakGlassLedger ledger = new BreakGlassLedger(temp);
        ledger.invoke("bg-tamper-1", "actor-a", "production outage", now);
        Path file = temp.resolve("bg-tamper-1.json");
        String tampered = Files.readString(file).replace("production outage", "fabricated justification");
        Files.writeString(file, tampered);
        IllegalStateException detected = assertThrows(IllegalStateException.class,
                () -> ledger.get("bg-tamper-1"));
        assertTrue(detected.getMessage().contains("BREAK_GLASS_LEDGER_CHAIN_INVALID"));
    }
}
