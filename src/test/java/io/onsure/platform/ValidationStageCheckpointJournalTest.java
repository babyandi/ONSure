package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationStageCheckpointJournalTest {
    @TempDir Path temp;

    @Test
    void sealsEveryCooperativeStageBoundaryAndFinalNonfinalDecision() throws Exception {
        ValidationStageCheckpointJournal journal = new ValidationStageCheckpointJournal(
                temp, "job-001", "target-001", List.of("INVENTORY", "VERIFY"));
        journal.stageStarted("INVENTORY", 0);
        journal.stageCompleted("INVENTORY", "PASS");
        journal.stageStarted("VERIFY", 1);
        journal.stageCompleted("VERIFY", "HOLD");
        journal.stagesFinished("HOLD");

        Map<String, Object> checkpoint = journal.verifyAndRead();
        assertEquals("STAGES_FINISHED", checkpoint.get("state"));
        assertEquals("HOLD", checkpoint.get("validation_decision"));
        assertEquals(List.of("INVENTORY", "VERIFY"), checkpoint.get("completed_stage_ids"));
        assertEquals(5L, ((Number) checkpoint.get("sequence")).longValue());
        assertEquals(6, ((List<?>) checkpoint.get("history")).size());
        assertEquals(true, checkpoint.get("context_replay_supported"));
        assertEquals(true, checkpoint.get("automatic_engine_resume_supported"));
        assertEquals(ValidationContextSnapshotStore.FILE_NAME, checkpoint.get("context_snapshot_file"));
        assertEquals(ValidationStageReplayLedger.FILE_NAME, checkpoint.get("stage_replay_ledger_file"));
        assertEquals(false, checkpoint.get("final_claim_allowed"));
    }

    @Test
    void rejectsOutOfOrderAndTamperedCheckpointState() throws Exception {
        ValidationStageCheckpointJournal journal = new ValidationStageCheckpointJournal(
                temp, "job-002", "target-002", List.of("FIRST", "SECOND"));
        assertThrows(IllegalStateException.class, () -> journal.stageStarted("SECOND", 1));
        journal.stageStarted("FIRST", 0);
        journal.stageFailed("FIRST", new IllegalStateException("bounded failure"));
        journal.stagesFinished("FAIL");
        assertEquals("FAIL", journal.verifyAndRead().get("validation_decision"));

        Path file = temp.resolve(ValidationStageCheckpointJournal.FILE_NAME);
        String changed = Files.readString(file).replace("bounded failure", "forged failure");
        Files.writeString(file, changed);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, journal::verifyAndRead);
        assertTrue(failure.getMessage().contains("DIGEST_INVALID"));
        assertFalse(changed.isBlank());
    }
}
