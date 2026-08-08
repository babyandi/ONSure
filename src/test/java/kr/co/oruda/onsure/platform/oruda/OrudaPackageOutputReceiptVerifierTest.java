package kr.co.oruda.onsure.platform.oruda;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.co.oruda.onsure.assurance.Decision;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OrudaPackageOutputReceiptVerifierTest {
    private final ObjectMapper mapper = new ObjectMapper();
    @TempDir Path temp;

    @Test
    void writerSealsCanonicalEvidenceAndVerifierDetectsTampering() throws Exception {
        Path run = temp.resolve("run");
        Files.createDirectories(run);
        writeEvidence(run, "ORU-PKG-03", "fixture_registry", "ORUDA", "PASS");

        Path receipt = new OrudaPackageOutputReceiptWriter().write(
                run, "ORU-PKG-03", "fixture_registry", "ORUDA", "job-1", "PASS");
        var verifier = new OrudaPackageOutputReceiptVerifier();
        assertEquals(Decision.PASS, verifier.verify(
                run, receipt, "ORUDA", "job-1", "ORU-PKG-03", "fixture_registry").result().decision());

        Path evidence = OrudaPackageOutputReceiptVerifier.expectedEvidencePath(
                run, "ORU-PKG-03", "fixture_registry");
        Files.writeString(evidence, "tampered");
        assertEquals(Decision.FAIL, verifier.verify(
                run, receipt, "ORUDA", "job-1", "ORU-PKG-03", "fixture_registry").result().decision());
    }

    @Test
    void writerRejectsSemanticIdentityMismatch() throws Exception {
        Path run = temp.resolve("mismatch");
        Files.createDirectories(run);
        writeEvidence(run, "ORU-PKG-03", "fixture_registry", "OTHER", "PASS");
        assertThrows(IllegalArgumentException.class, () -> new OrudaPackageOutputReceiptWriter().write(
                run, "ORU-PKG-03", "fixture_registry", "ORUDA", "job-1", "PASS"));
    }

    private void writeEvidence(Path run, String packageId, String outputId,
            String targetId, String decision) throws Exception {
        Path evidenceFile = OrudaPackageOutputReceiptVerifier.expectedEvidencePath(
                run, packageId, outputId);
        Files.createDirectories(evidenceFile.getParent());
        Map<String, Object> semantic = new LinkedHashMap<>();
        semantic.put("package_id", packageId);
        semantic.put("output_id", outputId);
        semantic.put("target_id", targetId);
        semantic.put("decision", decision);
        mapper.writeValue(evidenceFile.toFile(), Map.of(
                "contract", "ONSURE_ORUDA_PACKAGE_OUTPUT_EVIDENCE_V1",
                "semantic_payload", semantic));
    }
}
