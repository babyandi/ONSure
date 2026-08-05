package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ValidationImprovementLineageServiceTest {
    private final ObjectMapper mapper = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    @Test
    void bindsApprovedPatchAndProofToExactRunsAndSourcesWithoutDatabase(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root);
        Map<String, Object> result = new ValidationImprovementLineageService().bindValidated(
                fixture.baseline(), fixture.current(), fixture.patch(), fixture.proof(),
                root.resolve("lineage.json"), Map.of(), true);

        assertEquals("APPROVED", result.get("approval_state"));
        assertEquals("run-before", result.get("baseline_run_id"));
        assertEquals("run-after", result.get("current_run_id"));
        assertEquals("NOT_CONFIGURED", result.get("score_store_state"));
    }

    @Test
    void rejectsProofThatDoesNotBindTheExactCurrentReport(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root);
        Map<String, Object> changed = mapper.readValue(fixture.current().toFile(), Map.class);
        changed.put("decision", "FAIL");
        mapper.writeValue(fixture.current().toFile(), changed);

        assertThrows(IllegalStateException.class, () ->
                new ValidationImprovementLineageService().bindValidated(
                        fixture.baseline(), fixture.current(), fixture.patch(), fixture.proof(),
                        root.resolve("lineage.json"), Map.of(), true));
    }

    @Test
    void existingImprovementWorkflowGeneratesProofForProgramScoreReports(@TempDir Path root) throws Exception {
        Fixture fixture = fixture(root);
        Map<String, Object> proof = new ImprovementProofService().prove(
                fixture.baseline(), fixture.current(), fixture.patch(), root.resolve("generated-proof.json"));

        assertEquals("IMPROVEMENT_PROVEN", proof.get("decision"));
        assertEquals(true, proof.get("commit_allowed"));
        assertEquals("run-before", proof.get("baseline_job_id"));
        assertEquals("run-after", proof.get("current_job_id"));
    }

    @Test
    void rejectsDuplicateJsonKeysAndSymlinkOutput(@TempDir Path root) throws Exception {
        Fixture duplicateFixture = fixture(root);
        Files.writeString(duplicateFixture.current(),
                "{\"contract\":\"first\",\"contract\":\"second\"}");
        assertThrows(Exception.class, () ->
                new ValidationImprovementLineageService().bindValidated(
                        duplicateFixture.baseline(), duplicateFixture.current(),
                        duplicateFixture.patch(), duplicateFixture.proof(),
                        root.resolve("lineage.json"), Map.of(), true));

        Fixture symlinkFixture = fixture(root);
        Path destination = root.resolve("actual-lineage.json");
        Path link = root.resolve("lineage-link.json");
        Files.createSymbolicLink(link, destination);
        assertThrows(IllegalArgumentException.class, () ->
                new ValidationImprovementLineageService().bindValidated(
                        symlinkFixture.baseline(), symlinkFixture.current(),
                        symlinkFixture.patch(), symlinkFixture.proof(),
                        link, Map.of(), true));
    }

    private Fixture fixture(Path root) throws Exception {
        String before = "a".repeat(64);
        String after = "b".repeat(64);
        Path baseline = root.resolve("baseline.json");
        Path current = root.resolve("current.json");
        write(baseline, report("run-before", before, 10));
        write(current, report("run-after", after, 20));

        Path patch = root.resolve("patch.json");
        Map<String, Object> patchBody = new LinkedHashMap<>();
        patchBody.put("contract", ImprovementWorkflowService.APPLY_RECEIPT_CONTRACT);
        patchBody.put("approval_receipt_sha256", "c".repeat(64));
        patchBody.put("approval_actor", "reviewer");
        patchBody.put("approval_key_id", "key-1");
        patchBody.put("source_tree_sha256", before);
        patchBody.put("postimage_source_tree_sha256", after);
        patchBody.put("receipt_sha256", canonical(patchBody, "receipt_sha256"));
        write(patch, patchBody);

        Path proof = root.resolve("proof.json");
        Map<String, Object> proofBody = new LinkedHashMap<>();
        proofBody.put("contract", ImprovementProofService.CONTRACT);
        proofBody.put("target_id", "target-1");
        proofBody.put("baseline_job_id", "run-before");
        proofBody.put("current_job_id", "run-after");
        proofBody.put("baseline_report_sha256", Hashing.file(baseline));
        proofBody.put("current_report_sha256", Hashing.file(current));
        proofBody.put("patch_apply_receipt_sha256", Hashing.file(patch));
        proofBody.put("decision", "IMPROVEMENT_PROVEN");
        proofBody.put("commit_allowed", true);
        proofBody.put("focused_fixture_validation", "PASS");
        proofBody.put("full_regression", "PASS");
        proofBody.put("context_same", true);
        proofBody.put("source_changed", true);
        proofBody.put("proof_sha256", canonical(proofBody, "proof_sha256"));
        write(proof, proofBody);
        return new Fixture(baseline, current, patch, proof);
    }

    private static Map<String, Object> report(String runId, String source, int earned) {
        Map<String, Object> scorecard = Map.of(
                "contract", ValidationScorecard.CONTRACT, "earned_points", earned,
                "max_points", 100, "assessment_domains", java.util.List.of(),
                "phases", java.util.List.of(), "groups", java.util.List.of(),
                "assessment_areas", java.util.List.of(), "steps", java.util.List.of());
        return Map.ofEntries(
                Map.entry("contract", LocalProgramManagementService.CONTRACT),
                Map.entry("jobId", runId), Map.entry("projectId", "project-1"),
                Map.entry("targetId", "target-1"), Map.entry("targetType", "JAVA"),
                Map.entry("profile", "UNIVERSAL"), Map.entry("sourceDigestBefore", source),
                Map.entry("decision", "PASS_NONFINAL"), Map.entry("scorecard", scorecard),
                Map.entry("findings", java.util.List.of()));
    }

    private void write(Path file, Object value) throws Exception {
        mapper.writeValue(file.toFile(), value);
    }

    private String canonical(Map<String, Object> value, String field) throws Exception {
        Map<String, Object> copy = new TreeMap<>(value);
        copy.remove(field);
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(mapper.writeValueAsBytes(copy)));
    }

    private record Fixture(Path baseline, Path current, Path patch, Path proof) {}
}
