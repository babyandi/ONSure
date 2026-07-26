package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.platform.BehaviorLearningService.ValidationTargetBundle;
import io.onsure.platform.ValidationModel.Finding;
import io.onsure.platform.ValidationModel.FindingStatus;
import io.onsure.platform.ValidationModel.JobStatus;
import io.onsure.platform.ValidationModel.Severity;
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

class ProductWorkflowServicesTest {
    @TempDir Path temp;

    @Test
    void programAndBehaviorProfilesAreEvidenceBoundCandidates() throws Exception {
        Path source = Path.of("fixtures/e2e/ai-program").toAbsolutePath().normalize();
        Path programFile = temp.resolve("program-profile.json");
        Map<String, Object> program = new ProgramLearningService().learn(
                source, "project-ai", "sample-ai-program", programFile);
        assertEquals("ONSURE_PROGRAM_PROFILE_V1", program.get("contract"));
        assertEquals("PROFILE_CANDIDATE", program.get("state"));
        assertEquals("NOT_RUN", program.get("runtime_verification"));
        assertFalse((Boolean) program.get("final_claim_allowed"));
        assertTrue(Files.isRegularFile(programFile));

        ValidationTarget target = new ValidationTarget(
                "sample-ai-program", "Sample AI", TargetType.AI_APPLICATION, source,
                SourceReferenceBinding.treeReference(source), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_DEVELOPMENT");
        Path behaviorFile = temp.resolve("behavior-profile.json");
        Map<String, Object> behavior = new BehaviorLearningService().learn(
                new ValidationTargetBundle(target, new GenericManifestTargetAdapter()),
                program.get("profile_id").toString(), 2, behaviorFile);
        assertEquals("ONSURE_BEHAVIOR_PROFILE_V1", behavior.get("contract"));
        assertEquals("BEHAVIOR_CANDIDATE", behavior.get("state"));
        assertEquals("NOT_RUN", behavior.get("independent_validation"));
        assertTrue(((List<?>) behavior.get("observations")).size() >= 4);
        assertTrue(Files.isRegularFile(behaviorFile));
    }

    @Test
    void riskPlanAllowsOnlyBoundedLocalDevelopmentScope() throws Exception {
        Path source = Path.of("fixtures/e2e/general-program").toAbsolutePath().normalize();
        Path profileFile = temp.resolve("program-profile.json");
        new ProgramLearningService().learn(source, "project-general", "sample-general-program", profileFile);
        ValidationTarget target = new ValidationTarget(
                "sample-general-program", "Sample General", TargetType.GENERAL_SOFTWARE, source,
                SourceReferenceBinding.treeReference(source), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_DEVELOPMENT");
        Path planFile = temp.resolve("execution-plan.json");
        Map<String, Object> plan = new ExecutionPlanService().plan(target, profileFile, 2, planFile);
        Map<?, ?> approval = (Map<?, ?>) plan.get("approval");
        Map<?, ?> permissions = (Map<?, ?>) plan.get("permissions");
        assertEquals("AUTO_APPROVED_DEVELOPMENT_NONFINAL", approval.get("state"));
        assertEquals(false, permissions.get("modify_target"));
        assertEquals(false, permissions.get("git_push"));
        assertEquals(false, permissions.get("merge"));
        new ExecutionPlanService().requireApproved(plan);
    }

    @Test
    void patchPlanRequiresApprovalAndDoesNotModifySource() throws Exception {
        Path source = temp.resolve("target");
        Files.createDirectories(source);
        Path file = source.resolve("agent.txt");
        Files.writeString(file, "policy=SELF_APPROVE\n");
        Files.writeString(source.resolve("onsure-target.json"), """
                {
                  "contract":"ONSURE_TARGET_MANIFEST_V1",
                  "target_id":"patch-target",
                  "target_type":"GENERAL_SOFTWARE",
                  "self_reported_final_decision":false,
                  "capabilities":[],
                  "fixtures":[]
                }
                """);
        ValidationTarget target = new ValidationTarget(
                "patch-target", "Patch Target", TargetType.GENERAL_SOFTWARE, source,
                SourceReferenceBinding.treeReference(source), GenericManifestTargetAdapter.ID,
                "policy", "LOCAL_DEVELOPMENT");
        ValidationJob job = new ValidationJob(
                "job-patch-001", "patch-target", JobStatus.RUNNING,
                Instant.now(), Instant.now(), null, null);
        ValidationContext context = new ValidationContext(
                target, job, new GenericManifestTargetAdapter(), temp.resolve("run"));
        Files.createDirectories(context.runRoot());
        context.putAttribute("source_tree_sha256", Hashing.tree(source));
        context.putAttribute("review_id", "review-patch-001");
        context.putAttribute("evidence_based_rca_sha256", "a".repeat(64));
        String fingerprint = Hashing.sha256("finding-patch-001");
        context.addFinding(new Finding(
                "finding-patch-001", "STATIC_ANALYSIS", Severity.HIGH, "AI_SELF_APPROVAL",
                "Self approval marker", "Self approval marker", "agent.txt",
                FindingStatus.OPEN, List.of("EV-SOURCE"), fingerprint));

        String original = Files.readString(file);
        Path planFile = context.runRoot().resolve("patch-plan.json");
        Map<String, Object> plan = new ImprovementWorkflowService().createPatchPlan(context, planFile);
        assertEquals("AWAITING_HUNK_APPROVAL", plan.get("execution_state"));
        assertEquals(1, ((List<?>) plan.get("hunks")).size());
        assertEquals(original, Files.readString(file));
        assertEquals(false, plan.get("direct_main_write_allowed"));
        assertEquals(false, plan.get("merge_allowed"));
    }
}
