package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.platform.ProductCatalog.Project;
import io.onsure.platform.ProductCatalog.RegisteredTarget;
import io.onsure.platform.ProductCatalog.Workspace;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ProductCatalogIsolationTest {
    @TempDir Path temp;

    @Test
    void sameIdsAreIsolatedByOrganizationAndCrossTenantReferencesFail() throws Exception {
        ProductCatalog catalog = new ProductCatalog(temp.resolve("catalog"));
        catalog.registerWorkspace(new Workspace("org-a", "workspace", "A", Instant.now()));
        catalog.registerWorkspace(new Workspace("org-b", "workspace", "B", Instant.now()));
        catalog.registerProject(new Project(
                "org-a", "project", "workspace", "Project A", Instant.now()));
        catalog.registerProject(new Project(
                "org-b", "project", "workspace", "Project B", Instant.now()));

        Path targetRoot = temp.resolve("target");
        Files.createDirectories(targetRoot);
        ValidationTarget a = target("target", targetRoot);
        ValidationTarget b = target("target", targetRoot);
        catalog.registerTarget(new RegisteredTarget("org-a", "project", a, Instant.now()));
        catalog.registerTarget(new RegisteredTarget("org-b", "project", b, Instant.now()));

        assertEquals("target", catalog.requireTarget("org-a", "target").targetId());
        assertEquals("target", catalog.requireTarget("org-b", "target").targetId());
        assertEquals(1, catalog.targets("org-a", "project").size());
        assertEquals(1, catalog.targets("org-b", "project").size());

        assertThrows(IllegalArgumentException.class, () -> catalog.registerProject(new Project(
                "org-c", "project-c", "workspace", "Invalid", Instant.now())));
        assertThrows(IllegalArgumentException.class, () -> catalog.registerTarget(
                new RegisteredTarget("org-c", "project", a, Instant.now())));
        assertTrue(Files.isRegularFile(temp.resolve("catalog/.catalog.lock")));
    }

    private static ValidationTarget target(String id, Path root) {
        return new ValidationTarget(
                id, id, TargetType.GENERAL_SOFTWARE, root,
                "sha256:" + "a".repeat(64), GenericManifestTargetAdapter.ID,
                "policy", "DECLARATIVE_ONLY");
    }
}
