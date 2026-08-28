package kr.co.oruda.onsure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import kr.co.oruda.onsure.platform.ProductCatalog;
import kr.co.oruda.onsure.platform.ValidationModel.TargetType;
import kr.co.oruda.onsure.platform.ValidationModel.ValidationTarget;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CoreCatalogReadModelControllerTest {
    @TempDir
    Path workspace;

    @Test
    void missingWorkspaceConfigurationFailsClosed() {
        var snapshot = new CoreCatalogReadModelController("").catalog();

        assertEquals("ONSURE_WEB_CATALOG_READ_MODEL_V1", snapshot.contract());
        assertEquals("NOT_CONFIGURED_NONFINAL", snapshot.state());
        assertFalse(snapshot.available());
        assertEquals("WORKSPACE_ROOT_NOT_CONFIGURED", snapshot.blockedReason());
        assertTrue(snapshot.projects().isEmpty());
        assertFalse(snapshot.finalClaimAllowed());
        assertFalse(snapshot.productionGo());
    }

    @Test
    void configuredWorkspaceReadsExistingCoreCatalogWithoutExposingSourceRoot() throws Exception {
        ProductCatalog catalog = new ProductCatalog(workspace.resolve(".onsure/product-catalog"));
        Instant created = Instant.parse("2026-08-28T00:00:00Z");
        catalog.registerWorkspace(new ProductCatalog.Workspace("WS-1", "ONSure", created));
        catalog.registerProject(new ProductCatalog.Project("PROJECT-1", "WS-1", "ONSure Web", created));
        ValidationTarget target = new ValidationTarget(
                "TARGET-1",
                "ONSure",
                TargetType.GENERAL_SOFTWARE,
                workspace.resolve("secret-local-source"),
                "sha256:" + "a".repeat(64),
                "GENERIC_MANIFEST_V1",
                "ONSURE_DEFAULT_POLICY_V1",
                "REGISTERED_REVIEWED");
        catalog.registerTarget(new ProductCatalog.RegisteredTarget("PROJECT-1", target, created));

        var snapshot = new CoreCatalogReadModelController(workspace.toString()).catalog();

        assertEquals("CORE_READ_MODEL_NONFINAL", snapshot.state());
        assertTrue(snapshot.available());
        assertEquals(3L, snapshot.catalogRevision());
        assertEquals(1, snapshot.workspaces().size());
        assertEquals(1, snapshot.projects().size());
        assertEquals(1, snapshot.targets().size());
        assertEquals("TARGET-1", snapshot.targets().get(0).targetId());
        assertEquals("sha256:" + "a".repeat(64), snapshot.targets().get(0).immutableSourceReference());
        assertFalse(snapshot.finalClaimAllowed());
        assertFalse(snapshot.independentVerificationComplete());
        assertFalse(snapshot.productionGo());
    }
}
