package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DesignBaselineRuntimeTest {
    @Test
    void recalculatesNormativeAuthorities() throws Exception {
        var result = DesignBaselineRuntime.verify(Path.of("."));
        assertEquals(13, result.documentCount());
        assertEquals(22, result.requirementCount());
        assertEquals(62, result.acceptanceCount());
        assertEquals("SELF_VALIDATION_NONFINAL", result.decisionCeiling());
        assertFalse(result.finalClaimAllowed());
    }
}

