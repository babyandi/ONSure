package io.onsure.assurance;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class ReceiptReplayLedger {
    private final Set<String> consumedReceiptIds = ConcurrentHashMap.newKeySet();
    private final Set<String> consumedSelfHashes = ConcurrentHashMap.newKeySet();

    public synchronized ValidationResult consume(ReceiptEnvelope receipt) {
        if (receipt == null || receipt.receiptId() == null || receipt.receiptId().isBlank()) {
            return ValidationResult.fail(List.of("MISSING_RECEIPT_ID"));
        }
        if (receipt.selfHash() == null || receipt.selfHash().isBlank()) {
            return ValidationResult.fail(List.of("MISSING_RECEIPT_SELF_HASH"));
        }
        if (consumedReceiptIds.contains(receipt.receiptId()) || consumedSelfHashes.contains(receipt.selfHash())) {
            return ValidationResult.fail(List.of("RECEIPT_REPLAY"));
        }
        consumedReceiptIds.add(receipt.receiptId());
        consumedSelfHashes.add(receipt.selfHash());
        return ValidationResult.pass();
    }

    public int consumedCount() {
        return consumedReceiptIds.size();
    }
}
