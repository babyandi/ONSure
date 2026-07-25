package io.onsure.rag;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProgramLearningOrchestratorTest {
    @TempDir Path temp;
    private static final String SHA = "a".repeat(64);
    private static final String REF = "b".repeat(40);

    @Test
    void automaticLearningIsDisabledByDefault() throws Exception {
        Path target = environment(false);
        assertThrows(IllegalStateException.class, () -> new ProgramLearningOrchestrator()
                .createApprovedRequest(target, "C-1", REF, SHA, true, true, SHA));
    }

    @Test
    void approvalDataReviewImmutableSourceAndRollbackAreMandatory() throws Exception {
        Path target = environment(true);
        ProgramLearningOrchestrator service = new ProgramLearningOrchestrator();
        assertThrows(IllegalStateException.class,
                () -> service.createApprovedRequest(target, "C-1", REF, SHA, false, true, SHA));
        assertThrows(IllegalStateException.class,
                () -> service.createApprovedRequest(target, "C-1", REF, SHA, true, false, SHA));
        assertThrows(IllegalArgumentException.class,
                () -> service.createApprovedRequest(target, "C-1", "main", SHA, true, true, SHA));
        assertThrows(IllegalArgumentException.class,
                () -> service.createApprovedRequest(target, "C-1", REF, SHA, true, true, "bad"));
    }

    @Test
    void approvedValidatedCycleReachesPostValidatedButNotFinalLock() throws Exception {
        Path target = environment(true);
        ProgramLearningOrchestrator service = new ProgramLearningOrchestrator();
        Map<String, Object> request =
                service.createApprovedRequest(target, "C-1", REF, SHA, true, true, SHA);
        Map<String, Object> receipt = service.recordValidatedApplication(
                target, request, SHA, SHA, SHA, SHA, true, true);
        assertEquals("POST_VALIDATED", receipt.get("state"));
        assertEquals(true, receipt.get("actual_learning_performed"));
        assertFalse((Boolean) receipt.get("final_lock_allowed"));
    }

    @Test
    void validationOrPostValidationFailureCannotBeApplied() throws Exception {
        Path target = environment(true);
        ProgramLearningOrchestrator service = new ProgramLearningOrchestrator();
        Map<String, Object> request =
                service.createApprovedRequest(target, "C-1", REF, SHA, true, true, SHA);
        assertThrows(IllegalStateException.class, () -> service.recordValidatedApplication(
                target, request, SHA, SHA, SHA, SHA, false, true));
        assertThrows(IllegalStateException.class, () -> service.recordValidatedApplication(
                target, request, SHA, SHA, SHA, SHA, true, false));
    }

    private Path environment(boolean enabled) throws Exception {
        Path target = Files.createDirectories(temp.resolve(enabled ? "enabled" : "disabled"));
        Path root = target.resolve(RagPreparationService.TARGET_ENVIRONMENT);
        Files.createDirectories(root.resolve("learning"));
        Files.writeString(root.resolve("manifest.json"), "{}");
        Files.writeString(root.resolve("learning/profile.json"), "{}");
        new ObjectMapper().writeValue(root.resolve("learning/policy.json").toFile(), Map.of(
                "automatic_learning_enabled", enabled));
        return target;
    }
}
