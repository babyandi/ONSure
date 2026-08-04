package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class WorkflowLineageReceiptVerifierTest {
    @TempDir Path temp;

    @Test
    void verifiesActualArtifactSchemaHandoffPermitAndDecisions() throws Exception {
        WorkflowLineageTestFixture.write(temp, "Neutral E2E");

        var result = new WorkflowLineageReceiptVerifier().verify(temp);

        assertEquals(UniversalValidationProfile.Outcome.PASS_NONFINAL, result.outcome());
        assertEquals("WORKFLOW_LINEAGE_DIGEST_SCHEMA_PERMIT_VERIFIED", result.reason());
        assertTrue(result.output().contains("handoff_count=1"));
    }

    @Test
    void rejectsArtifactMutationAfterReceiptWasWritten() throws Exception {
        WorkflowLineageTestFixture.write(temp, "Before");
        Files.writeString(temp.resolve(".onsure/e2e/artifact.json"),
                "{\"title\":\"After\",\"exposed\":false}");

        var result = new WorkflowLineageReceiptVerifier().verify(temp);

        assertEquals(UniversalValidationProfile.Outcome.FAIL, result.outcome());
        assertTrue(result.output().contains("ARTIFACT_SHA256_MISMATCH"));
    }

    @Test
    void rejectsConsumerDigestMismatchAndReusedHandoff() throws Exception {
        Path receipt = WorkflowLineageTestFixture.write(temp, "Neutral");
        var value = WorkflowLineageTestFixture.read(receipt);
        ((com.fasterxml.jackson.databind.node.ObjectNode) value.path("handoffs").get(0))
                .put("consumer_input_sha256", "d".repeat(64));
        value.withArray("handoffs").add(value.path("handoffs").get(0).deepCopy());
        WorkflowLineageTestFixture.write(receipt, value);

        var result = new WorkflowLineageReceiptVerifier().verify(temp);

        assertEquals(UniversalValidationProfile.Outcome.FAIL, result.outcome());
        assertTrue(result.output().contains("CONSUMER_INPUT_SHA256_MISMATCH"));
        assertTrue(result.output().contains("HANDOFF_REUSED"));
    }

    @Test
    void rejectsArtifactThatDoesNotConformToBoundSchema() throws Exception {
        WorkflowLineageTestFixture.write(temp, "");

        var result = new WorkflowLineageReceiptVerifier().verify(temp);

        assertEquals(UniversalValidationProfile.Outcome.FAIL, result.outcome());
        assertTrue(result.output().contains("SCHEMA_MIN_LENGTH:$/title"));
    }

    @Test
    void executableLineageStepCannotPassWithoutReadBackReceipt() throws Exception {
        var step = new UniversalValidationProfile.Step(
                "node-scripts.test-lineage", UniversalValidationProfile.Phase.END_TO_END_LINEAGE,
                UniversalValidationProfile.StepKind.WORKFLOW_LINEAGE, true,
                List.of("npm", "--offline", "run", "test:lineage"), Path.of(""),
                Duration.ofMinutes(2), List.of());
        var commandPass = new UniversalValidationRunner.StepExecution(
                UniversalValidationProfile.Outcome.PASS_NONFINAL, 0, "script passed", false, "EXECUTED");

        var missing = UniversalValidationRunner.verifyExecutableEvidence(step, commandPass, temp);
        assertEquals(UniversalValidationProfile.Outcome.FAIL, missing.outcome());
        assertTrue(missing.output().contains("RECEIPT_FILE_MISSING"));

        WorkflowLineageTestFixture.write(temp, "Neutral");
        var verified = UniversalValidationRunner.verifyExecutableEvidence(step, commandPass, temp);
        assertEquals(UniversalValidationProfile.Outcome.PASS_NONFINAL, verified.outcome());
        assertEquals("WORKFLOW_LINEAGE_DIGEST_SCHEMA_PERMIT_VERIFIED", verified.reason());
        assertTrue(verified.output().contains("script passed"));
    }

    @Test
    void rejectsPermitSubjectReuseExposureMismatchAndInvalidTimeWindow() throws Exception {
        Path receipt = WorkflowLineageTestFixture.write(temp, "Permit binding");
        var value = WorkflowLineageTestFixture.read(receipt);
        ((com.fasterxml.jackson.databind.node.ObjectNode) value.path("permit"))
                .put("run_id", "reused-other-run")
                .put("request_sha256", "f".repeat(64))
                .put("artifact_sha256", "e".repeat(64));
        ((com.fasterxml.jackson.databind.node.ObjectNode) value.path("exposure"))
                .put("permit_id", "reused-other-permit");
        value.put("generated_at", "2026-08-04T01:00:00Z");
        WorkflowLineageTestFixture.write(receipt, value);

        var result = new WorkflowLineageReceiptVerifier().verify(temp);

        assertEquals(UniversalValidationProfile.Outcome.FAIL, result.outcome());
        assertTrue(result.output().contains("PERMIT_RUN_ID_MISMATCH"));
        assertTrue(result.output().contains("PERMIT_REQUEST_SHA256_MISMATCH"));
        assertTrue(result.output().contains("PERMIT_ARTIFACT_SHA256_MISMATCH"));
        assertTrue(result.output().contains("EXPOSURE_PERMIT_MISMATCH"));
        assertTrue(result.output().contains("PERMIT_TIME_WINDOW_INVALID"));
    }
}
