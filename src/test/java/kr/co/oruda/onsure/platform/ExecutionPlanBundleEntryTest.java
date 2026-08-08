package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionPlanBundleEntryTest {
    @TempDir Path temp;

    @Test
    void engineRejectsApprovedPlanFileWithoutOriginalReceiptKeyAndReplayLedger() throws Exception {
        Path source = temp.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("README.md"), "target\n");
        Path forgedApprovedPlan = temp.resolve("forged-approved-plan.json");
        Files.writeString(forgedApprovedPlan, "{}\n");
        ValidationModel.ValidationTarget target = new ValidationModel.ValidationTarget(
                "target-001", "Target", ValidationModel.TargetType.GENERAL_SOFTWARE,
                source, "sha256:" + Hashing.tree(source), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "REMOTE_REVIEWED");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> ValidationEngine.defaultEngine(temp.resolve("store"))
                        .run(target, forgedApprovedPlan));
        assertEquals("APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED", failure.getMessage());
    }
}
