package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.Evidence;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import java.nio.file.Files;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Same-process, nonfinal lineage audit. This is not an independent external attestation. */
public final class IndependentProductAuditStage implements ValidatorStage {
    @Override public String stageId() { return "INTERNAL_PRODUCT_AUDIT"; }
    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) throws Exception {
        Instant start = Instant.now();
        ValidationCompletionGate.requireRuntimeCoverage(context, "INDEPENDENT_AUDIT");
        Set<String> evidenceIds = new HashSet<>();
        for (Evidence evidence : context.evidence()) {
            if (!evidenceIds.add(evidence.evidenceId())) {
                throw new IllegalStateException("DUPLICATE_EVIDENCE_ID: " + evidence.evidenceId());
            }
        }
        Set<String> findingIds = new HashSet<>();
        for (Finding finding : context.findings()) {
            if (!findingIds.add(finding.findingId())) {
                throw new IllegalStateException("DUPLICATE_FINDING_ID: " + finding.findingId());
            }
            if (finding.evidenceIds().isEmpty() || !evidenceIds.containsAll(finding.evidenceIds())) {
                throw new IllegalStateException("FINDING_EVIDENCE_MISSING: " + finding.findingId());
            }
            long modes = context.failureModes().stream()
                    .filter(value -> value.findingIds().contains(finding.findingId())).count();
            long rca = context.rcaRecords().stream()
                    .filter(value -> value.findingId().equals(finding.findingId())).count();
            long plans = context.remediationPlans().stream()
                    .filter(value -> value.findingId().equals(finding.findingId())).count();
            if (modes != 1 || rca != 1 || plans != 1) {
                throw new IllegalStateException("FINDING_LINEAGE_INCOMPLETE: " + finding.findingId());
            }
        }
        for (String required : List.of("fixture-registry.json", "oracle-registry.json")) {
            if (!Files.isRegularFile(context.runRoot().resolve(required))) {
                throw new IllegalStateException("AUDIT_REQUIRED_EVIDENCE_MISSING: " + required);
            }
        }
        if (context.regressionLock() == null) throw new IllegalStateException("AUDIT_REGRESSION_LOCK_MISSING");
        ProductReceiptWriter.verify(
                context.runRoot().resolve("internal-verifier-receipt.json"),
                "ONSURE_INTERNAL_VERIFIER_RECEIPT_V1", "ONSURE_INTERNAL_VERIFIER",
                context.job().jobId());
        ProductReceiptWriter.write(
                context.runRoot().resolve("internal-audit-receipt.json"),
                "ONSURE_INTERNAL_AUDIT_RECEIPT_V1", "ONSURE_INTERNAL_AUDIT",
                context.job().jobId(), Map.of(
                        "assurance_class", "INTERNAL_NONFINAL",
                        "execution_context", "SAME_PROCESS_MUTABLE_CONTEXT",
                        "evidence_count", context.evidence().size(),
                        "finding_count", context.findings().size(),
                        "failure_mode_count", context.failureModes().size(),
                        "rca_count", context.rcaRecords().size(),
                        "remediation_plan_count", context.remediationPlans().size(),
                        "regression_lock_digest", context.regressionLock().lockDigest()));
        return new StageResult(stageId(), Decision.PASS, start, Instant.now(), List.of(),
                Map.of("audited_findings", context.findings().size(),
                        "audited_evidence", context.evidence().size()));
    }
}
