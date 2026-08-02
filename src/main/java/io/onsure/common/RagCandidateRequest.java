package io.onsure.common;

import java.util.Locale;

/** Product-neutral evidence summary used to prepare an ONSure-owned RAG candidate. */
public record RagCandidateRequest(
        String jobId,
        String reportId,
        String targetId,
        String targetSourceReference,
        String validationDecision,
        int findingCount,
        int failureModeCount,
        int rcaCount,
        boolean nonPassingFixture,
        String sourceReportSha256) {

    public RagCandidateRequest {
        jobId = requireText(jobId, "RAG_JOB_ID_MISSING");
        reportId = requireText(reportId, "RAG_REPORT_ID_MISSING");
        targetId = requireText(targetId, "RAG_TARGET_ID_MISSING");
        targetSourceReference = requireText(
                targetSourceReference, "RAG_TARGET_SOURCE_REFERENCE_MISSING");
        validationDecision = requireText(
                validationDecision, "RAG_VALIDATION_DECISION_MISSING");
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
