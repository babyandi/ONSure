package kr.co.oruda.onsure.sdk.v1;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.CatalogResult;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.PlanGeneration;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.ProjectRegistration;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.RegisteredTargetRef;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.Response;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.TargetRegistration;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.TargetType;
import kr.co.oruda.onsure.sdk.v1.ONSureSdkV1.WorkspaceRegistration;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ONSureSdkV1Test {
    @TempDir Path temp;

    @Test
    void typedSdkUsesRegisteredIdentityAndProductOwnedOutputs() throws Exception {
        ONSureSdkV1 sdk = new ONSureSdkV1(temp);
        Response<CatalogResult> workspace = sdk.registerWorkspace(
                new WorkspaceRegistration("workspace-001", "Workspace"));
        assertEquals(ONSureSdkV1.CONTRACT, workspace.contract());
        assertEquals(1L, workspace.result().revision());
        assertFalse(workspace.finalClaimAllowed());

        Response<CatalogResult> project = sdk.registerProject(
                new ProjectRegistration("workspace-001", "project-001", "Project"));
        assertEquals(2L, project.result().revision());

        Path source = temp.resolve("source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("README.md"), "typed SDK target\n");
        Files.writeString(source.resolve("onsure-target.json"), """
                {"contract":"ONSURE_TARGET_MANIFEST_V1","target_id":"target-001",
                 "target_type":"GENERAL_SOFTWARE","self_reported_final_decision":false,
                 "capabilities":[],"fixtures":[]}
                """);
        Response<CatalogResult> target = sdk.registerTarget(new TargetRegistration(
                "project-001", "target-001", "Target", TargetType.GENERAL_SOFTWARE, source));
        assertEquals(3L, target.result().revision());

        RegisteredTargetRef reference = new RegisteredTargetRef("project-001", "target-001");
        var learned = sdk.learnProgram(reference);
        assertEquals("target-001", learned.result().document().path("program_id").asText());
        assertTrue(learned.result().productOwnedPath().startsWith(temp));

        var plan = sdk.generatePlan(new PlanGeneration(reference, learned.result().productOwnedPath()));
        assertEquals("AWAITING_USER_APPROVAL",
                plan.result().document().path("approval").path("state").asText());
        assertEquals(temp.resolve(".onsure/plans/target-001-execution-plan.json").toAbsolutePath(),
                plan.result().productOwnedPath());
        assertFalse(plan.result().document().path("final_claim_allowed").asBoolean(true));
    }

    @Test
    void publicMethodsCannotAcceptRawRequestsOrAuthorityPaths() {
        for (Method method : ONSureSdkV1.class.getDeclaredMethods()) {
            if (!java.lang.reflect.Modifier.isPublic(method.getModifiers())) continue;
            assertFalse(method.getName().equals("dispatch"), "raw dispatch must not be public");
            for (Class<?> parameter : method.getParameterTypes()) {
                assertFalse(Map.class.isAssignableFrom(parameter), method.toString());
                assertFalse(JsonNode.class.isAssignableFrom(parameter), method.toString());
                assertFalse(parameter.getName().contains("ApprovalAuthorityPaths"), method.toString());
            }
        }
        for (Class<?> nested : ONSureSdkV1.class.getDeclaredClasses()) {
            if (!nested.isRecord()) continue;
            for (RecordComponent component : nested.getRecordComponents()) {
                String name = component.getName().toLowerCase();
                assertFalse(name.contains("trustedkey") || name.contains("replayledger")
                        || name.contains("authorityroot") || name.contains("outputfile"),
                        nested.getSimpleName() + "." + component.getName());
            }
        }
    }

    @Test
    void requestRecordsFailClosedBeforeDispatch() {
        assertThrows(IllegalArgumentException.class,
                () -> new WorkspaceRegistration("../escape", "Workspace"));
        assertThrows(IllegalArgumentException.class,
                () -> new RegisteredTargetRef("project", ""));
        assertThrows(NullPointerException.class,
                () -> new TargetRegistration("project", "target", "Target", null, temp));
        assertTrue(Arrays.stream(ONSureSdkV1.PlanApproval.class.getRecordComponents())
                .noneMatch(component -> component.getName().contains("Registry")));
    }
}
