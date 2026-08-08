package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.platform.ValidationModel.JobStatus;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationJob;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecutionPlanActionPolicyTest {
    @TempDir Path temp;

    @Test
    void partialApprovalAuthorizesOnlyMappedStages() {
        Instant now = Instant.now();
        ValidationTarget target = new ValidationTarget(
                "target-001", "Target", TargetType.GENERAL_SOFTWARE, temp,
                "sha256:" + "a".repeat(64), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "REMOTE_REVIEWED");
        ValidationJob job = new ValidationJob(
                "job-001", target.targetId(), JobStatus.RUNNING, now, now, null, null);
        ValidationContext context = new ValidationContext(
                target, job, new GenericManifestTargetAdapter(), temp.resolve("run"));
        context.putAttribute("execution_plan_approved_actions", List.of("STATIC_ANALYSIS", "REVIEW"));

        assertTrue(ExecutionPlanActionPolicy.isApproved(context, "STATIC_ANALYSIS"));
        assertTrue(ExecutionPlanActionPolicy.isApproved(context, "REVIEW"));
        assertFalse(ExecutionPlanActionPolicy.isApproved(context, "FIXTURE_EXECUTION"));
        assertEquals("FIXTURE_EXECUTION",
                ExecutionPlanActionPolicy.requiredAction("FIXTURE_HARNESS_ORACLE"));
        assertEquals(Decision.HOLD,
                ExecutionPlanActionPolicy.notApproved("FIXTURE_HARNESS_ORACLE", "FIXTURE_EXECUTION")
                        .decision());
    }
}
