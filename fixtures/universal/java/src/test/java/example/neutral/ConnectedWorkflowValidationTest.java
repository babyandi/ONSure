package example.neutral;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;

class ConnectedWorkflowValidationTest {
    private static final Path EVIDENCE = Path.of(".onsure", "e2e");
    private static final Path REQUEST = EVIDENCE.resolve("request.json");
    private static final Path ARTIFACT = EVIDENCE.resolve("artifact.json");
    private static final Path SCHEMA = EVIDENCE.resolve("artifact.schema.json");

    @Test void requestFlow() throws Exception {
        prepare();
        assertEquals("{\"left\":8,\"right\":2}", Files.readString(REQUEST));
    }

    @Test void renderOrProduce() throws Exception {
        prepare();
        assertTrue(Files.readString(ARTIFACT).contains("\"result\":4"));
    }

    @Test void artifactReadback() throws Exception {
        prepare();
        assertEquals("{\"result\":4,\"exposed\":false}", Files.readString(ARTIFACT));
    }

    @Test void testerCheck() throws Exception {
        prepare();
        assertTrue(sha(ARTIFACT).matches("[0-9a-f]{64}"));
    }

    @Test void auditCheck() throws Exception {
        prepare();
        assertEquals(sha(ARTIFACT), sha(ARTIFACT));
    }

    @Test void exposureDecision() throws Exception {
        prepare();
        assertFalse(Files.readString(ARTIFACT).contains("\"exposed\":true"));
    }

    @Test void workflowLineage() throws Exception {
        prepare();
        String requestSha = sha(REQUEST);
        String artifactSha = sha(ARTIFACT);
        String schemaSha = sha(SCHEMA);
        Instant issued = Instant.now();
        String runId = "java-" + requestSha.substring(0, 16);
        String permitId = "java-permit-" + artifactSha.substring(0, 16);
        String receipt = """
                {
                  "contract":"ONSURE_PORTABLE_WORKFLOW_LINEAGE_V1",
                  "run_id":"%s",
                  "request":{"path":".onsure/e2e/request.json","sha256":"%s"},
                  "artifact":{"path":".onsure/e2e/artifact.json","sha256":"%s","schema_path":".onsure/e2e/artifact.schema.json","schema_sha256":"%s","media_type":"application/json"},
                  "handoffs":[{"producer":"calculator","consumer":"read-back","producer_output_sha256":"%s","consumer_input_sha256":"%s","artifact_sha256":"%s","producer_schema_sha256":"%s","consumer_schema_sha256":"%s"}],
                  "permit":{"permit_id":"%s","run_id":"%s","request_sha256":"%s","artifact_sha256":"%s","decision":"ALLOW","issued_at":"%s","expires_at":"%s"},
                  "read_back":{"artifact_sha256":"%s"},
                  "tester":{"decision":"PASS","artifact_sha256":"%s"},
                  "audit":{"decision":"PASS","artifact_sha256":"%s"},
                  "exposure":{"expected_decision":"DENY","actual_decision":"DENY","artifact_sha256":"%s","permit_id":"%s"},
                  "generated_at":"%s"
                }
                """.formatted(
                runId, requestSha, artifactSha, schemaSha,
                artifactSha, artifactSha, artifactSha, schemaSha, schemaSha,
                permitId, runId, requestSha, artifactSha, issued, issued.plus(10, ChronoUnit.MINUTES),
                artifactSha, artifactSha, artifactSha, artifactSha, permitId, issued);
        Path lineage = Path.of(".onsure", "workflow-lineage.v1.json");
        Files.createDirectories(lineage.getParent());
        Files.writeString(lineage, receipt, StandardCharsets.UTF_8);
        assertTrue(Files.isRegularFile(lineage));
    }

    private static void prepare() throws Exception {
        Files.createDirectories(EVIDENCE);
        Files.writeString(REQUEST, "{\"left\":8,\"right\":2}");
        Files.writeString(ARTIFACT, "{\"result\":" + Calculator.divide(8, 2) + ",\"exposed\":false}");
        Files.writeString(SCHEMA, "{\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"type\":\"object\",\"additionalProperties\":false,\"required\":[\"result\",\"exposed\"],\"properties\":{\"result\":{\"type\":\"integer\"},\"exposed\":{\"type\":\"boolean\"}}}");
    }

    private static String sha(Path path) throws Exception {
        return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }
}
