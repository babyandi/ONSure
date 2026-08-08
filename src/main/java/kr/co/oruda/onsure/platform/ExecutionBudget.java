package kr.co.oruda.onsure.platform;

import java.util.Objects;

/**
 * Pre-execution budget declaration (NFR-06: execution cost/token/data-transfer-scope visibility;
 * FR-04-B: execution preview must show expected result, required permission, time and cost before
 * running). Every plannable action must declare one of these before it is approved for execution.
 */
public record ExecutionBudget(
        String expectedResult,
        int tokenEstimate,
        long costCeilingMicros,
        DataTransferScope dataTransferScope) {

    /** Ordered from least to most permissive; a projection may not exceed the budgeted scope. */
    public enum DataTransferScope { LOCAL_ONLY, EXTERNAL_ALLOWLISTED, EXTERNAL_UNRESTRICTED }

    public ExecutionBudget {
        if (expectedResult == null || expectedResult.isBlank()) throw new IllegalArgumentException("expectedResult");
        if (tokenEstimate < 0) throw new IllegalArgumentException("tokenEstimate");
        if (costCeilingMicros < 0) throw new IllegalArgumentException("costCeilingMicros");
        Objects.requireNonNull(dataTransferScope, "dataTransferScope");
    }
}
