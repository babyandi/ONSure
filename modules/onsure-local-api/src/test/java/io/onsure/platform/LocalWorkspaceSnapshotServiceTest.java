package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalWorkspaceSnapshotServiceTest {
    @TempDir Path temp;
    @TempDir Path outside;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void returnsRegisteredProfilePlanRunsFindingsEvidenceAndArtifacts() throws Exception {
        registerTarget();
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        dispatcher.dispatch("program.learn", request(Map.of(
                "project_id", "project-001", "target_id", "target-001",
                "program_id", "target-001")));
        Path profile = temp.resolve(".onsure/profiles/target-001/program-profile.json");
        dispatcher.dispatch("plan.generate", request(Map.of(
                "project_id", "project-001", "target_id", "target-001",
                "program_profile_file", profile.toString())));

        Path run = temp.resolve(".onsure/validation-data/target-001/JOB-001");
        Files.createDirectories(run);
        Files.writeString(run.resolve("job.json"), """
                {"jobId":"JOB-001","status":"COMPLETED"}
                """);
        Files.writeString(run.resolve("validation-report.json"), """
                {"reportId":"REPORT-001","jobId":"JOB-001","decision":"FAIL",
                 "generatedAt":"2026-08-02T00:00:00Z"}
                """);
        Files.writeString(run.resolve("findings.json"), """
                [{"findingId":"F-001","severity":"HIGH","title":"Unsafe boundary","status":"OPEN"}]
                """);
        Files.writeString(run.resolve("evidence.json"), """
                [{"evidenceId":"E-001","type":"SOURCE","subject":"src/Main.java"}]
                """);
        Files.writeString(run.resolve("patch-plan.json"), "{}\n");

        Map<String, Object> snapshot = new LocalWorkspaceSnapshotService(temp)
                .snapshot("project-001", "target-001");
        assertEquals(LocalWorkspaceSnapshotService.CONTRACT, snapshot.get("contract"));
        assertEquals("AVAILABLE", map(snapshot.get("profile")).get("state"));
        assertEquals("AVAILABLE", map(snapshot.get("plan")).get("state"));
        assertEquals("NOT_PRESENT", map(snapshot.get("approved_plan")).get("state"));
        assertEquals("NOT_PRESENT",
                map(map(snapshot.get("delivery")).get("patch_apply_receipt")).get("state"));
        assertEquals(1, snapshot.get("run_count"));

        Map<String, Object> latest = map(snapshot.get("latest_run"));
        assertEquals("JOB-001", latest.get("job_id"));
        assertEquals("FAIL", latest.get("decision"));
        assertEquals(1, latest.get("finding_count"));
        assertEquals(1, latest.get("evidence_count"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> artifacts = (List<Map<String, Object>>) latest.get("artifacts");
        assertTrue(artifacts.stream().anyMatch(value -> "patch-plan.json".equals(value.get("name"))));
        assertFalse(Boolean.TRUE.equals(snapshot.get("final_claim_allowed")));
    }

    @Test
    void rejectsUnknownOrInvalidIdentityAndIgnoresSymlinkRun() throws Exception {
        registerTarget();
        LocalWorkspaceSnapshotService service = new LocalWorkspaceSnapshotService(temp);
        assertThrows(IllegalArgumentException.class,
                () -> service.snapshot("bad:id", "target-001"));
        assertThrows(IllegalArgumentException.class,
                () -> service.snapshot("project-001", "unknown"));

        Path targetRuns = temp.resolve(".onsure/validation-data/target-001");
        Files.createDirectories(targetRuns);
        Path outside = temp.resolve("outside-run");
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(targetRuns.resolve("linked-run"), outside);
            assertEquals(0, service.snapshot("project-001", "target-001").get("run_count"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // Platform does not support test symlinks; identity rejection remains verified.
        }
    }

    @Test
    void autopilotControlIsCheckpointBoundAndVisibleInSnapshot() throws Exception {
        registerTarget();
        LocalAutopilotControlService controls = new LocalAutopilotControlService(temp);
        assertThrows(IllegalStateException.class, () -> controls.request("PAUSE"));

        Path root = temp.resolve(".onsure/autopilot");
        Files.createDirectories(root);
        Path checkpoint = root.resolve("checkpoint.json");
        mapper.writeValue(checkpoint.toFile(), Map.of(
                "contract", "ONSURE_UNATTENDED_AUTOPILOT_V1",
                "contract_sha256", "a".repeat(64),
                "state", "RUNNING"));
        Map<String, Object> control = controls.request("PAUSE");
        assertEquals("PAUSED", control.get("desired_state"));
        assertFalse(Boolean.TRUE.equals(control.get("final_claim_allowed")));

        Map<String, Object> snapshot = new LocalWorkspaceSnapshotService(temp)
                .snapshot("project-001", "target-001");
        Map<String, Object> autopilot = map(snapshot.get("autopilot"));
        assertEquals("AVAILABLE", map(autopilot.get("checkpoint")).get("state"));
        assertEquals("AVAILABLE", map(autopilot.get("control")).get("state"));

        mapper.writeValue(checkpoint.toFile(), Map.of(
                "contract", "ONSURE_UNATTENDED_AUTOPILOT_V1",
                "contract_sha256", "a".repeat(64),
                "state", "WAITING_HUMAN_GATE"));
        assertThrows(IllegalStateException.class, () -> controls.request("RESUME"));
        assertThrows(IllegalArgumentException.class, () -> controls.request("UNKNOWN"));
    }

    @Test
    void ignoresIntermediateSymlinkThatEscapesWorkspace() throws Exception {
        registerTarget();
        Path outsideRun = outside.resolve("target-001/JOB-OUTSIDE");
        Files.createDirectories(outsideRun);
        Files.writeString(outsideRun.resolve("job.json"),
                "{\"jobId\":\"JOB-OUTSIDE\",\"status\":\"COMPLETED\"}");
        try {
            Files.createSymbolicLink(temp.resolve(".onsure/validation-data"), outside);
            Map<String, Object> snapshot = new LocalWorkspaceSnapshotService(temp)
                    .snapshot("project-001", "target-001");
            assertEquals(0, snapshot.get("run_count"));
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException ignored) {
            // Platform does not support test symlinks; other safety assertions remain active.
        }
    }

    private void registerTarget() throws Exception {
        Files.writeString(temp.resolve("README.md"), "snapshot target\n");
        Files.writeString(temp.resolve("onsure-target.json"), """
                {"contract":"ONSURE_TARGET_MANIFEST_V1","target_id":"target-001",
                 "target_type":"GENERAL_SOFTWARE","self_reported_final_decision":false,
                 "capabilities":[],"fixtures":[]}
                """);
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        dispatcher.dispatch("project.register-workspace", request(Map.of(
                "workspace_id", "workspace-001", "workspace_name", "Workspace")));
        dispatcher.dispatch("project.register", request(Map.of(
                "workspace_id", "workspace-001", "project_id", "project-001",
                "project_name", "Project")));
        dispatcher.dispatch("project.register-target", request(Map.of(
                "project_id", "project-001", "target_id", "target-001",
                "target_name", "Target", "target_type", "GENERAL_SOFTWARE",
                "source_root", temp.toString())));
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> map(Object value) {
        return (Map<String, Object>) value;
    }
}
