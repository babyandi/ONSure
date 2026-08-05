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

class LocalProgramManagementServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void registersExternalSourceAndWritesNonfinalReadOnlyEvidence() throws Exception {
        Path workspace = temp.resolve("workspace");
        Path source = temp.resolve("source");
        Files.createDirectories(workspace);
        Files.createDirectories(source.resolve("src/main/java/example"));
        Files.writeString(source.resolve("pom.xml"), "<project/>\n");
        Files.writeString(source.resolve("src/main/java/example/App.java"), "package example; public class App {}\n");
        Files.writeString(source.resolve("LICENSE"), "test only\n");
        Files.createDirectories(source.resolve(".venv/bin"));
        Files.createSymbolicLink(source.resolve(".venv/bin/python"), Path.of("/usr/bin/python3"));
        Files.createDirectories(source.resolve("module/target.root-owned-backup"));
        Files.createSymbolicLink(source.resolve("module/target.root-owned-backup/outside"), Path.of("/root/secret"));
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        Map<String, Object> registered = service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "project", "project_name", "Project",
                "target_id", "target", "target_name", "Target",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        assertTrue(Boolean.TRUE.equals(registered.get("read_only_registration")));
        String before = Files.readString(source.resolve("src/main/java/example/App.java"));
        Map<String, Object> validation = service.validate(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target", "profile", "INSPECT_ONLY")));
        assertFalse(Boolean.TRUE.equals(validation.get("source_mutation_detected")));
        assertEquals(before, Files.readString(source.resolve("src/main/java/example/App.java")));
        Path run = Path.of(validation.get("run_root").toString());
        assertTrue(Files.isRegularFile(run.resolve("validation-report.json")));
        assertTrue(Files.isRegularFile(run.resolve("evidence.json")));
        assertTrue(Files.isRegularFile(run.resolve("remediation-plans.json")));

        Files.writeString(source.resolve("src/main/java/example/App.java"),
                "package example; public class App { int changed; }\n");
        Map<String, Object> drifted = service.validate(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target", "profile", "INSPECT_ONLY")));
        assertEquals("HOLD", drifted.get("decision"));
        assertEquals(1, drifted.get("finding_count"));
    }

    @Test
    void universalManagementValidationUsesSharedRunnerReceiptAndProjection() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("universal-workspace"));
        Path source = Files.createDirectory(temp.resolve("universal-source"));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: managed, version: '1'}
                paths:
                  /health:
                    get:
                      operationId: getHealth
                      responses:
                        '200': {description: healthy}
                """);
        Path environmentProfile = workspace.resolve("environment-profile.json");
        Files.writeString(environmentProfile, """
                {"contract":"ONSURE_ENVIRONMENT_REQUIREMENT_PROFILE_V1","profile_id":"managed",
                 "requirements":[{"requirement_id":"optional.fixture","kind":"SOURCE_FILE",
                 "value":"fixtures/optional.json","required":false}]}
                """);
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "project", "project_name", "Project",
                "target_id", "managed", "target_name", "Managed",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));

        Map<String, Object> result = service.validate(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "managed", "profile", "UNIVERSAL",
                "environment_profile_file", environmentProfile.toString())));

        Path runRoot = Path.of(result.get("run_root").toString());
        String projection = Files.readString(runRoot.resolve("validation-report.json"));
        assertEquals("UNIVERSAL", result.get("profile"));
        assertEquals("NOT_RUN", result.get("decision"), projection);
        assertFalse(Boolean.TRUE.equals(result.get("source_mutation_detected")));
        Path receipt = Path.of(result.get("receipt_file").toString());
        assertTrue(Files.isRegularFile(receipt));
        assertEquals(Hashing.file(receipt), result.get("receipt_sha256"));
        assertTrue(result.get("scorecard") instanceof Map<?, ?>);
        assertTrue(Files.readString(receipt).contains("\"external_environment_profile\""));
        assertTrue(projection.contains("\"verificationGroupOutcomes\""));
        assertTrue(Files.isRegularFile(runRoot.resolve("evidence.json")));
        assertTrue(Files.isRegularFile(runRoot.resolve("remediation-plans.json")));
        assertFalse(Files.exists(source.resolve(".onsure")));

        @SuppressWarnings("unchecked")
        var programs = (java.util.List<Map<String, Object>>) new LocalManagementOverviewService(workspace)
                .overview().get("programs");
        @SuppressWarnings("unchecked")
        var latest = (Map<String, Object>) programs.get(0).get("latest_validation");
        assertEquals("NOT_RUN", latest.get("decision"));
        assertEquals(2, ((Number) latest.get("evidence_count")).intValue());
        assertTrue(latest.get("scorecard") instanceof Map<?, ?>);
    }

    @Test
    void universalManagementValidationRejectsSourceDriftBeforeCreatingRunEvidence() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("drift-workspace"));
        Path source = Files.createDirectory(temp.resolve("drift-source"));
        Files.writeString(source.resolve("openapi.yaml"), "openapi: 3.1.0\ninfo: {title: drift, version: '1'}\npaths: {}\n");
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "project", "project_name", "Project",
                "target_id", "drift", "target_name", "Drift",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        Files.writeString(source.resolve("changed.txt"), "changed after registration\n");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.validate(mapper.valueToTree(Map.of(
                        "project_id", "project", "target_id", "drift", "profile", "UNIVERSAL"))));

        assertEquals("PROGRAM_SOURCE_REFERENCE_DRIFT", error.getMessage());
        Path validationData = workspace.resolve(".onsure/validation-data/drift");
        assertFalse(Files.exists(validationData));
    }

    @Test
    void registeredProgramUnderstandingIsReadOnlyReviewOnlyAndSourceBound() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("understanding-workspace"));
        Path source = Files.createDirectory(temp.resolve("understanding-source"));
        Files.writeString(source.resolve("openapi.yaml"), """
                openapi: 3.1.0
                info: {title: Customer orders, version: '1'}
                paths: {/orders: {post: {operationId: createOrder, responses: {'200': {description: ok}}}}}
                """);
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "customer", "project_name", "Customer",
                "target_id", "orders", "target_name", "Orders",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        String before = Files.readString(source.resolve("openapi.yaml"));

        Map<String, Object> result = service.understand(mapper.valueToTree(Map.of(
                "project_id", "customer", "target_id", "orders")));

        assertEquals("ONSURE_PROGRAM_UNDERSTANDING_CANDIDATE_V1", result.get("contract"));
        assertEquals("NOT_RUN_REVIEW_REQUIRED", result.get("automatic_execution"));
        assertFalse((Boolean) result.get("source_mutation_detected"));
        assertEquals(before, Files.readString(source.resolve("openapi.yaml")));
        @SuppressWarnings("unchecked")
        Map<String, Object> understanding = (Map<String, Object>) result.get("program_understanding");
        assertTrue(((Number) understanding.get("flow_candidate_count")).intValue() > 0);
        assertFalse((Boolean) understanding.get("inferences_are_pass_evidence"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> questions = (List<Map<String, Object>>) understanding.get("minimal_questions");
        List<Map<String, Object>> answers = questions.stream().map(question -> Map.<String, Object>of(
                "question_id", question.get("question_id"),
                "answer_state", "CONFIRMED",
                "evidence_reference_id", "fixture:" + question.get("question_id"))).toList();
        Map<String, Object> review = service.reviewUnderstanding(mapper.valueToTree(Map.of(
                "project_id", "customer", "target_id", "orders",
                "profile_file_sha256", result.get("profile_file_sha256"),
                "answers", answers)));

        assertEquals("ONSURE_PROGRAM_UNDERSTANDING_REVIEW_V1", review.get("contract"));
        assertEquals("READY_FOR_SEPARATE_APPROVAL", review.get("review_state"));
        assertEquals("NOT_RUN", review.get("approval_state"));
        assertEquals("NOT_RUN", review.get("execution_state"));
        assertFalse((Boolean) review.get("secret_values_accepted"));
        assertFalse((Boolean) review.get("score_eligible"));
        assertEquals(before, Files.readString(source.resolve("openapi.yaml")));
        assertTrue(Files.isRegularFile(workspace.resolve(".onsure/program-understanding/orders/review.json")));
    }

    @Test
    void understandingReviewRejectsStaleProfileDigestAndUnknownQuestions() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("review-guard-workspace"));
        Path source = Files.createDirectory(temp.resolve("review-guard-source"));
        Files.writeString(source.resolve("openapi.json"), """
                {"openapi":"3.1.0","info":{"title":"Runs","version":"1"},
                 "paths":{"/runs":{"get":{"operationId":"listRuns","responses":{"200":{"description":"ok"}}}}}}
                """);
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local",
                "project_id", "customer", "project_name", "Customer",
                "target_id", "runs", "target_name", "Runs",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        Map<String, Object> understood = service.understand(mapper.valueToTree(Map.of(
                "project_id", "customer", "target_id", "runs")));

        IllegalArgumentException stale = assertThrows(IllegalArgumentException.class, () ->
                service.reviewUnderstanding(mapper.valueToTree(Map.of(
                        "project_id", "customer", "target_id", "runs",
                        "profile_file_sha256", "0".repeat(64), "answers", List.of()))));
        assertEquals("PROGRAM_UNDERSTANDING_PROFILE_DIGEST_MISMATCH", stale.getMessage());

        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () ->
                service.reviewUnderstanding(mapper.valueToTree(Map.of(
                        "project_id", "customer", "target_id", "runs",
                        "profile_file_sha256", understood.get("profile_file_sha256"),
                        "answers", List.of(Map.of("question_id", "INVENTED_SECRET",
                                "answer_state", "CONFIRMED", "evidence_reference_id", "env:SECRET"))))));
        assertEquals("PROGRAM_UNDERSTANDING_ANSWER_UNKNOWN", unknown.getMessage());
        assertFalse(Files.exists(workspace.resolve(".onsure/program-understanding/runs/review.json")));
    }
}
