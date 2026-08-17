package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** deployment.register-target / deployment.read-target -- the deployment identity binding
 * Batch 3 ("Assurance Core") builds on top of, letting deployment.verify-installed resolve a real,
 * server-bound install root instead of always returning BLOCKED. */
class DeploymentIdentityRegistrationTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private LocalWorkflowDispatcher dispatcher;

    @BeforeEach
    void registerProjectAndTarget() throws Exception {
        dispatcher = new LocalWorkflowDispatcher(temp);
        dispatcher.dispatch("project.register-workspace", request(Map.of(
                "workspace_id", "workspace-1", "workspace_name", "Workspace")));
        dispatcher.dispatch("project.register", request(Map.of(
                "workspace_id", "workspace-1", "project_id", "project-1", "project_name", "Project")));
        Path source = temp.resolve("target-src");
        Files.createDirectories(source);
        Files.writeString(source.resolve("subject.txt"), "subject");
        dispatcher.dispatch("project.register-target", request(Map.of(
                "project_id", "project-1", "target_id", "target-1", "target_name", "Target",
                "target_type", "GENERAL_SOFTWARE", "source_root", source.toString())));
    }

    @Test
    void registeredDeploymentIsReadableByProjectTargetAndDeploymentId() throws Exception {
        Map<String, Object> registered = result(dispatcher.dispatch(
                "deployment.register-target", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "deployment_target_id", "deploy-1", "environment_class", "PROD",
                        "deployment_root", temp.resolve("deployment-1").toString()))));
        assertEquals(4L, ((Number) registered.get("catalog_revision")).longValue());

        Map<String, Object> read = result(dispatcher.dispatch(
                "deployment.read-target", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "deployment_target_id", "deploy-1"))));
        ProductCatalog.RegisteredDeployment deployment =
                (ProductCatalog.RegisteredDeployment) read.get("registered_deployment");
        assertEquals("PROD", deployment.environmentClass());
        assertEquals("deploy-1", deployment.deploymentTargetId());
    }

    @Test
    void deploymentTargetIdMustBeUniqueAcrossTheCatalog() throws Exception {
        dispatcher.dispatch("deployment.register-target", request(Map.of(
                "project_id", "project-1", "target_id", "target-1",
                "deployment_target_id", "deploy-1", "environment_class", "PROD",
                "deployment_root", temp.resolve("deployment-1").toString())));

        IllegalArgumentException duplicate = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("deployment.register-target", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "deployment_target_id", "deploy-1", "environment_class", "STAGE",
                        "deployment_root", temp.resolve("deployment-2").toString()))));
        assertEquals("DEPLOYMENT_TARGET_EXISTS", duplicate.getMessage());
    }

    @Test
    void deploymentCannotBeRegisteredAgainstAnUnregisteredTarget() {
        // TenantRbacService requires the caller to already own "target:<project>:<target>" before
        // any workflow body runs, so an unregistered target fails closed at the RBAC boundary --
        // ProductCatalog's own UNKNOWN_TARGET check is a second, defense-in-depth layer beneath it.
        SecurityException denied = assertThrows(SecurityException.class,
                () -> dispatcher.dispatch("deployment.register-target", request(Map.of(
                        "project_id", "project-1", "target_id", "target-does-not-exist",
                        "deployment_target_id", "deploy-1", "environment_class", "PROD",
                        "deployment_root", temp.resolve("deployment-1").toString()))));
        assertEquals("TENANT_RESOURCE_BINDING_MISSING:target:project-1:target-does-not-exist", denied.getMessage());
    }

    @Test
    void environmentClassMustBeAKnownValue() {
        IllegalArgumentException invalid = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("deployment.register-target", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "deployment_target_id", "deploy-1", "environment_class", "PRODUCTION",
                        "deployment_root", temp.resolve("deployment-1").toString()))));
        assertEquals("DEPLOYMENT_ENVIRONMENT_CLASS_INVALID", invalid.getMessage());
    }

    @Test
    void readingAnUnregisteredDeploymentTargetFailsClosed() {
        // Same RBAC-boundary-first behavior as the registration test above: a deployment resource
        // that was never claimed has no owner binding, so TenantRbacService denies it before
        // ProductCatalog.requireDeployment's own UNKNOWN_DEPLOYMENT_TARGET check is reached.
        SecurityException denied = assertThrows(SecurityException.class,
                () -> dispatcher.dispatch("deployment.read-target", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "deployment_target_id", "deploy-does-not-exist"))));
        assertEquals("TENANT_RESOURCE_BINDING_MISSING:deployment:deploy-does-not-exist", denied.getMessage());
    }

    @Test
    void readingADeploymentTargetRegisteredUnderADifferentTargetFailsClosedAtTheCatalog() throws Exception {
        dispatcher.dispatch("deployment.register-target", request(Map.of(
                "project_id", "project-1", "target_id", "target-1",
                "deployment_target_id", "deploy-1", "environment_class", "PROD",
                "deployment_root", temp.resolve("deployment-1").toString())));
        Path otherSource = temp.resolve("other-target-src");
        Files.createDirectories(otherSource);
        Files.writeString(otherSource.resolve("subject.txt"), "subject");
        dispatcher.dispatch("project.register-target", request(Map.of(
                "project_id", "project-1", "target_id", "target-2", "target_name", "Target Two",
                "target_type", "GENERAL_SOFTWARE", "source_root", otherSource.toString())));

        // deploy-1 is owned by this tenant (claimed above) so RBAC lets the call through, but it
        // was registered under target-1, not target-2 -- ProductCatalog.requireDeployment must
        // still reject the (project, wrong target, deployment) triple as UNKNOWN_DEPLOYMENT_TARGET.
        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class,
                () -> dispatcher.dispatch("deployment.read-target", request(Map.of(
                        "project_id", "project-1", "target_id", "target-2",
                        "deployment_target_id", "deploy-1"))));
        assertEquals("UNKNOWN_DEPLOYMENT_TARGET", mismatch.getMessage());
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("result");
    }
}
