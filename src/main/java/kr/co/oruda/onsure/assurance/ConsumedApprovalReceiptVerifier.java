package kr.co.oruda.onsure.assurance;

import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** Revalidates signature, purpose, trust root and prior consumption for an already-used approval. */
public final class ConsumedApprovalReceiptVerifier {
    private ConsumedApprovalReceiptVerifier() {}

    public static void requireTrustedConsumed(
            Path receiptFile,
            Path trustedKeyRegistry,
            Path replayLedger,
            String expectedContract,
            String expectedPurpose,
            Instant now,
            String failurePrefix) {
        ApprovalReceiptVerifier verifier = new ApprovalReceiptVerifier(
                trustedKeyRegistry, replayLedger);
        ValidationResult result = verifier.verify(
                receiptFile, expectedContract, expectedPurpose, now);
        List<String> violations = new ArrayList<>(result.violations());
        boolean consumed = violations.remove("APPROVAL_RECEIPT_REPLAY");
        if (!consumed || !violations.isEmpty()) {
            List<String> details = new ArrayList<>(violations);
            if (!consumed) details.add("APPROVAL_RECEIPT_NOT_CONSUMED");
            throw new IllegalStateException(
                    failurePrefix + ":" + String.join(",", details));
        }
    }
}
