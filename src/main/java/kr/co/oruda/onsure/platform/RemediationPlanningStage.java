package kr.co.oruda.onsure.platform;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.RemediationPlan.ChangeClass;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.Severity;
import kr.co.oruda.onsure.platform.ValidationModel.StageResult;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Converts findings and RCA into bounded improvement and revalidation plans. */
public final class RemediationPlanningStage implements ValidatorStage {
    @Override public String stageId() { return "REMEDIATION_PLANNING"; }
    @Override public boolean supports(ValidationContext context) { return true; }

    @Override
    public StageResult execute(ValidationContext context) {
        Instant start = Instant.now();
        for (Finding finding : context.findings()) {
            ChangeClass changeClass = requiresApproval(finding) ? ChangeClass.APPROVAL_REQUIRED : ChangeClass.AUTO_ALLOWED;
            context.addRemediationPlan(new RemediationPlan(
                    "PLAN-" + finding.fingerprint().substring(0, 16),
                    finding.findingId(),
                    changeClass,
                    "Resolve " + finding.title() + " without changing approved business behavior.",
                    List.of(
                            "Reproduce the finding using its bound evidence and fixture.",
                            "Apply the smallest change that addresses the RCA root cause.",
                            "Preserve or add the failing fixture as a regression fixture.",
                            "Run focused validation and the complete ONSURE pipeline."),
                    List.of("FOCUSED_FIXTURE", "SECURITY_VARIANT", "FULL_REGRESSION", "INDEPENDENT_REVALIDATION"),
                    "Restore the pre-change source lock and re-run the baseline regression lock."));
        }
        return new StageResult(stageId(), Decision.PASS, start, Instant.now(), List.of(),
                Map.of("plans", context.remediationPlans().size(),
                        "approval_required", context.remediationPlans().stream()
                                .filter(value -> value.changeClass() == ChangeClass.APPROVAL_REQUIRED).count()));
    }

    private static boolean requiresApproval(Finding finding) {
        if (finding.severity() == Severity.CRITICAL) return true;
        return finding.category().contains("AUTHORIZATION")
                || finding.category().contains("SELF_APPROVAL")
                || finding.category().contains("DATA_EXFILTRATION");
    }
}
