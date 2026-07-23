package io.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.onsure.assurance.Decision;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaPackageExecutionRegistryTest {
    private static final Path CATALOG = Path.of("contracts/oruda-execution-packages.v1.json");
    @TempDir Path temp;

    @Test
    void missingPackageOutputsRemainExplicitlyNotRun() throws Exception {
        Path run = createRun("not-run");
        OrudaPackageExecutionRegistry registry = new OrudaPackageExecutionRegistry();
        var sealed = registry.seal(run, CATALOG, "ORUDA", "job-not-run");

        assertEquals(8, sealed.packages().size());
        assertTrue(sealed.packages().stream().allMatch(value -> "NOT_RUN".equals(value.status())));
        assertFalse(registry.allPackagesPass(sealed));
        assertEquals(Decision.PASS, registry.verify(run, CATALOG, "ORUDA", "job-not-run").decision());
        assertTrue(Files.readString(run.resolve("manifest.sha256"))
                .contains(OrudaPackageExecutionRegistry.FILE_NAME));
    }

    @Test
    void allPackageOutputsCanBeSealedAndEvidenceTamperingFailsClosed() throws Exception {
        Path run = createRun("pass");
        var catalog = new OrudaExecutionPackageCatalog().load(CATALOG);
        for (var executionPackage : catalog.packages()) {
            for (String outputId : executionPackage.requiredOutputs()) {
                OrudaCandidateTestSupport.writePackageOutputReceipt(
                        run, "ORUDA", "job-pass", executionPackage.packageId(), outputId, "PASS");
            }
        }

        OrudaPackageExecutionRegistry registry = new OrudaPackageExecutionRegistry();
        var sealed = registry.seal(run, CATALOG, "ORUDA", "job-pass");
        assertTrue(registry.allPackagesPass(sealed));
        assertEquals(Decision.PASS, registry.verify(run, CATALOG, "ORUDA", "job-pass").decision());

        Path tampered = OrudaPackageOutputReceiptVerifier.expectedEvidencePath(
                run, "ORU-PKG-03", "fixture_registry");
        Files.writeString(tampered, "tampered");
        assertEquals(Decision.FAIL, registry.verify(run, CATALOG, "ORUDA", "job-pass").decision());
    }

    @Test
    void nonPassOutputReceiptCannotBecomePackagePass() throws Exception {
        Path run = createRun("fail");
        var catalog = new OrudaExecutionPackageCatalog().load(CATALOG);
        for (var executionPackage : catalog.packages()) {
            for (String outputId : executionPackage.requiredOutputs()) {
                String decision = executionPackage.packageId().equals("ORU-PKG-04")
                        && outputId.equals("canonical_execution_trace") ? "FAIL" : "PASS";
                OrudaCandidateTestSupport.writePackageOutputReceipt(
                        run, "ORUDA", "job-fail", executionPackage.packageId(), outputId, decision);
            }
        }
        var sealed = new OrudaPackageExecutionRegistry().seal(run, CATALOG, "ORUDA", "job-fail");
        assertFalse(new OrudaPackageExecutionRegistry().allPackagesPass(sealed));
        assertTrue(sealed.packages().stream().anyMatch(value ->
                value.packageId().equals("ORU-PKG-04") && value.status().equals("FAIL")));
    }

    private Path createRun(String name) throws Exception {
        Path run = temp.resolve(name);
        Files.createDirectories(run);
        Files.writeString(run.resolve("manifest.sha256"), "");
        return run;
    }
}
