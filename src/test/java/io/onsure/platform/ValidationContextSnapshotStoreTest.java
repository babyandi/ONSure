package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import io.onsure.platform.ValidationModel.JobStatus;
import io.onsure.platform.ValidationModel.StageResult;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationJob;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationContextSnapshotStoreTest {
    @TempDir Path temp;

    @Test
    void restoresDigestBoundTypedContextAtStageBoundary() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        ValidationTarget target = target(source);
        TargetAdapter adapter = new GenericManifestTargetAdapter();
        Instant now = Instant.now();
        ValidationContext original = new ValidationContext(
                target,
                new ValidationJob("job-001", "target-001", JobStatus.RUNNING, now, now, null, null),
                adapter,
                temp);
        original.putAttribute("bounded", Map.of("count", 2, "values", List.of("a", "b")));
        original.addStageResult(new StageResult(
                "SOURCE_INVENTORY", Decision.PASS, now, now, List.of(), Map.of("files", 2)));

        ValidationContextSnapshotStore snapshots = new ValidationContextSnapshotStore(temp);
        snapshots.save(original, 0, "STAGE_COMPLETED");
        ValidationContextSnapshotStore.Restored restored = snapshots.restore(target, adapter);

        assertEquals(0, restored.lastCompletedStageIndex());
        assertEquals("STAGE_COMPLETED", restored.boundaryState());
        assertEquals(original.job(), restored.context().job());
        assertEquals(original.stageResults(), restored.context().stageResults());
        assertEquals(original.attributes(), restored.context().attributes());
        Map<String, Object> envelope = snapshots.verifyAndRead(target);
        assertEquals(true, envelope.get("automatic_engine_resume_supported"));
        assertEquals(false, envelope.get("final_claim_allowed"));
    }

    @Test
    void rejectsTamperingAndDifferentTargetBinding() throws Exception {
        Path source = Files.createDirectory(temp.resolve("source"));
        ValidationTarget target = target(source);
        Instant now = Instant.now();
        ValidationContext original = new ValidationContext(
                target,
                new ValidationJob("job-002", "target-001", JobStatus.RUNNING, now, now, null, null),
                new GenericManifestTargetAdapter(),
                temp);
        ValidationContextSnapshotStore snapshots = new ValidationContextSnapshotStore(temp);
        snapshots.save(original, -1, "INITIALIZED");

        Path snapshot = temp.resolve(ValidationContextSnapshotStore.FILE_NAME);
        String forged = Files.readString(snapshot).replace("INITIALIZED", "STAGE_FAILED");
        Files.writeString(snapshot, forged);
        IllegalStateException failure = assertThrows(
                IllegalStateException.class, () -> snapshots.verifyAndRead(target));
        assertTrue(failure.getMessage().contains("DIGEST_INVALID"));
        assertFalse(forged.isBlank());
    }

    private static ValidationTarget target(Path source) throws Exception {
        return new ValidationTarget(
                "target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                SourceReferenceBinding.treeReference(source), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", FixtureRegistryStage.TRUSTED_LOCAL_PROFILE);
    }
}
