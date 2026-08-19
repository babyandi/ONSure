package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

final class DesignGapDdSemanticEvaluatorsTest {
    @Test
    void successorRegistryMaterializesExact42ButKeepsNewEvaluatorsUnqualified() {
        DdSemanticEvaluatorRegistry registry = DdSemanticEvaluatorRegistry.builtInUnqualified();
        assertEquals(42, registry.registeredCount());
        assertTrue(registry.registered("DD-041").isPresent());
        assertTrue(registry.registered("DD-042").isPresent());
        assertTrue(registry.qualified("DD-041").isEmpty());
        assertTrue(registry.qualified("DD-042").isEmpty());
        assertEquals(0, registry.qualifiedCount());
    }

    @Test
    void successorRuntimeRoutesDd041AndDd042FailClosedBeforeQualification() {
        DdAssuranceOperationRuntime runtime = new DdAssuranceOperationRuntime();
        assertEquals(42, runtime.operations().size());
        assertTrue(runtime.supports("crypto.erasure-completeness.evaluate"));
        assertTrue(runtime.supports("ai-safety.self-referential-claim.evaluate"));
        assertEquals("DD-041", runtime.ddIdFor("crypto.erasure-completeness.evaluate"));
        assertEquals("DD-042", runtime.ddIdFor("ai-safety.self-referential-claim.evaluate"));
    }
}
