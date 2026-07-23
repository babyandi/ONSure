package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaExecutionPackageCatalogTest {
    private static final Path CATALOG = Path.of("contracts/oruda-execution-packages.v1.json");

    @Test
    void classifiesAllEightySevenDocumentsExactlyOnceIntoEightExecutionPackages() throws Exception {
        var catalog = new OrudaExecutionPackageCatalog().load(CATALOG);

        assertEquals("oruda_failure_loop_review_to_goal.md", catalog.authorityEntry());
        assertEquals(87, catalog.totalDocuments());
        assertEquals(64, catalog.loopDocuments());
        assertEquals(23, catalog.supportingDocuments());
        assertEquals(8, catalog.packages().size());
        assertEquals("STOP_BLOCKED_BY_TRUE_EXHAUSTION_DECISION", catalog.stopClass());
        assertEquals("EXECUTION_REQUIRED_NEXT", catalog.nextAction());
        assertEquals("NOT_ALLOWED", catalog.finalLock());
        assertTrue(catalog.packages().stream().noneMatch(value -> value.automaticFinalLock()));

        Set<String> identifiers = catalog.packages().stream()
                .map(OrudaExecutionPackageCatalog.ExecutionPackage::packageId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "ORU-PKG-01", "ORU-PKG-02", "ORU-PKG-03", "ORU-PKG-04",
                "ORU-PKG-05", "ORU-PKG-06", "ORU-PKG-07", "ORU-PKG-08"), identifiers);
    }

    @Test
    void mvfAndFinalDecisionDocumentsArePlacedInTheirExecutionPackages() throws Exception {
        var catalog = new OrudaExecutionPackageCatalog().load(CATALOG);
        var fixturePackage = catalog.packages().stream()
                .filter(value -> value.packageId().equals("ORU-PKG-03"))
                .findFirst().orElseThrow();
        var finalPackage = catalog.packages().stream()
                .filter(value -> value.packageId().equals("ORU-PKG-08"))
                .findFirst().orElseThrow();

        assertTrue(fixturePackage.loopDocuments().contains(
                "oruda_loop_16_minimum_viable_fixture_set.md"));
        assertTrue(finalPackage.loopDocuments().contains(
                "oruda_loop_64_true_exhaustion_decision_record.md"));
        assertTrue(finalPackage.supportingDocuments().contains(
                "oruda_failure_loop_review_to_goal.md"));
        assertFalse(finalPackage.automaticFinalLock());
    }

    @Test
    void duplicateDocumentAssignmentFailsClosed(@TempDir Path temp) throws Exception {
        String source = Files.readString(CATALOG);
        String duplicate = source.replace(
                "\"supporting_documents\": [],",
                "\"supporting_documents\": [\"oruda_loop_01_prerun_dryrun_check.md\"],");
        Path file = temp.resolve("duplicate.json");
        Files.writeString(file, duplicate);

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> new OrudaExecutionPackageCatalog().load(file));
        assertTrue(error.getMessage().startsWith("ORUDA_DOCUMENT_ASSIGNED_MULTIPLE_TIMES"));
    }
}
