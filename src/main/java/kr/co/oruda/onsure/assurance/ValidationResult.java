package kr.co.oruda.onsure.assurance;

import java.util.List;

public record ValidationResult(Decision decision, List<String> violations) {
    public ValidationResult {
        violations = List.copyOf(violations);
        if (decision == Decision.PASS && !violations.isEmpty()) {
            throw new IllegalArgumentException("PASS cannot contain violations");
        }
    }

    public static ValidationResult pass() {
        return new ValidationResult(Decision.PASS, List.of());
    }

    public static ValidationResult fail(List<String> violations) {
        return new ValidationResult(Decision.FAIL, violations);
    }
}
