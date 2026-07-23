package io.onsure.assurance;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public final class ReceiptEnvelopeValidator {
    private static final Pattern SHA256 = Pattern.compile("^[a-f0-9]{64}$");
    private static final Pattern COMMIT_SHA = Pattern.compile("^[a-f0-9]{40}$");
    private static final Set<String> RECEIPT_TYPES = Set.of(
            "SOURCE", "REQUIREMENT", "ARCHITECTURE", "DESIGN", "CODE_REVIEW",
            "SECURITY", "REMEDIATION_PLAN", "PATCH", "OMAKER_PLAN", "OBUILDER_BUILD",
            "RUNTIME", "REGRESSION", "OTESTER", "OAUDIT", "PUBLICATION");
    private final Clock clock;
    private final Duration maximumFutureSkew;

    public ReceiptEnvelopeValidator() { this(Clock.systemUTC(), Duration.ofMinutes(5)); }
    ReceiptEnvelopeValidator(Clock clock, Duration maximumFutureSkew) {
        this.clock = clock;
        this.maximumFutureSkew = maximumFutureSkew;
    }

    public ValidationResult validate(ReceiptEnvelope receipt) {
        if (receipt == null) return ValidationResult.fail(List.of("RECEIPT_MISSING"));
        List<String> v = new ArrayList<>();
        requireText(receipt.receiptId(), "MISSING_RECEIPT_ID", v);
        requireText(receipt.receiptType(), "MISSING_RECEIPT_TYPE", v);
        requireText(receipt.authority(), "MISSING_AUTHORITY", v);
        requireText(receipt.workspaceId(), "MISSING_WORKSPACE_ID", v);
        requireDigest(receipt.policyDigest(), "INVALID_POLICY_DIGEST", v);
        requireText(receipt.permitId(), "MISSING_PERMIT_ID", v);
        requireText(receipt.previousState(), "MISSING_PREVIOUS_STATE", v);
        requireText(receipt.nextState(), "MISSING_NEXT_STATE", v);
        requireDigest(receipt.selfHash(), "INVALID_SELF_HASH", v);
        requireText(receipt.keyId(), "MISSING_KEY_ID", v);
        requireText(receipt.signature(), "MISSING_SIGNATURE", v);
        if (receipt.claims() == null) v.add("MISSING_CLAIMS");
        if (receipt.subjectCommitSha() == null || !COMMIT_SHA.matcher(receipt.subjectCommitSha()).matches()) {
            v.add("MUTABLE_SOURCE_REF");
        }
        if (receipt.receiptType() != null && !RECEIPT_TYPES.contains(receipt.receiptType())) {
            v.add("UNSUPPORTED_RECEIPT_TYPE");
        }
        if (receipt.decision() == null) v.add("MISSING_DECISION");
        if (receipt.issuedAt() == null) v.add("MISSING_ISSUED_AT");
        else if (receipt.issuedAt().isAfter(Instant.now(clock).plus(maximumFutureSkew))) v.add("FUTURE_DATED_RECEIPT");
        validateDigestList(receipt.inputDigests(), "INVALID_INPUT_DIGEST", v);
        validateDigestList(receipt.outputDigests(), "INVALID_OUTPUT_DIGEST", v);
        String type = receipt.receiptType() == null ? "" : receipt.receiptType();
        String authority = receipt.authority() == null ? "" : receipt.authority().toUpperCase();
        if ("OTESTER".equals(type) && !"OTESTER_AGENT".equals(authority)) v.add("INVALID_OTESTER_AUTHORITY");
        if ("OAUDIT".equals(type) && !"OAUDIT_AGENT".equals(authority)) v.add("INVALID_AUDIT_AUTHORITY");
        if (receipt.decision() == Decision.PASS
                && (receipt.inputDigests() == null || receipt.inputDigests().isEmpty()
                || receipt.outputDigests() == null || receipt.outputDigests().isEmpty())) {
            v.add("PASS_WITHOUT_BOUND_EVIDENCE");
        }
        return v.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(v);
    }

    private static void validateDigestList(List<String> values, String code, List<String> v) {
        if (values == null) { v.add(code); return; }
        Set<String> unique = new HashSet<>();
        for (String value : values) {
            if (value == null || !SHA256.matcher(value).matches()) v.add(code);
            else if (!unique.add(value)) v.add("DUPLICATE_EVIDENCE_DIGEST");
        }
    }

    private static void requireDigest(String value, String code, List<String> v) {
        if (value == null || !SHA256.matcher(value).matches()) v.add(code);
    }

    private static void requireText(String value, String code, List<String> v) {
        if (value == null || value.isBlank()) v.add(code);
    }
}
