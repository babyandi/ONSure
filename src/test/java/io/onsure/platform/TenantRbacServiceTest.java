package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TenantRbacServiceTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void rolesAndDurableOwnershipDenyCrossTenantReadsAndWrites() throws Exception {
        TenantRbacService service = new TenantRbacService(temp);
        AuthenticatedWorkflowIdentity tenantA = identity(
                "tenant-a", "actor-a", AuthenticatedWorkflowIdentity.Role.OPERATOR);
        AuthenticatedWorkflowIdentity tenantB = identity(
                "tenant-b", "actor-b", AuthenticatedWorkflowIdentity.Role.OPERATOR);
        AuthenticatedWorkflowIdentity viewer = identity(
                "tenant-a", "viewer-a", AuthenticatedWorkflowIdentity.Role.VIEWER);

        assertEquals("workspace-created", service.execute(
                tenantA, "project.register-workspace",
                request(Map.of("workspace_id", "workspace-1")), () -> "workspace-created"));
        assertEquals("project-created", service.execute(
                tenantA, "project.register",
                request(Map.of("workspace_id", "workspace-1", "project_id", "project-1")),
                () -> "project-created"));
        assertEquals("read", service.execute(
                tenantA, "project.list-targets",
                request(Map.of("project_id", "project-1")), () -> "read"));

        SecurityException crossRead = assertThrows(SecurityException.class, () -> service.execute(
                tenantB, "project.list-targets",
                request(Map.of("project_id", "project-1")), () -> "must-not-run"));
        assertEquals("CROSS_TENANT_RESOURCE_ACCESS_DENIED:project:project-1", crossRead.getMessage());

        SecurityException crossWrite = assertThrows(SecurityException.class, () -> service.execute(
                tenantB, "project.register-workspace",
                request(Map.of("workspace_id", "workspace-1")), () -> "must-not-run"));
        assertEquals("CROSS_TENANT_RESOURCE_WRITE_DENIED:workspace:workspace-1", crossWrite.getMessage());

        SecurityException roleDenied = assertThrows(SecurityException.class, () -> service.execute(
                viewer, "project.register-workspace",
                request(Map.of("workspace_id", "workspace-2")), () -> "must-not-run"));
        assertEquals("RBAC_OPERATION_DENIED:project.register-workspace:OPERATOR", roleDenied.getMessage());
        assertTrue(service.verify().valid());
    }

    @Test
    void callerCannotSubstituteAuthenticatedActorTenantRegionOrRoles() throws Exception {
        TenantRbacService service = new TenantRbacService(temp);
        AuthenticatedWorkflowIdentity identity = identity(
                "tenant-a", "actor-a", AuthenticatedWorkflowIdentity.Role.OPERATOR);

        SecurityException actor = assertThrows(SecurityException.class, () -> service.execute(
                identity, "case.open", request(Map.of("actor", "attacker")), () -> "must-not-run"));
        assertEquals("AUTHENTICATED_ACTOR_SUBSTITUTION", actor.getMessage());

        Map<String, Object> substituted = Map.of(
                "organization_id", "organization",
                "tenant_id", "tenant-b",
                "workspace_id", "workspace",
                "actor_id", "actor-a",
                "roles", Set.of("ADMIN"),
                "data_region", "EU");
        SecurityException tenant = assertThrows(SecurityException.class, () -> service.execute(
                identity, "project.register-workspace",
                request(Map.of("workspace_id", "workspace-1", "tenant_context", substituted)),
                () -> "must-not-run"));
        assertEquals("AUTHENTICATED_TENANT_CONTEXT_SUBSTITUTION", tenant.getMessage());
    }

    @Test
    void failedWorkflowDoesNotLeaveAResourceClaimBehind() throws Exception {
        TenantRbacService service = new TenantRbacService(temp);
        AuthenticatedWorkflowIdentity tenantA = identity(
                "tenant-a", "actor-a", AuthenticatedWorkflowIdentity.Role.OPERATOR);
        AuthenticatedWorkflowIdentity tenantB = identity(
                "tenant-b", "actor-b", AuthenticatedWorkflowIdentity.Role.OPERATOR);

        assertThrows(IllegalStateException.class, () -> service.execute(
                tenantA, "project.register-workspace",
                request(Map.of("workspace_id", "workspace-1")),
                () -> { throw new IllegalStateException("EXPECTED_WORKFLOW_FAILURE"); }));
        assertEquals("tenant-b-created", service.execute(
                tenantB, "project.register-workspace",
                request(Map.of("workspace_id", "workspace-1")), () -> "tenant-b-created"));
        assertTrue(service.verify().valid());
    }

    @Test
    void validationResultBindsItsRunArtifactsToTheExecutingTenant() throws Exception {
        TenantRbacService service = new TenantRbacService(temp);
        AuthenticatedWorkflowIdentity tenantA = identity(
                "tenant-a", "actor-a", AuthenticatedWorkflowIdentity.Role.OPERATOR);
        AuthenticatedWorkflowIdentity tenantB = identity(
                "tenant-b", "actor-b", AuthenticatedWorkflowIdentity.Role.VIEWER);
        Path runRoot = temp.resolve(".onsure/validation-data/runs/run-1");
        Map<String, Object> workflow = Map.of("result", Map.of("run_root", runRoot.toString()));

        service.execute(tenantA, "validation.run", request(Map.of()), () -> workflow);
        assertEquals("artifact", service.execute(
                tenantA, "artifact.read", request(Map.of("run_root", runRoot.toString())),
                () -> "artifact"));
        SecurityException denied = assertThrows(SecurityException.class, () -> service.execute(
                tenantB, "artifact.read", request(Map.of("run_root", runRoot.toString())),
                () -> "must-not-run"));
        assertEquals("CROSS_TENANT_RESOURCE_ACCESS_DENIED:run:.onsure/validation-data/runs/run-1",
                denied.getMessage());
    }

    @Test
    void authenticatedDispatcherCannotBypassTheCommonAuthorizationBoundary() throws Exception {
        AuthenticatedWorkflowIdentity tenantA = identity(
                "tenant-a", "actor-a", AuthenticatedWorkflowIdentity.Role.OPERATOR);
        AuthenticatedWorkflowIdentity tenantB = identity(
                "tenant-b", "actor-b", AuthenticatedWorkflowIdentity.Role.OPERATOR);
        AuthenticatedWorkflowIdentity viewer = identity(
                "tenant-a", "viewer-a", AuthenticatedWorkflowIdentity.Role.VIEWER);
        LocalWorkflowDispatcher dispatcherA = new LocalWorkflowDispatcher(temp, tenantA);
        LocalWorkflowDispatcher dispatcherB = new LocalWorkflowDispatcher(temp, tenantB);

        dispatcherA.dispatch("project.register-workspace", request(Map.of(
                "workspace_id", "workspace-1", "workspace_name", "Tenant A")));
        SecurityException crossTenant = assertThrows(SecurityException.class, () ->
                dispatcherB.dispatch("project.register-workspace", request(Map.of(
                        "workspace_id", "workspace-1", "workspace_name", "Tenant B"))));
        assertEquals("CROSS_TENANT_RESOURCE_WRITE_DENIED:workspace:workspace-1",
                crossTenant.getMessage());
        SecurityException viewerWrite = assertThrows(SecurityException.class, () ->
                new LocalWorkflowDispatcher(temp, viewer).dispatch(
                        "project.register-workspace", request(Map.of(
                                "workspace_id", "workspace-2", "workspace_name", "Denied"))));
        assertEquals("RBAC_OPERATION_DENIED:project.register-workspace:OPERATOR",
                viewerWrite.getMessage());
    }

    @Test
    void authenticatedDispatcherPersistsOnlyServerTenantAndActor() throws Exception {
        AuthenticatedWorkflowIdentity identity = identity(
                "tenant-a", "actor-a", AuthenticatedWorkflowIdentity.Role.OPERATOR);
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp, identity);

        @SuppressWarnings("unchecked")
        Map<String, Object> state = (Map<String, Object>) dispatcher.dispatch(
                "case.open", request(Map.of(
                        "case_id", "case-1", "service_type", "VALIDATION",
                        "target_reference", "target-1"))).get("result");
        assertEquals("organization", state.get("organization_id"));
        assertEquals("tenant-a", state.get("tenant_id"));
        assertEquals("workspace", state.get("workspace_id"));
        String ledger = Files.readString(temp.resolve(
                ".onsure/service-cases/cases/case-1/ledger.jsonl"));
        assertTrue(ledger.contains("\"actor\":\"actor-a\""));
        assertFalse(ledger.contains("LOCAL_API_BEARER"));
    }

    @Test
    void tenantRegistryCannotBeRedirectedThroughWorkspaceSymlink() throws Exception {
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        try {
            Files.createSymbolicLink(temp.resolve(".onsure"), outside);
        } catch (UnsupportedOperationException | java.nio.file.FileSystemException unsupported) {
            return;
        }
        IllegalArgumentException failure = assertThrows(
                IllegalArgumentException.class, () -> new TenantRbacService(temp));
        assertEquals("TENANT_RBAC_STATE_SYMLINK", failure.getMessage());
    }

    private AuthenticatedWorkflowIdentity identity(
            String tenant, String actor, AuthenticatedWorkflowIdentity.Role role) {
        return new AuthenticatedWorkflowIdentity(
                "organization", tenant, "workspace", actor, Set.of(role), "EU",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }
}
