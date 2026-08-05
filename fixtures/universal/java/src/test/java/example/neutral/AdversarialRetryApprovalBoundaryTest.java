package example.neutral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class AdversarialRetryApprovalBoundaryTest {
    @Test void negativePathRejectsInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Calculator.divide(1, 0));
    }

    @Test void retryAndReplayAreDeterministic() {
        assertEquals(Calculator.divide(8, 2), Calculator.divide(8, 2));
    }

    @Test void approvalBoundaryBlocksInvalidInput() {
        assertThrows(IllegalArgumentException.class, () -> Calculator.divide(1, 0));
    }
}
