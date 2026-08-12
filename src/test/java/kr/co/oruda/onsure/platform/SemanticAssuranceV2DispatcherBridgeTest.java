package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticAssuranceV2DispatcherBridgeTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private SemanticAssuranceV2DispatcherBridge bridge;

    @BeforeEach
    void registerOwnedTarget() throws Exception {
        AuthenticatedWorkflowIdentity admin = identity("tenant-a", "admin-a");
        bridge = new SemanticAssuranceV2DispatcherBridge(temp, admin);
        Files.createDirectories(temp.resolve("target-src"));
        Files.writeString(temp.resolve("target-src/subject.txt"), "subject");
        Files.writeString(temp.resolve("outside.txt"), "other-tenant-or-target");
        bridge.dispatch("project.register-workspace", request(Map.of(
                "workspace_id", "workspace-1", "workspace_name", "Workspace")));
        bridge.dispatch("project.register", request(Map.of(
                "workspace_id", "workspace-1", "project_id", "project-1", "project_name", "Project")));
        bridge.dispatch("project.register-target", request(Map.of(
                "project_id", "project-1",
                "target_id", "target-1",
                "target_name", "Target",
                "target_type", "GENERAL_SOFTWARE",
                "source_root", "target-src")));
    }

    @Test
    void reperformanceMayReadOnlyInsideServerResolvedTargetRoot() throws Exception {
        String digest = Hashing.file(temp.resolve("target-src/subject.txt"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) bridge.dispatch(
                "semantic.reperformance.run", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "subject_path", "target-src/subject.txt",
                        "subject_sha256", digest,
                        "oracle_state", "PASS"))).get("result");
        assertEquals("NON_FINAL", ((Map<?, ?>) result.get("result")).get("decision"));

        SecurityException escape = assertThrows(SecurityException.class, () -> bridge.dispatch(
                "semantic.reperformance.run", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "subject_path", "outside.txt",
                        "subject_sha256", Hashing.file(temp.resolve("outside.txt")),
                        "oracle_state", "PASS"))));
        assertEquals("SEMANTIC_V2_TARGET_PATH_ESCAPE:subject_path", escape.getMessage());
    }

    @Test
    void crossTenantSemanticOperationIsDeniedInsideDurableOwnershipTransaction() throws Exception {
        SemanticAssuranceV2DispatcherBridge tenantB = new SemanticAssuranceV2DispatcherBridge(
                temp, identity("tenant-b", "admin-b"));
        SecurityException denied = assertThrows(SecurityException.class, () -> tenantB.dispatch(
                "semantic.denominator.discover", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "items", java.util.List.of(Map.of(
                                "item_id", "REQ-1", "item_sha256", "a".repeat(64)))))));
        assertEquals("CROSS_TENANT_RESOURCE_ACCESS_DENIED:project:project-1", denied.getMessage());
    }

    @Test
    void callerCannotInjectServerAuthorityFields() {
        SecurityException denied = assertThrows(SecurityException.class, () -> bridge.dispatch(
                "semantic.denominator.discover", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "_authorized_target_root", temp.toString(),
                        "items", java.util.List.of()))));
        assertEquals("SEMANTIC_V2_SERVER_AUTHORITY_FIELD_SUBSTITUTION:_authorized_target_root", denied.getMessage());
    }

    @Test
    void deploymentVerificationStaysBlockedUntilDeploymentIsTargetBound() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) bridge.dispatch(
                "deployment.verify-installed", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "verified_artifact_path", "target-src/subject.txt",
                        "deployed_artifact_path", "outside.txt"))).get("result");
        assertEquals("BLOCKED", result.get("decision"));
        assertEquals("TARGET_BOUND_DEPLOYMENT_IDENTITY_NOT_AVAILABLE",
                ((java.util.List<?>) result.get("reasons")).get(0));
    }

    private AuthenticatedWorkflowIdentity identity(String tenant, String actor) {
        return new AuthenticatedWorkflowIdentity(
                "organization", tenant, "workspace", actor,
                Set.of(AuthenticatedWorkflowIdentity.Role.ADMIN), "LOCAL",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }
}
