package kr.co.oruda.onsure.web.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.co.oruda.onsure.platform.EnterpriseWebReadService;
import kr.co.oruda.onsure.platform.ProductCatalog;
import kr.co.oruda.onsure.platform.ValidationModel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EnterpriseWebReadServiceTest {

    @TempDir
    Path temp;

    @Test
    void readsAuthoritativeProjectTargetAndPersistedEvidenceWithoutInventingAssurance() throws Exception {
        Path catalogRoot = temp.resolve("catalog");
        Path validationRoot = temp.resolve("validation");
        ProductCatalog catalog = catalogWithOneTarget(catalogRoot);

        Path run = validationRoot.resolve("T1").resolve("JOB1");
        Files.createDirectories(run);
        ValidationModel.Evidence evidence = new ValidationModel.Evidence(
                "EV1", "RUNTIME", "runtime-test", "a".repeat(64),
                Instant.parse("2026-08-29T00:03:00Z"), Map.of("bounded_result", "PASSED"));
        new ObjectMapper().findAndRegisterModules().writeValue(run.resolve("evidence.json").toFile(), List.of(evidence));

        EnterpriseWebReadService read = new EnterpriseWebReadService(catalogRoot, validationRoot);
        assertEquals(1, read.projects().size());
        assertEquals("Project One", read.project("P1").name());
        assertEquals(1, read.targets("P1").size());
        assertEquals("Payment API", read.target("P1", "T1").name());
        assertEquals(EnterpriseWebReadService.Availability.NOT_AVAILABLE, read.assurance("P1", "T1").availability());
        assertNull(read.assurance("P1", "T1").canonicalState());
        assertEquals(1, read.evidence("P1", "T1").size());
        assertEquals("EV1", read.evidence("P1", "T1").get(0).evidenceId());
        assertEquals("ONSURE_CORE_VALIDATION_STORE", read.evidence("P1", "T1").get(0).authority());
    }

    @Test
    void missingValidationStoreIsUnavailableRatherThanAuthoritativeEmptyEvidence() throws Exception {
        Path catalogRoot = temp.resolve("catalog-missing-validation");
        catalogWithOneTarget(catalogRoot);
        EnterpriseWebReadService read = new EnterpriseWebReadService(catalogRoot, temp.resolve("does-not-exist"));

        EnterpriseWebReadService.NotAvailableException error = assertThrows(
                EnterpriseWebReadService.NotAvailableException.class,
                () -> read.evidence("P1", "T1"));
        assertEquals("VALIDATION_STORE_NOT_AVAILABLE", error.getMessage());
    }

    @Test
    void missingCatalogIsUnavailableRatherThanAuthoritativeEmptyProjectList() {
        EnterpriseWebReadService read = new EnterpriseWebReadService(
                temp.resolve("missing-catalog"), temp.resolve("missing-validation"));
        EnterpriseWebReadService.NotAvailableException error = assertThrows(
                EnterpriseWebReadService.NotAvailableException.class, read::projects);
        assertEquals("PRODUCT_CATALOG_NOT_AVAILABLE", error.getMessage());
    }

    private ProductCatalog catalogWithOneTarget(Path catalogRoot) throws Exception {
        ProductCatalog catalog = new ProductCatalog(catalogRoot);
        catalog.registerWorkspace(new ProductCatalog.Workspace("WS1", "Workspace", Instant.parse("2026-08-29T00:00:00Z")));
        catalog.registerProject(new ProductCatalog.Project("P1", "WS1", "Project One", Instant.parse("2026-08-29T00:01:00Z")));
        ValidationModel.ValidationTarget target = new ValidationModel.ValidationTarget(
                "T1", "Payment API", ValidationModel.TargetType.GENERAL_SOFTWARE,
                temp.resolve("source"), "commit:abc123", "generic-manifest", "policy-default", "execution-default");
        catalog.registerTarget(new ProductCatalog.RegisteredTarget("P1", target, Instant.parse("2026-08-29T00:02:00Z")));
        return catalog;
    }
}
