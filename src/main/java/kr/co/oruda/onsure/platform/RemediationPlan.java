package kr.co.oruda.onsure.platform;

import java.util.List;

/** Improvement plan generated from a Finding and RCA. */
public record RemediationPlan(
        String planId,
        String findingId,
        ChangeClass changeClass,
        String objective,
        List<String> steps,
        List<String> requiredTests,
        String rollbackPlan) {

    public enum ChangeClass {
        AUTO_ALLOWED,
        APPROVAL_REQUIRED
    }

    public RemediationPlan {
        if (planId == null || planId.isBlank()) throw new IllegalArgumentException("planId");
        if (findingId == null || findingId.isBlank()) throw new IllegalArgumentException("findingId");
        if (changeClass == null) throw new IllegalArgumentException("changeClass");
        if (objective == null || objective.isBlank()) throw new IllegalArgumentException("objective");
        steps = List.copyOf(steps);
        requiredTests = List.copyOf(requiredTests);
        if (rollbackPlan == null || rollbackPlan.isBlank()) throw new IllegalArgumentException("rollbackPlan");
    }
}
