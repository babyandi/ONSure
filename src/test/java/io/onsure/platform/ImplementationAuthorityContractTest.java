package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ImplementationAuthorityContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void platformProductAndUniversalHarnessPackagesAreAuthoritative() throws Exception {
        JsonNode contract = mapper.readTree(Path.of("contracts/implementation-authority.v1.json").toFile());
        assertEquals("ONSURE_IMPLEMENTATION_AUTHORITY_V1", contract.path("contract").asText());
        assertEquals("io.onsure.platform", contract.path("authoritative_product_package").asText());
        assertEquals("io.onsure.assurance", contract.path("authoritative_assurance_package").asText());
        assertEquals("io.onsure.harness", contract.path("authoritative_universal_harness_package").asText());
        assertEquals("io.onsure.platform.OrudaTargetAdapter", contract.path("authoritative_oruda_adapter").asText());
        assertEquals("io.onsure.harness.HarnessCli", contract.path("authoritative_universal_harness_main").asText());
        assertFalse(contract.path("oruda_claims_trusted").asBoolean(true));
        assertFalse(contract.path("oruda_can_write_onsure_final_decision").asBoolean(true));
        assertTrue(contract.path("standalone_runtime_required").asBoolean());
    }

    @Test
    void allRequiredProductScenariosHarnessInvariantsAndOutputsAreLocked() throws Exception {
        JsonNode contract = mapper.readTree(Path.of("contracts/implementation-authority.v1.json").toFile());
        Set<String> scenarios = textSet(contract.path("required_target_scenarios"));
        assertTrue(scenarios.containsAll(Set.of(
                "GENERAL_PROGRAM_BASELINE_FAIL",
                "GENERAL_PROGRAM_REMEDIATED_PASS",
                "AI_PROGRAM_FAIL",
                "ORUDA_TARGET_FAIL",
                "ORUDA_MVF_001_PASS")));
        Set<String> invariants = textSet(contract.path("required_universal_harness_invariants"));
        assertTrue(invariants.containsAll(Set.of(
                "THIRTY_REQUIRED_AXES",
                "SEVEN_FIXTURE_CATEGORIES",
                "FAIL_BLOCKED_NOT_RUN_CANNOT_BECOME_PASS",
                "TWO_INDEPENDENT_RUNS",
                "CRITICAL_MAJOR_ZERO_TWO_CONSECUTIVE_RUNS",
                "EVIDENCE_RECEIPT_REVERIFY",
                "RCA_PENDING_BLOCKS_CLOSURE",
                "REGRESSION_TWO_CLEAN_RUNS",
                "AUTOMATIC_FINAL_LOCK_FORBIDDEN")));
        Set<String> outputs = textSet(contract.path("required_persistent_outputs"));
        assertTrue(outputs.containsAll(Set.of(
                "finding", "failure_mode", "rca", "remediation_plan", "fixture_result",
                "oracle_result", "regression_lock", "validation_report", "revalidation_delta",
                "internal_verifier_receipt", "internal_audit_receipt",
                "universal_run_summary", "universal_evidence_manifest",
                "universal_run_receipt", "universal_final_candidate", "universal_regression_receipt")));
    }

    @Test
    void authoritativeImplementationFilesExistAndCompetingProductPackageDoesNot() {
        for (String path : Set.of(
                "src/main/java/io/onsure/platform/ValidationModel.java",
                "src/main/java/io/onsure/platform/ValidationEngine.java",
                "src/main/java/io/onsure/platform/FileValidationStore.java",
                "src/main/java/io/onsure/platform/FixtureRegistryStage.java",
                "src/main/java/io/onsure/platform/RemediationPlanningStage.java",
                "src/main/java/io/onsure/platform/OrudaTargetAdapter.java",
                "src/main/java/io/onsure/platform/ProductPlatformE2EMain.java",
                "src/main/java/io/onsure/harness/HarnessModels.java",
                "src/main/java/io/onsure/harness/UniversalHarnessRunner.java",
                "src/main/java/io/onsure/harness/RunVerifier.java",
                "src/main/java/io/onsure/harness/FinalCandidateGate.java",
                "src/main/java/io/onsure/harness/RegressionGate.java",
                "harness/universal-v1/axes/verification-axes.v1.json",
                "fixtures/universal-v1/sample-target/fixtures.v1.json",
                "scripts/run-product-platform-e2e.sh",
                "scripts/run-universal-harness-twice.sh",
                "scripts/run-onsure-development-gate.sh",
                "fixtures/e2e/general-program/onsure-target.json",
                "fixtures/e2e/general-program-fixed/onsure-target.json",
                "fixtures/e2e/ai-program/onsure-target.json",
                "fixtures/e2e/oruda-target/oruda-target.json",
                "fixtures/oruda/mvf-001/oruda-target.json",
                "onsure_chat_instruction_universal_harness_v1.md")) {
            assertTrue(Files.exists(Path.of(path)), "missing authoritative implementation: " + path);
        }
        assertFalse(Files.exists(Path.of("src/main/java/io/onsure/product")),
                "competing Product Core package must not exist");
        assertFalse(Files.exists(Path.of("src/test/java/io/onsure/product")),
                "competing Product Core tests must not exist");
    }

    private static Set<String> textSet(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText).collect(Collectors.toSet());
    }
}
