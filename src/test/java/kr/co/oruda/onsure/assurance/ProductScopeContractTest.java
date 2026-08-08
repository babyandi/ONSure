package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.Test;

class ProductScopeContractTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void onsureIsIndependentCommercialGeneralValidationPlatform() throws Exception {
        JsonNode scope = mapper.readTree(Path.of("contracts/product-scope.v1.json").toFile());
        assertEquals("ONSURE_PRODUCT_SCOPE_V1", scope.path("contract").asText());
        assertEquals("INDEPENDENT_COMMERCIAL_SOFTWARE_VALIDATION_PLATFORM",
                scope.path("product_category").asText());
        assertTrue(scope.path("independence").path("standalone_product").asBoolean());
        assertTrue(scope.path("independence").path("works_without_oruda").asBoolean());
        assertFalse(scope.path("independence").path("target_specific_runtime_dependency").asBoolean(true));

        Set<String> targetTypes = textSet(scope.path("commercial_positioning").path("supported_target_types"));
        assertTrue(targetTypes.contains("AI_APPLICATION"));
        assertTrue(targetTypes.contains("GENERAL_SOFTWARE"));

        Set<String> capabilities = textSet(scope.path("core_capabilities"));
        assertTrue(capabilities.containsAll(Set.of(
                "ROOT_CAUSE_ANALYSIS",
                "FAILURE_MODE_DETECTION",
                "FIXTURE_GENERATION_AND_REGISTRY",
                "HARNESS_EXECUTION",
                "ORACLE_BASED_JUDGEMENT",
                "RECEIPT_AND_EVIDENCE_CHAIN",
                "REGRESSION_LOCK",
                "REMEDIATION_PLANNING_AND_PATCHING",
                "VALIDATION_REPORT_GENERATION",
                "INDEPENDENT_REVALIDATION")));
    }

    @Test
    void orudaIsFirstTargetAndDesignInputNotRuntimeDependency() throws Exception {
        JsonNode scope = mapper.readTree(Path.of("contracts/product-scope.v1.json").toFile());
        JsonNode source = scope.path("validator_engine_sources").get(0);
        assertEquals("ORUDA_ADAPTIVE_VALIDATION_MASTER", source.path("source").asText());
        assertEquals("DESIGN_INPUT_ONLY", source.path("relationship").asText());
        assertFalse(source.path("runtime_dependency").asBoolean(true));
        assertFalse(source.path("authority_dependency").asBoolean(true));

        JsonNode registry = mapper.readTree(Path.of("contracts/validation-target-registry.v1.json").toFile());
        JsonNode first = registry.path("targets").get(0);
        assertEquals(1, first.path("ordinal").asInt());
        assertEquals("ORUDA", first.path("target_id").asText());
        assertEquals("PLANNED_FIRST_VALIDATION_TARGET", first.path("status").asText());
        assertEquals("EXTERNAL_VALIDATION_TARGET", first.path("relationship").asText());
        assertFalse(first.path("onsure_runtime_dependency_on_target").asBoolean(true));
        assertEquals("ONSURE_INDEPENDENT_VERIFIER_AND_AUDIT",
                first.path("final_decision_authority").asText());
    }

    @Test
    void targetAdapterCannotOverridePolicyOracleOrIndependentDecision() throws Exception {
        JsonNode adapter = mapper.readTree(Path.of("contracts/target-adapter.v1.json").toFile());
        assertEquals("ONSURE_TARGET_ADAPTER_V1", adapter.path("contract").asText());
        Set<String> prohibited = textSet(adapter.path("prohibited"));
        assertTrue(prohibited.containsAll(Set.of(
                "OVERRIDE_ONSURE_POLICY",
                "OVERRIDE_ONSURE_ORACLE",
                "WRITE_INDEPENDENT_VERIFIER_DECISION",
                "WRITE_INDEPENDENT_AUDIT_DECISION",
                "PROMOTE_TARGET_SELF_REPORTED_PASS_TO_FINAL_PASS",
                "CREATE_CORE_RUNTIME_DEPENDENCY_ON_TARGET")));
        assertTrue(textSet(adapter.path("embedded_mode_requires")).containsAll(Set.of(
                "PORTABLE_RECEIPT_EXPORT",
                "STANDALONE_REVERIFICATION_SUPPORT",
                "EMBEDDED_STANDALONE_EQUIVALENCE_TEST")));
    }

    @Test
    void omakerAndObuilderRemainOptionalReferenceProviders() throws Exception {
        JsonNode adapter = mapper.readTree(Path.of("contracts/omaker-obuilder-adapter.v1.json").toFile());
        assertEquals("REFERENCE_PROVIDER_COMPATIBILITY", adapter.path("status").asText());
        assertFalse(adapter.path("authority").path("this_contract_is_core_required").asBoolean(true));
        assertFalse(adapter.path("authority").path("oruda_runtime_required").asBoolean(true));
        assertTrue(adapter.path("standalone_requirement").path("works_without_oruda").asBoolean());
        assertFalse(adapter.path("standalone_requirement").path("external_target_runtime_required").asBoolean(true));
    }

    @Test
    void assuranceLanesUseGenericProductAndTargetBoundaries() throws Exception {
        JsonNode lanes = mapper.readTree(Path.of("contracts/assurance-lanes.v1.json").toFile());
        assertEquals("INDEPENDENT_COMMERCIAL_SOFTWARE_VALIDATION_PLATFORM",
                lanes.path("product_category").asText());
        assertTrue(textSet(lanes.path("target_scope")).containsAll(Set.of("AI_APPLICATION", "GENERAL_SOFTWARE")));
        assertTrue(textSet(lanes.path("invariants")).containsAll(Set.of(
                "no_target_specific_runtime_dependency",
                "oruda_is_first_target_not_product_dependency",
                "embedded_target_module_exports_portable_receipts")));
    }

    private static Set<String> textSet(JsonNode array) {
        return StreamSupport.stream(array.spliterator(), false)
                .map(JsonNode::asText)
                .collect(Collectors.toSet());
    }
}
