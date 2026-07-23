package io.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class StateTransitionValidatorTest {
    private final StateTransitionValidator validator = new StateTransitionValidator();

    @Test void acceptsValidImmediateTransition() {
        assertEquals(Decision.PASS, validator.validate(context("UNINITIALIZED", "SOURCE_LOCKED", false, "runtime-key", "otester-key", "oaudit-key", null, null, Decision.PASS)).decision());
    }
    @Test void rejectsStageSkipping() {
        assertTrue(validator.validate(context("UNINITIALIZED", "SECURITY_REVIEWED", false, "runtime-key", "otester-key", "oaudit-key", null, null, Decision.PASS)).violations().contains("STAGE_SKIP"));
    }
    @Test void rejectsSharedSigningKeysWithoutThrowing() {
        ValidationResult r = validator.validate(context("TESTED", "OTESTER_VERIFIED", false, "shared-key", "shared-key", "oaudit-key", "run-1", "run-2", Decision.PASS));
        assertEquals(Decision.FAIL, r.decision());
        assertTrue(r.violations().contains("INDEPENDENCE_KEY_COLLISION"));
    }
    @Test void rejectsOpenHighSecurityFinding() {
        assertTrue(validator.validate(context("CODE_REVIEWED", "SECURITY_REVIEWED", true, "runtime-key", "otester-key", "oaudit-key", null, null, Decision.PASS)).violations().contains("OPEN_BLOCKING_SECURITY_FINDING"));
    }
    @Test void rejectsReusedRegressionRunId() {
        assertTrue(validator.validate(context("OBUILDER_BUILT", "TESTED", false, "runtime-key", "otester-key", "oaudit-key", "same-run", "same-run", Decision.PASS)).violations().contains("REGRESSION_NOT_INDEPENDENT"));
    }
    @Test void rejectsNotRunClaimAdvancement() {
        assertTrue(validator.validate(context("PATCHED", "OBUILDER_BUILT", false, "runtime-key", "otester-key", "oaudit-key", null, null, Decision.NOT_RUN)).violations().contains("NOT_RUN_CANNOT_PASS"));
    }

    private static TransitionContext context(String from, String to, boolean securityOpen, String runtimeKey, String otesterKey, String oauditKey, String run1, String run2, Decision decision) {
        return new TransitionContext(from, to, Set.of("input-receipt"), Set.of("output-receipt"), securityOpen, runtimeKey, otesterKey, oauditKey, run1, run2, decision);
    }
}
