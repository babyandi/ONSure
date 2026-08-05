package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TargetProvenanceRunVerifierTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void verifiesReceiptReportEvidenceEqualityAndRejectsReportTampering() throws Exception {
        Path workspace = Files.createDirectory(temp.resolve("workspace"));
        Path source = Files.createDirectory(temp.resolve("source"));
        Files.writeString(source.resolve("openapi.yaml"),
                "openapi: 3.1.0\ninfo: {title: test, version: '1'}\npaths: {}\n");
        LocalProgramManagementService service = new LocalProgramManagementService(workspace);
        service.register(mapper.valueToTree(Map.of(
                "workspace_id", "local", "workspace_name", "Local", "project_id", "project",
                "project_name", "Project", "target_id", "target", "target_name", "Target",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
        Map<String, Object> result = service.validate(mapper.valueToTree(Map.of(
                "project_id", "project", "target_id", "target", "profile", "UNIVERSAL")));
        Path run = Path.of(result.get("run_root").toString());

        var verified = new TargetProvenanceRunVerifier().verify(run);
        assertTrue(verified.valid(), verified.reasons().toString());
        assertFalse(verified.realTargetEvidenceEligible());
        assertEquals("UNKNOWN", result.get("target_classification"));
        assertFalse((Boolean) result.get("real_target_universality_evidence_eligible"));

        Path reportFile = run.resolve("validation-report.json");
        ObjectNode report = (ObjectNode) mapper.readTree(reportFile.toFile());
        report.put("targetProvenanceSha256", "0".repeat(64));
        mapper.writeValue(reportFile.toFile(), report);

        var tampered = new TargetProvenanceRunVerifier().verify(run);
        assertFalse(tampered.valid());
        assertTrue(tampered.reasons().contains("REPORT_PROVENANCE_DIGEST_MISMATCH"));
    }

    @Test
    void dirtyRealRepositoryIsBlockedBeforeAnyValidationStep() throws Exception {
        Path repository = Files.createDirectory(temp.resolve("repository"));
        Files.writeString(repository.resolve("app.txt"), "committed\n");
        git(repository, "init", "-q");
        git(repository, "config", "user.email", "test@onsure.invalid");
        git(repository, "config", "user.name", "ONSure Test");
        git(repository, "add", ".");
        git(repository, "commit", "-q", "-m", "initial");
        Files.writeString(repository.resolve("untracked.txt"), "dirty\n");
        TargetProvenanceService service = new TargetProvenanceService(
                Files.createDirectory(temp.resolve("runner-workspace")));
        Map<String, Object> provenance = service.capture(
                repository, Hashing.sha256("registration"), "REAL_REPOSITORY");

        var result = new UniversalValidationRunner((step, root) -> {
            throw new AssertionError("dirty real target must not execute steps");
        }).run(profile(repository), temp.resolve("run"), null, null, provenance);

        JsonNode receipt = mapper.readTree(result.receiptFile().toFile());
        assertEquals("BLOCKED", result.overallOutcome().name());
        assertEquals("BLOCKED_BEFORE_EXECUTION",
                receipt.path("target_provenance_binding").path("state").asText());
        assertFalse(receipt.path("real_target_universality_evidence_eligible").asBoolean(true));
        assertTrue(result.steps().stream().allMatch(step ->
                step.outcome() == UniversalValidationProfile.Outcome.NOT_RUN));
    }

    @Test
    void sourceMutationDuringExecutionInvalidatesProvenanceEvidence() throws Exception {
        Path source = Files.createDirectory(temp.resolve("mutable-source"));
        Files.writeString(source.resolve("app.txt"), "before\n");
        TargetProvenanceService service = new TargetProvenanceService(
                Files.createDirectory(temp.resolve("mutation-workspace")));
        Map<String, Object> provenance = service.capture(
                source, Hashing.sha256("registration"), "AUTO");
        var runner = new UniversalValidationRunner(new UniversalValidationRunner.StepExecutor() {
            @Override
            public UniversalValidationRunner.StepExecution execute(
                    UniversalValidationProfile.Step step, Path root) {
                throw new AssertionError("execution must stop after mutation");
            }

            @Override
            public UniversalValidationRunner.StepExecution probe(Path root) throws Exception {
                Files.writeString(source.resolve("app.txt"), "after\n");
                return new UniversalValidationRunner.StepExecution(
                        UniversalValidationProfile.Outcome.PASS_NONFINAL, 0, "pass", false, "TEST");
            }
        });

        var result = runner.run(profile(source), temp.resolve("mutation-run"),
                null, null, provenance);
        JsonNode receipt = mapper.readTree(result.receiptFile().toFile());

        assertTrue(result.sourceMutationDetected());
        assertEquals("FAIL", result.overallOutcome().name());
        assertEquals("INVALID_EVIDENCE_AFTER_EXECUTION",
                receipt.path("target_provenance_binding").path("state").asText());
        assertFalse(receipt.path("real_target_universality_evidence_eligible").asBoolean(true));
    }

    private static UniversalValidationProfile.Profile profile(Path source) {
        List<UniversalValidationProfile.Step> steps = new ArrayList<>();
        steps.add(step("environment.preflight", UniversalValidationProfile.Phase.STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.ENVIRONMENT_PREFLIGHT, List.of()));
        steps.add(step("structure.inventory", UniversalValidationProfile.Phase.STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.INVENTORY, List.of("environment.preflight")));
        steps.add(step("validator.meta-check", UniversalValidationProfile.Phase.STRUCTURE_STATIC,
                UniversalValidationProfile.StepKind.VALIDATOR_META_CHECK, List.of("structure.inventory")));
        return new UniversalValidationProfile.Profile(
                "dirty-real", source, Set.of("TEST"), List.of(
                        new UniversalValidationProfile.EnvironmentRequirement(
                                "shell", UniversalValidationProfile.RequirementKind.EXECUTABLE,
                                "sh", true)), steps, Map.of(
                        UniversalValidationProfile.Phase.COMPONENT_AND_NEGATIVE, "NOT_CONFIGURED",
                        UniversalValidationProfile.Phase.END_TO_END_LINEAGE, "NOT_CONFIGURED",
                        UniversalValidationProfile.Phase.OPERATIONAL_RESILIENCE, "NOT_CONFIGURED"));
    }

    private static UniversalValidationProfile.Step step(String id,
            UniversalValidationProfile.Phase phase, UniversalValidationProfile.StepKind kind,
            List<String> dependencies) {
        return new UniversalValidationProfile.Step(
                id, phase, kind, true, List.of(), Path.of(""), Duration.ofMinutes(1), dependencies);
    }

    private static void git(Path root, String... arguments) throws Exception {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.add("-C");
        command.add(root.toString());
        command.addAll(List.of(arguments));
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        String output = new String(process.getInputStream().readAllBytes(),
                java.nio.charset.StandardCharsets.UTF_8);
        if (process.waitFor() != 0) throw new IllegalStateException("git failed: " + output);
    }
}
