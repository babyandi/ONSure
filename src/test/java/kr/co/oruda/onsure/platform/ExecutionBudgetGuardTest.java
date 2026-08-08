package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.platform.ExecutionBudget.DataTransferScope;
import kr.co.oruda.onsure.platform.ExecutionBudgetGuard.BudgetExceededException;
import kr.co.oruda.onsure.platform.ExecutionBudgetGuard.ProjectedUsage;
import org.junit.jupiter.api.Test;

class ExecutionBudgetGuardTest {

    private static ExecutionBudget budget(int tokens, long costMicros, DataTransferScope scope) {
        return new ExecutionBudget("Program Profile candidate for target repository", tokens, costMicros, scope);
    }

    @Test
    void usageWithinAllThreeLimitsPasses() {
        var result = ExecutionBudgetGuard.check(
                budget(1000, 5000, DataTransferScope.EXTERNAL_ALLOWLISTED),
                new ProjectedUsage(900, 4000, DataTransferScope.LOCAL_ONLY));
        assertTrue(result.withinBudget());
        assertTrue(result.violations().isEmpty());
    }

    @Test
    void tokenOverrunIsRejected() {
        var result = ExecutionBudgetGuard.check(
                budget(1000, 5000, DataTransferScope.LOCAL_ONLY),
                new ProjectedUsage(1500, 100, DataTransferScope.LOCAL_ONLY));
        assertFalse(result.withinBudget());
        assertEquals(1, result.violations().size());
        assertTrue(result.violations().get(0).startsWith("TOKEN_ESTIMATE_EXCEEDED:1500>1000"));
    }

    @Test
    void costCeilingOverrunIsRejected() {
        var result = ExecutionBudgetGuard.check(
                budget(1000, 5000, DataTransferScope.LOCAL_ONLY),
                new ProjectedUsage(100, 6000, DataTransferScope.LOCAL_ONLY));
        assertFalse(result.withinBudget());
        assertTrue(result.violations().get(0).startsWith("COST_CEILING_EXCEEDED:6000>5000"));
    }

    @Test
    void moreDataTransferThanBudgetedIsRejectedEvenIfCheaperAndFasterThanExpected() {
        var result = ExecutionBudgetGuard.check(
                budget(1000, 5000, DataTransferScope.LOCAL_ONLY),
                new ProjectedUsage(10, 1, DataTransferScope.EXTERNAL_UNRESTRICTED));
        assertFalse(result.withinBudget());
        assertTrue(result.violations().get(0)
                .startsWith("DATA_TRANSFER_SCOPE_EXCEEDED:EXTERNAL_UNRESTRICTED>LOCAL_ONLY"));
    }

    @Test
    void multipleViolationsAreAllReported() {
        var result = ExecutionBudgetGuard.check(
                budget(10, 10, DataTransferScope.LOCAL_ONLY),
                new ProjectedUsage(1000, 1000, DataTransferScope.EXTERNAL_UNRESTRICTED));
        assertEquals(3, result.violations().size());
    }

    @Test
    void requireWithinBudgetFailsClosedByThrowing() {
        BudgetExceededException thrown = assertThrows(BudgetExceededException.class, () ->
                ExecutionBudgetGuard.requireWithinBudget(
                        budget(10, 10, DataTransferScope.LOCAL_ONLY),
                        new ProjectedUsage(50, 10, DataTransferScope.LOCAL_ONLY)));
        assertTrue(thrown.violations().get(0).contains("TOKEN_ESTIMATE_EXCEEDED"));
    }

    @Test
    void budgetRejectsBlankExpectedResult() {
        assertThrows(IllegalArgumentException.class, () ->
                new ExecutionBudget("", 10, 10, DataTransferScope.LOCAL_ONLY));
    }
}
