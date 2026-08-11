package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticAssuranceV2WorkflowServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

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
    void duplicateDenominatorIdentityFailsClosed() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        JsonNode request = mapper.valueToTree(Map.of(
                "target_id", "target-1",
                "items", java.util.List.of(
                        Map.of("item_id", "REQ-1", "item_sha256", "a".repeat(64)),
                        Map.of("item_id", "REQ-1", "item_sha256", "b".repeat(64)))));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service
                .dispatch("semantic.denominator.discover", request).get("result");
        assertEquals("HOLD", result.get("decision"));
    }

    @Test
    void notApplicableWithoutRationaleBecomesHold() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        JsonNode request = mapper.valueToTree(Map.of(
                "target_id", "target-1",
                "capabilities", java.util.List.of(Map.of(
                        "capability_id", "SA-02",
                        "disposition", "NOT_APPLICABLE_JUSTIFIED",
                        "rationale", ""))));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service
                .dispatch("semantic.applicability.evaluate", request).get("result");
        assertEquals("HOLD", result.get("decision"));
    }

    @Test
    void samePrincipalOrSharedAdminOwnerCannotBecomeIndependent() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        JsonNode request = mapper.valueToTree(Map.of(
                "target_id", "target-1",
                "producer_principal_id", "person-1",
                "verifier_principal_id", "person-1",
                "producer_admin_owner_id", "kms-admin",
                "verifier_admin_owner_id", "kms-admin",
                "implementation_independent", true,
                "oracle_independent", true,
                "discovery_independent", true,
                "knowledge_independent", true));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service
                .dispatch("semantic.independence.assess", request).get("result");
        assertEquals("HOLD", result.get("decision"));
        assertFalse((Boolean) result.get("independent"));
    }

    @Test
    void verifiedAndDeployedBytesMustMatch() throws Exception {
        Files.writeString(temp.resolve("verified.bin"), "verified");
        Files.writeString(temp.resolve("deployed.bin"), "different");
        SemanticAssuranceV2WorkflowService service = service();
        JsonNode request = mapper.valueToTree(Map.of(
                "target_id", "target-1",
                "verified_artifact_path", "verified.bin",
                "deployed_artifact_path", "deployed.bin"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service
                .dispatch("deployment.verify-installed", request).get("result");
        assertEquals("FAIL", result.get("decision"));
        assertFalse((Boolean) result.get("identity_equal"));
    }

    @Test
    void externalGitPushRemainsBlockedUntilRuntimeIsExplicitlyWired() throws Exception {
        SemanticAssuranceV2WorkflowService service = service();
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) service
                .dispatch("git.push", mapper.valueToTree(Map.of("target_id", "target-1"))).get("result");
        assertEquals("BLOCKED", result.get("decision"));
        assertTrue(((java.util.List<?>) result.get("reasons")).get(0).toString()
                .contains("EXTERNAL_EFFECT_RUNTIME_NOT_WIRED"));
    }

    private SemanticAssuranceV2WorkflowService service() {
        AuthenticatedWorkflowIdentity identity = new AuthenticatedWorkflowIdentity(
                "organization", "tenant-a", "workspace", "actor-a",
                Set.of(AuthenticatedWorkflowIdentity.Role.AUDITOR), "LOCAL",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
        return new SemanticAssuranceV2WorkflowService(temp, identity);
    }
}
