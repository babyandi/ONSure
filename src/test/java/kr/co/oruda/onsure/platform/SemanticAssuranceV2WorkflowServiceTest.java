package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticAssuranceV2WorkflowServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @BeforeEach
    void createAuthorizedRoots() throws Exception {
        Files.createDirectories(temp.resolve("target-src"));
        Files.createDirectories(temp.resolve("deployment"));
    }

    @Test
    void legacyPassDoesNotBecomeV2PassWithoutEvidenceIndependenceQualificationAndFreshness() {
        JsonNode legacy = mapper.valueToTree(Map.of("decision", "PASS"));
        Map<String, Object> result = new SemanticAssuranceV2Reconstructor()
                .reconstructStatus(legacy, "LEGACY_TEST_V1", "target-1");
        assertEquals("HOLD", result.get("decision"));
        assertEquals("NOT_ASSESSED", result.get("independence_state"));
        assertEquals("NOT_QUALIFIED", result.get("qualification_state"));
        assertEquals("STATUS_UNKNOWN", result.get("freshness_state"));
        assertEquals(false, result.get("final_claim_allowed"));
    }

    @Test
    void directServiceCallWithoutServerBoundContextIsDenied() {
        SemanticAssuranceV2WorkflowService service = service();
        SecurityException denied = assertThrows(SecurityException.class, () -> service.dispatch(
                "semantic.denominator.discover",
                mapper.valueToTree(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "items", List.of(Map.of("item_id", "REQ-1", "item_sha256", "a".repeat(64)))))));
        assertEquals("SEMANTIC_V2_AUTHORIZED_TARGET_MISMATCH", denied.getMessage());
    }

    @Test
    void duplicateDenominatorIdentityFailsClosed() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        JsonNode request = authorized(Map.of(
                "items", List.of(
                        Map.of("item_id", "REQ-1", "item_sha256", "a".repeat(64)),
                        Map.of("item_id", "REQ-1", "item_sha256", "b".repeat(64)))));
        Map<String, Object> result = result(service.dispatch("semantic.denominator.discover", request));
        assertEquals("HOLD", result.get("decision"));
    }

    @Test
    void denominatorListDigestIsProducedWithoutMapCoercionCrash() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        JsonNode request = authorized(Map.of(
                "items", List.of(Map.of(
                        "item_id", "REQ-1",
                        "item_sha256", "a".repeat(64),
                        "disposition", "INCLUDED"))));
        Map<String, Object> result = result(service.dispatch("semantic.denominator.discover", request));
        assertEquals("NON_FINAL", result.get("decision"));
        assertTrue(result.get("population_digest").toString().matches("[0-9a-f]{64}"));
    }

    @Test
    void applicabilityRequiresAllFourteenCapabilitiesAndRationaleForNotApplicable() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        List<Map<String, Object>> capabilities = new ArrayList<>();
        for (int i = 1; i <= 14; i++) {
            String id = "SA-" + String.format("%02d", i);
            capabilities.add(Map.of(
                    "capability_id", id,
                    "disposition", "SA-02".equals(id) ? "NOT_APPLICABLE_JUSTIFIED" : "APPLICABLE",
                    "rationale", "SA-02".equals(id) ? "" : "applicable"));
        }
        Map<String, Object> result = result(service.dispatch(
                "semantic.applicability.evaluate", authorized(Map.of("capabilities", capabilities))));
        assertEquals("HOLD", result.get("decision"));
    }

    @Test
    void selfAttestedIndependenceIsIgnored() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        Map<String, Object> result = result(service.dispatch(
                "semantic.independence.assess", authorized(Map.of(
                        "producer_principal_id", "person-1",
                        "verifier_principal_id", "person-2",
                        "implementation_independent", true,
                        "oracle_independent", true,
                        "discovery_independent", true,
                        "knowledge_independent", true))));
        assertEquals("HOLD", result.get("decision"));
        assertFalse((Boolean) result.get("independent"));
        assertEquals(true, result.get("self_attested_fields_ignored"));
    }

    @Test
    void independentReceiptAcceptanceRemainsHoldUntilVerifierIsWired() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        Map<String, Object> result = result(service.dispatch(
                "assurance.otester.accept", authorized(Map.of(
                        "independent", true,
                        "qualification_state", "QUALIFIED",
                        "freshness_state", "CURRENT",
                        "signature_verified", true,
                        "receipt_sha256", "a".repeat(64)))));
        assertEquals("HOLD", result.get("decision"));
        assertEquals(false, result.get("accepted"));
    }

    @Test
    void verifiedAndDeployedBytesMustMatchWhenServerBoundRootsExist() throws Exception {
        Files.writeString(temp.resolve("target-src/verified.bin"), "verified");
        Files.writeString(temp.resolve("deployment/deployed.bin"), "different");
        SemanticAssuranceV2WorkflowService service = service();
        JsonNode request = authorizedWithDeployment(Map.of(
                "verified_artifact_path", "target-src/verified.bin",
                "deployed_artifact_path", "deployment/deployed.bin"));
        Map<String, Object> result = result(service.dispatch("deployment.verify-installed", request));
        assertEquals("FAIL", result.get("decision"));
        assertFalse((Boolean) result.get("identity_equal"));
    }

    @Test
    void deploymentVerificationWithoutTargetBoundDeploymentRootIsBlocked() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        Map<String, Object> result = result(service.dispatch(
                "deployment.verify-installed", authorized(Map.of(
                        "verified_artifact_path", "target-src/verified.bin",
                        "deployed_artifact_path", "deployment/deployed.bin"))));
        assertEquals("BLOCKED", result.get("decision"));
    }

    @Test
    void externalGitPushRemainsBlockedUntilRuntimeIsExplicitlyWired() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        Map<String, Object> result = result(service.dispatch("git.push", authorized(Map.of())));
        assertEquals("BLOCKED", result.get("decision"));
        assertTrue(((List<?>) result.get("reasons")).get(0).toString().contains("EXTERNAL_EFFECT_RUNTIME_NOT_WIRED"));
    }

    private SemanticAssuranceV2WorkflowService service() {
        AuthenticatedWorkflowIdentity identity = new AuthenticatedWorkflowIdentity(
                "organization", "tenant-a", "workspace", "actor-a",
                Set.of(AuthenticatedWorkflowIdentity.Role.AUDITOR), "LOCAL",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
        return new SemanticAssuranceV2WorkflowService(temp, identity);
    }

    private JsonNode authorized(Map<String, Object> values) {
        Map<String, Object> request = new LinkedHashMap<>(values);
        request.put("project_id", "project-1");
        request.put("target_id", "target-1");
        request.put("_authorized_project_id", "project-1");
        request.put("_authorized_target_id", "target-1");
        request.put("_authorized_target_root", temp.resolve("target-src").toAbsolutePath().normalize().toString());
        return mapper.valueToTree(request);
    }

    private JsonNode authorizedWithDeployment(Map<String, Object> values) {
        Map<String, Object> request = mapper.convertValue(authorized(values), Map.class);
        request.put("_authorized_deployment_root", temp.resolve("deployment").toAbsolutePath().normalize().toString());
        return mapper.valueToTree(request);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> result(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("result");
    }
}
