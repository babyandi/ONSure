package kr.co.oruda.onsure.platform;

import java.util.ArrayList;
import java.util.List;

/**
 * Fail-closed pre-execution budget check: an action never proceeds once its projected token
 * count, cost, or data-transfer scope exceeds what was declared and approved in its
 * {@link ExecutionBudget} (NFR-06: execution budget visibility).
 */
public final class ExecutionBudgetGuard {

    public record ProjectedUsage(
            int projectedTokens, long projectedCostMicros, ExecutionBudget.DataTransferScope projectedScope) {
        public ProjectedUsage {
            if (projectedTokens < 0) throw new IllegalArgumentException("projectedTokens");
            if (projectedCostMicros < 0) throw new IllegalArgumentException("projectedCostMicros");
            if (projectedScope == null) throw new IllegalArgumentException("projectedScope");
        }
    }

    public record BudgetCheckResult(boolean withinBudget, List<String> violations) {
        public BudgetCheckResult {
            violations = List.copyOf(violations);
        }
    }

    public static final class BudgetExceededException extends RuntimeException {
        private final List<String> violations;

        BudgetExceededException(List<String> violations) {
            super("EXECUTION_BUDGET_EXCEEDED: " + String.join(",", violations));
            this.violations = List.copyOf(violations);
        }

        public List<String> violations() { return violations; }
    }

    private ExecutionBudgetGuard() {}

    public static BudgetCheckResult check(ExecutionBudget budget, ProjectedUsage projected) {
        if (budget == null) throw new IllegalArgumentException("budget");
        if (projected == null) throw new IllegalArgumentException("projected");

        List<String> violations = new ArrayList<>();
        if (projected.projectedTokens() > budget.tokenEstimate()) {
            violations.add("TOKEN_ESTIMATE_EXCEEDED:" + projected.projectedTokens() + ">" + budget.tokenEstimate());
        }
        if (projected.projectedCostMicros() > budget.costCeilingMicros()) {
            violations.add("COST_CEILING_EXCEEDED:" + projected.projectedCostMicros() + ">" + budget.costCeilingMicros());
        }
        if (projected.projectedScope().ordinal() > budget.dataTransferScope().ordinal()) {
            violations.add("DATA_TRANSFER_SCOPE_EXCEEDED:" + projected.projectedScope() + ">" + budget.dataTransferScope());
        }
        return new BudgetCheckResult(violations.isEmpty(), violations);
    }

    /** Same check as {@link #check}, but fails closed by throwing instead of returning a result. */
    public static void requireWithinBudget(ExecutionBudget budget, ProjectedUsage projected) {
        BudgetCheckResult result = check(budget, projected);
        if (!result.withinBudget()) throw new BudgetExceededException(result.violations());
    }
}
