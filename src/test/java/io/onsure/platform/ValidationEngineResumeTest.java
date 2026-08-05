package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationEngineResumeTest {
    @TempDir Path temp;

    @Test
    void resumesFailedStageFromVerifiedBoundaryAndRemovesOnlyNewFiles() throws Exception {
        ValidationTarget target = target();
        AtomicInteger attempts = new AtomicInteger();
        ValidatorStage stage = stage("RECOVERABLE", context -> {
            Path partial = context.runRoot().resolve("partial-stage-output.txt");
            if (attempts.incrementAndGet() == 1) {
                Files.createDirectories(context.runRoot().resolve("partial-directory/nested"));
                Files.writeString(partial, "interrupted");
                throw new IllegalStateException("synthetic interruption");
            }
            assertFalse(Files.exists(partial));
            assertFalse(Files.exists(context.runRoot().resolve("partial-directory")));
            Files.writeString(context.runRoot().resolve("completed-stage-output.txt"), "complete");
            return result("RECOVERABLE");
        });
        Path storeRoot = temp.resolve("runs");
        ValidationEngine engine = new ValidationEngine(
                List.of(new GenericManifestTargetAdapter()), List.of(stage), new FileValidationStore(storeRoot));
        ValidationEngine.ValidationExecutionException failure = assertThrows(
                ValidationEngine.ValidationExecutionException.class, () -> engine.run(target));

        ValidationEngine.RunResult resumed = engine.resume(target, failure.runRoot());

        assertEquals(2, attempts.get());
        assertTrue(Files.isRegularFile(resumed.runRoot().resolve("completed-stage-output.txt")));
        JsonNode checkpoint = new ObjectMapper().readTree(
                resumed.runRoot().resolve(ValidationStageCheckpointJournal.FILE_NAME).toFile());
        assertEquals("STAGES_FINISHED", checkpoint.path("state").asText());
        assertEquals(1, checkpoint.path("resume_count").asInt());
        JsonNode ledger = new ObjectMapper().readTree(
                resumed.runRoot().resolve(ValidationStageReplayLedger.FILE_NAME).toFile());
        assertEquals("ROLLED_BACK_FOR_REPLAY", ledger.path("entries").get(0).path("state").asText());
        assertEquals(2, ledger.path("entries").get(0).path("removed_new_directory_count").asInt());
        assertEquals("COMPLETED", ledger.path("entries").get(1).path("state").asText());
        assertThrows(IllegalStateException.class, () -> engine.resume(target, resumed.runRoot()));
    }

    @Test
    void refusesResumeAfterCheckpointDigestTampering() throws Exception {
        ValidationTarget target = target();
        ValidatorStage stage = stage("FAIL_ONCE", context -> {
            throw new IllegalStateException("synthetic interruption");
        });
        ValidationEngine engine = new ValidationEngine(
                List.of(new GenericManifestTargetAdapter()), List.of(stage),
                new FileValidationStore(temp.resolve("runs-checkpoint-tamper")));
        ValidationEngine.ValidationExecutionException failure = assertThrows(
                ValidationEngine.ValidationExecutionException.class, () -> engine.run(target));
        Path checkpoint = failure.runRoot().resolve(ValidationStageCheckpointJournal.FILE_NAME);
        Files.writeString(checkpoint, Files.readString(checkpoint).replace("STAGE_FAILED", "STAGE_READY"));
        IllegalStateException rejected = assertThrows(
                IllegalStateException.class, () -> engine.resume(target, failure.runRoot()));
        assertEquals("VALIDATION_CHECKPOINT_DIGEST_INVALID", rejected.getMessage());
    }

    @Test
    void refusesResumeAfterReplayLedgerDigestTampering() throws Exception {
        ValidationTarget target = target();
        ValidatorStage stage = stage("FAIL_ONCE", context -> {
            throw new IllegalStateException("synthetic interruption");
        });
        ValidationEngine engine = new ValidationEngine(
                List.of(new GenericManifestTargetAdapter()), List.of(stage),
                new FileValidationStore(temp.resolve("runs-ledger-tamper")));
        ValidationEngine.ValidationExecutionException failure = assertThrows(
                ValidationEngine.ValidationExecutionException.class, () -> engine.run(target));
        Path ledger = failure.runRoot().resolve(ValidationStageReplayLedger.FILE_NAME);
        Files.writeString(ledger, Files.readString(ledger).replace("INTERRUPTED", "STARTED"));

        IllegalStateException rejected = assertThrows(
                IllegalStateException.class, () -> engine.resumeInternal(target, failure.runRoot()));

        assertEquals("STAGE_REPLAY_LEDGER_DIGEST_INVALID", rejected.getMessage());
    }

    @Test
    void refusesReplayWhenInterruptedStageChangedAPreexistingFile() throws Exception {
        ValidationTarget target = target();
        ValidatorStage first = stage("FIRST", context -> {
            Files.writeString(context.runRoot().resolve("owned-by-first.txt"), "sealed");
            return result("FIRST");
        });
        ValidatorStage second = stage("SECOND", context -> {
            Files.writeString(context.runRoot().resolve("owned-by-first.txt"), "tampered");
            throw new IllegalStateException("non-idempotent mutation");
        });
        Path storeRoot = temp.resolve("runs-unsafe");
        ValidationEngine engine = new ValidationEngine(
                List.of(new GenericManifestTargetAdapter()), List.of(first, second), new FileValidationStore(storeRoot));
        ValidationEngine.ValidationExecutionException failure = assertThrows(
                ValidationEngine.ValidationExecutionException.class, () -> engine.run(target));

        IllegalStateException rejected = assertThrows(
                IllegalStateException.class, () -> engine.resumeInternal(target, failure.runRoot()));
        assertTrue(rejected.getMessage().contains("PREEXISTING_FILE_CHANGED"));
    }

    @Test
    void refusesReplayWhenInterruptedStageDeletedAPreexistingFile() throws Exception {
        ValidationTarget target = target();
        ValidatorStage first = stage("FIRST", context -> {
            Files.writeString(context.runRoot().resolve("sealed.txt"), "sealed");
            return result("FIRST");
        });
        ValidatorStage second = stage("SECOND", context -> {
            Files.delete(context.runRoot().resolve("sealed.txt"));
            throw new IllegalStateException("synthetic interruption");
        });
        ValidationEngine engine = new ValidationEngine(
                List.of(new GenericManifestTargetAdapter()), List.of(first, second),
                new FileValidationStore(temp.resolve("runs-deleted")));
        ValidationEngine.ValidationExecutionException failure = assertThrows(
                ValidationEngine.ValidationExecutionException.class, () -> engine.run(target));

        IllegalStateException rejected = assertThrows(
                IllegalStateException.class, () -> engine.resumeInternal(target, failure.runRoot()));
        assertTrue(rejected.getMessage().contains("PREEXISTING_FILE_CHANGED:sealed.txt"));
    }

    @Test
    void refusesReplayWhenInterruptedStageCreatedASymbolicLink() throws Exception {
        ValidationTarget target = target();
        ValidatorStage stage = stage("SYMLINK", context -> {
            Files.createSymbolicLink(context.runRoot().resolve("unsafe-link"), target.sourceRoot());
            throw new IllegalStateException("synthetic interruption");
        });
        Path storeRoot = temp.resolve("runs-symlink");
        ValidationEngine engine = new ValidationEngine(
                List.of(new GenericManifestTargetAdapter()), List.of(stage), new FileValidationStore(storeRoot));
        ValidationEngine.ValidationExecutionException failure = assertThrows(
                ValidationEngine.ValidationExecutionException.class, () -> engine.run(target));

        IllegalStateException rejected = assertThrows(
                IllegalStateException.class, () -> engine.resumeInternal(target, failure.runRoot()));
        assertEquals("STAGE_REPLAY_SYMBOLIC_LINK_FORBIDDEN", rejected.getMessage());
    }

    private ValidationTarget target() throws Exception {
        Path source = temp.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("onsure-target.json"), "{}\n");
        return new ValidationTarget(
                "resume-target", "Resume Target", TargetType.GENERAL_SOFTWARE, source,
                SourceReferenceBinding.treeReference(source), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
    }

    private static ValidatorStage stage(String id, ThrowingExecution execution) {
        return new ValidatorStage() {
            @Override public String stageId() { return id; }
            @Override public boolean supports(ValidationContext context) { return true; }
            @Override public StageResult execute(ValidationContext context) throws Exception {
                return execution.execute(context);
            }
        };
    }

    private static StageResult result(String id) {
        Instant now = Instant.now();
        return new StageResult(id, Decision.PASS, now, now, List.of(), Map.of());
    }

    @FunctionalInterface
    private interface ThrowingExecution {
        StageResult execute(ValidationContext context) throws Exception;
    }
}
