package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
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

class EvidenceBasedRcaServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void sourceCandidateSeparatesObservedImpactFromExplicitUnknowns() throws Exception {
        ValidationContext context = context("job-rca-candidate");
        Map<String, Object> result = new EvidenceBasedRcaService().analyze(
                context, context.runRoot().resolve("evidence-based-rca.json"));

        Map<String, Object> record = record(result);
        @SuppressWarnings("unchecked")
        Map<String, Object> impact = (Map<String, Object>) record.get("impact_scope");
        assertEquals("SOURCE_LOCATION_OBSERVED", impact.get("classification"));
        assertEquals(List.of("source:src/main.py"), impact.get("affected_assets"));
        assertFalse((Boolean) impact.get("verified"));
        @SuppressWarnings("unchecked")
        List<String> unknowns = (List<String>) record.get("unknown_items");
        assertTrue(unknowns.contains("CAUSAL_FACTOR_NOT_CONFIRMED"));
        assertTrue(unknowns.contains("IMPACT_BOUNDARY_NOT_CAUSALLY_VERIFIED"));
        assertTrue(unknowns.contains("INDEPENDENT_CONFIRMATION_NOT_RUN"));
    }

    @Test
    void boundExperimentCanVerifyImpactButNeverInventIndependentConfirmation() throws Exception {
        ValidationContext context = context("job-rca-confirmed");
        mapper.writeValue(context.runRoot().resolve("rca-causal-experiments.json").toFile(), Map.of(
                "contract", EvidenceBasedRcaService.EXPERIMENT_CONTRACT,
                "job_id", context.job().jobId(),
                "source_tree_sha256", context.attributes().get("source_tree_sha256"),
                "experiments", List.of(Map.ofEntries(
                        Map.entry("experiment_id", "experiment-001"),
                        Map.entry("finding_id", "finding-001"),
                        Map.entry("decision", "PASS"),
                        Map.entry("single_factor_varied", true),
                        Map.entry("same_source_context", true),
                        Map.entry("same_environment_context", true),
                        Map.entry("source_tree_sha256", context.attributes().get("source_tree_sha256")),
                        Map.entry("control_output_sha256", "a".repeat(64)),
                        Map.entry("treatment_output_sha256", "b".repeat(64)),
                        Map.entry("experiment_receipt_sha256", "c".repeat(64)),
                        Map.entry("impact_scope_verified", true),
                        Map.entry("affected_assets", List.of("service:payments", "api:/v1/payments"))))));

        Map<String, Object> result = new EvidenceBasedRcaService().analyze(
                context, context.runRoot().resolve("evidence-based-rca.json"));
        Map<String, Object> record = record(result);
        assertEquals("CONFIRMED", record.get("status"));
        @SuppressWarnings("unchecked")
        Map<String, Object> impact = (Map<String, Object>) record.get("impact_scope");
        assertEquals("EXPERIMENT_VERIFIED", impact.get("classification"));
        assertEquals(true, impact.get("verified"));
        assertEquals(List.of("service:payments", "api:/v1/payments"), impact.get("affected_assets"));
        @SuppressWarnings("unchecked")
        List<String> unknowns = (List<String>) record.get("unknown_items");
        assertEquals(List.of("INDEPENDENT_CONFIRMATION_NOT_RUN"), unknowns);
    }

    private ValidationContext context(String jobId) throws Exception {
        Path source = temp.resolve(jobId + "-source");
        Files.createDirectories(source.resolve("src"));
        Files.writeString(source.resolve("src/main.py"), "ALLOW_UNTRUSTED_TOOL = True\n");
        ValidationTarget target = new ValidationTarget(
                "target-001", "Target", TargetType.GENERAL_SOFTWARE, source,
                SourceReferenceBinding.treeReference(source), GenericManifestTargetAdapter.ID,
                "policy", "LOCAL_DEVELOPMENT");
        ValidationJob job = new ValidationJob(
                jobId, target.targetId(), JobStatus.RUNNING, Instant.now(), Instant.now(), null, null);
        Path runRoot = temp.resolve(jobId + "-run");
        Files.createDirectories(runRoot);
        ValidationContext context = new ValidationContext(
                target, job, new GenericManifestTargetAdapter(), runRoot);
        context.putAttribute("source_tree_sha256", Hashing.tree(source));
        context.putAttribute("immutable_source_verified", true);
        context.addFinding(new Finding(
                "finding-001", Hashing.sha256("finding-001"), "AI_TOOL_AUTHORIZATION",
                Severity.HIGH, FindingStatus.OPEN, "Unsafe tool", "Unsafe tool authorization",
                "src/main.py", List.of(), "STATIC_ANALYSIS"));
        return context;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> record(Map<String, Object> result) {
        return (Map<String, Object>) ((List<?>) result.get("records")).get(0);
    }
}
