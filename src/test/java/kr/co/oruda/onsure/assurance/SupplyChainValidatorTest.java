package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SupplyChainValidatorTest {
    private static final String D1 = "a".repeat(64);
    private static final String D2 = "b".repeat(64);
    private final SupplyChainValidator validator = new SupplyChainValidator();

    @Test
    void rejectsUnlockedDependency() {
        assertTrue(validator.validateDependencyLock(false).violations().contains("UNLOCKED_DEPENDENCY"));
    }

    @Test
    void rejectsRegressionDivergence() {
        ValidationResult result = validator.validateRegression("run-1", "run-2", "PASS", "FAIL", D1, D2);
        assertTrue(result.violations().contains("NON_REPRODUCIBLE_REGRESSION"));
    }

    @Test
    void rejectsPolicyDrift() {
        assertTrue(validator.validatePolicyBinding(D1, D2).violations().contains("POLICY_DIGEST_DRIFT"));
    }

    @Test
    void blocksUnsignedUpdateAndMissingRollback() {
        ValidationResult result = validator.validateStandaloneUpdate(false, false);
        assertTrue(result.violations().contains("UNSIGNED_COMPONENT_UPDATE"));
        assertTrue(result.violations().contains("ROLLBACK_EVIDENCE_MISSING"));
    }

    @Test
    void marksImplementationDivergenceInconclusive() {
        ValidationResult result = validator.validateImplementationEquivalence(D1, D2);
        assertEquals(Decision.INCONCLUSIVE, result.decision());
        assertTrue(result.violations().contains("IMPLEMENTATION_EQUIVALENCE_DIVERGENCE"));
    }
}
