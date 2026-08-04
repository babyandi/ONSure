package io.onsure.assurance;

import java.time.Instant;
import java.util.List;
import java.util.Map;

public record ReceiptEnvelope(
        String receiptId,
        String receiptType,
        String authority,
        String workspaceId,
        String subjectCommitSha,
        String policyDigest,
        String permitId,
        String previousState,
        String nextState,
        Decision decision,
        Instant issuedAt,
        List<String> inputDigests,
        List<String> outputDigests,
        Map<String, Object> claims,
        String keyId,
        String signature,
        String selfHash) {
}
