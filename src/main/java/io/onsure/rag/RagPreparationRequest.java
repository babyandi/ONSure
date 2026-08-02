package io.onsure.rag;

import java.nio.file.Path;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * Platform-neutral input contract for RAG candidate preparation and target bootstrap decisions.
 *
 * <p>The source report digest must bind the exact canonical report bytes chosen by the caller.
 */
public record RagPreparationRequest(
        String jobId,
        String reportId,
        String targetId,
        String targetName,
        String targetType,
        Path targetSourceRoot,
        String targetSourceReference,
        String validationDecision,
        int findingCount,
        int failureModeCount,
        int rcaCount,
        boolean nonPassingFixture,
        String sourceReportSha256) {
    private static final Set<String> TARGET_TYPES = Set.of(
            "GENERAL_SOFTWARE", "AI_APPLICATION", "AI_AGENTIC_PLATFORM");
    private static final Set<String> VALIDATION_DECISIONS = Set.of(
            "PASS", "FAIL", "HOLD", "BLOCKED", "NOT_RUN", "INCONCLUSIVE");

    public RagPreparationRequest {
        jobId = requireText(jobId, "RAG_JOB_ID_MISSING");
        reportId = requireText(reportId, "RAG_REPORT_ID_MISSING");
        targetId = requireText(targetId, "RAG_TARGET_ID_MISSING");
        targetName = requireText(targetName, "RAG_TARGET_NAME_MISSING");
        targetType = requireText(targetType, "RAG_TARGET_TYPE_MISSING");
        if (!TARGET_TYPES.contains(targetType)) {
            throw new IllegalArgumentException("RAG_TARGET_TYPE_INVALID");
        }
        targetSourceRoot = Objects.requireNonNull(
                targetSourceRoot, "RAG_TARGET_SOURCE_ROOT_MISSING").toAbsolutePath().normalize();
        targetSourceReference = requireText(
                targetSourceReference, "RAG_TARGET_SOURCE_REFERENCE_MISSING");
        validationDecision = requireText(
                validationDecision, "RAG_VALIDATION_DECISION_MISSING");
        if (!VALIDATION_DECISIONS.contains(validationDecision)) {
            throw new IllegalArgumentException("RAG_VALIDATION_DECISION_INVALID");
        }
        if (findingCount < 0 || failureModeCount < 0 || rcaCount < 0) {
            throw new IllegalArgumentException("RAG_NEGATIVE_EVIDENCE_COUNT");
        }
        sourceReportSha256 = requireText(
                sourceReportSha256, "RAG_SOURCE_REPORT_DIGEST_MISSING")
                .toLowerCase(Locale.ROOT);
        if (!sourceReportSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("RAG_SOURCE_REPORT_DIGEST_INVALID");
        }
    }

    private static String requireText(String value, String error) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(error);
        return value;
    }
}
