package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaDocumentMaterializerTest {
    private static final Path CATALOG = Path.of("contracts/oruda-execution-packages.v1.json");
    @TempDir Path temp;

    @Test
    void materializesAllEightySevenDocumentsIntoTheirPackagesAndDetectsTampering() throws Exception {
        Path source = createSourceSet(false);
        Path output = temp.resolve("materialized");
        OrudaDocumentMaterializer materializer = new OrudaDocumentMaterializer();
        var manifest = materializer.materialize(source, CATALOG, output);

        assertEquals(87, manifest.documentCount());
        assertEquals(8, manifest.packages().size());
        assertEquals(Decision.PASS, materializer.verify(output, CATALOG).decision());
        assertTrue(Files.isRegularFile(output.resolve(OrudaDocumentMaterializer.MANIFEST_FILE)));

        Path tampered = output.resolve("packages/ORU-PKG-08/documents/oruda_failure_loop_review_to_goal.md");
        Files.writeString(tampered, "tampered");
        assertEquals(Decision.FAIL, materializer.verify(output, CATALOG).decision());
    }

    @Test
    void missingDocumentAndExistingOutputFailClosed() throws Exception {
        Path incomplete = createSourceSet(true);
        OrudaDocumentMaterializer materializer = new OrudaDocumentMaterializer();
        assertThrows(IllegalArgumentException.class,
                () -> materializer.materialize(incomplete, CATALOG, temp.resolve("incomplete-output")));

        Path complete = createSourceSet(false);
        Path existing = temp.resolve("existing-output");
        Files.createDirectories(existing);
        assertThrows(IllegalArgumentException.class,
                () -> materializer.materialize(complete, CATALOG, existing));
    }

    private Path createSourceSet(boolean omitLast) throws Exception {
        Path source = temp.resolve(omitLast ? "source-incomplete" : "source-complete");
        Files.createDirectories(source);
        var catalog = new OrudaExecutionPackageCatalog().load(CATALOG);
        int index = 0;
        for (var executionPackage : catalog.packages()) {
            for (String filename : executionPackage.loopDocuments()) {
                index++;
                Files.writeString(source.resolve(filename), "# " + filename + "\ncontent=" + index + "\n");
            }
            for (String filename : executionPackage.supportingDocuments()) {
                index++;
                if (omitLast && filename.equals("oruda_pr_gate_checklist.md")) continue;
                Files.writeString(source.resolve(filename), "# " + filename + "\ncontent=" + index + "\n");
            }
        }
        return source;
    }
}
