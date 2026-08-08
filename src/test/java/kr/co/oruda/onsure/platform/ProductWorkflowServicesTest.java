package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import kr.co.oruda.onsure.platform.BehaviorLearningService.ValidationTargetBundle;
import kr.co.oruda.onsure.platform.ValidationModel.Finding;
import kr.co.oruda.onsure.platform.ValidationModel.FindingStatus;
import kr.co.oruda.onsure.platform.ValidationModel.JobStatus;
import kr.co.oruda.onsure.platform.ValidationModel.Severity;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationJob;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        assertEquals(BehaviorLearningService.COVERAGE_PROXY, behavior.get("coverage_class"));
        assertEquals(false, behavior.get("direct_behavior_telemetry"));
        assertEquals(false, behavior.get("production_behavior_telemetry"));
        assertEquals("NOT_RUN", behavior.get("independent_validation"));
        assertTrue(((List<?>) behavior.get("observations")).size() >= 4);
        assertTrue(Files.isRegularFile(behaviorFile));
        for (Object item : (List<?>) behavior.get("observations")) {
            Map<?, ?> observation = (Map<?, ?>) item;
            Path receipt = Path.of(observation.get("run_receipt_path").toString());
            assertTrue(Files.isRegularFile(receipt), receipt.toString());
            assertTrue(observation.get("run_receipt_file_sha256").toString().matches("[0-9a-f]{64}"));
            assertEquals(Hashing.file(receipt), observation.get("run_receipt_file_sha256"));
        }
    }

    @Test
    void riskPlanAllowsOnlyExactBoundedLocalDevelopmentScope() throws Exception {
        Path source = Path.of("fixtures/e2e/general-program").toAbsolutePath().normalize();
        Path profileFile = temp.resolve("program-profile.json");
        new ProgramLearningService().learn(source, "project-general", "sample-general-program", profileFile);
        ValidationTarget target = new ValidationTarget(
                "sample-general-program", "Sample General", TargetType.GENERAL_SOFTWARE, source,
                SourceReferenceBinding.treeReference(source), GenericManifestTargetAdapter.ID,
                "ONSURE_DEFAULT_POLICY_V1", "LOCAL_DEVELOPMENT");
        Path planFile = temp.resolve("execution-plan.json");
        ExecutionPlanService service = new ExecutionPlanService();
        Map<String, Object> plan = service.plan(target, profileFile, 2, planFile);
        Map<?, ?> approval = (Map<?, ?>) plan.get("approval");
        Map<?, ?> permissions = (Map<?, ?>) plan.get("permissions");
        Set<String> allowed = Set.copyOf((List<String>) plan.get("allowed_actions"));
        Set<String> approved = Set.copyOf((List<String>) approval.get("approved_actions"));
        assertEquals("AUTO_APPROVED_DEVELOPMENT_NONFINAL", approval.get("state"));
        assertEquals(allowed, approved);
        assertTrue(ExecutionPlanService.APPROVABLE_ACTIONS.containsAll(approved));
        assertEquals(false, permissions.get("modify_target"));
        assertEquals(false, permissions.get("git_push"));
        assertEquals(false, permissions.get("merge"));
        service.requireApproved(plan);
        assertEquals(plan.get("plan_sha256"), service.planHash(plan));
    }

    @Test
    void patchPlanRequiresApprovalAndDoesNotModifySource() throws Exception {
        Path source = temp.resolve("target");
        Files.createDirectories(source);
        git(source, "init");
        git(source, "config", "user.email", "test@example.invalid");
        git(source, "config", "user.name", "ONSure Test");
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
        git(source, "add", "agent.txt", "onsure-target.json");
        git(source, "commit", "-m", "baseline");
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
                "finding-patch-001", fingerprint, "AI_SELF_APPROVAL", Severity.HIGH,
                FindingStatus.OPEN, "Self approval marker", "Self approval marker",
                "agent.txt", List.of("EV-SOURCE"), "STATIC_ANALYSIS"));

        String original = Files.readString(file);
        Path planFile = context.runRoot().resolve("patch-plan.json");
        Map<String, Object> plan = new ImprovementWorkflowService().createPatchPlan(context, planFile);
        assertEquals("AWAITING_HUNK_APPROVAL", plan.get("execution_state"));
        assertEquals(1, ((List<?>) plan.get("hunks")).size());
        assertEquals(original, Files.readString(file));
        assertEquals(false, plan.get("direct_main_write_allowed"));
        assertEquals(false, plan.get("merge_allowed"));
        Map<?, ?> assessment = (Map<?, ?>) plan.get("preapply_assessment");
        assertEquals(75, assessment.get("risk_score"));
        assertEquals("HIGH", assessment.get("risk_level"));
        assertEquals(true, assessment.get("approval_required"));
        Map<?, ?> impact = (Map<?, ?>) assessment.get("impact_scope");
        assertEquals(List.of("agent.txt"), impact.get("changed_files"));
        assertEquals(List.of("finding-patch-001"), impact.get("finding_ids"));
        assertEquals(1, impact.get("hunk_count"));
        Map<?, ?> rollback = (Map<?, ?>) assessment.get("rollback_preview");
        assertEquals("BYTE_EXACT_PREIMAGE_BACKUP", rollback.get("method"));
        assertEquals("SHA256_PREIMAGE_RESTORE_AND_SOURCE_TREE_MATCH", rollback.get("verification"));
        assertEquals(false, rollback.get("target_source_mutated_before_approval"));
    }

    private static void git(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).directory(root.toFile())
                .redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes());
        if (process.waitFor() != 0) throw new IllegalStateException(output);
    }
}
