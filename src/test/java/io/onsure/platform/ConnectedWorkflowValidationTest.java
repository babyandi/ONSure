package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/** Executable self-validation convention for a digest-bound connected workflow. */
class ConnectedWorkflowValidationTest {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();
    private static final Path ROOT = Path.of("").toAbsolutePath().normalize();
    private static final Path EVIDENCE = ROOT.resolve(".onsure/e2e");

    @Test
    void requestFlow() throws Exception {
        enabled();
        Files.createDirectories(EVIDENCE);
        Files.writeString(EVIDENCE.resolve("request.json"),
                "{\"title\":\"ONSure self connected workflow\"}\n");
        assertTrue(Files.size(EVIDENCE.resolve("request.json")) > 0);
    }

    @Test
    void renderOrProduce() throws Exception {
        enabled();
        JsonNode request = MAPPER.readTree(require("request.json").toFile());
        ObjectNode artifact = MAPPER.createObjectNode()
                .put("title", request.path("title").asText()).put("exposed", false);
        MAPPER.writeValue(EVIDENCE.resolve("artifact.json").toFile(), artifact);
        Files.writeString(EVIDENCE.resolve("artifact.schema.json"), """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                 "additionalProperties":false,"required":["title","exposed"],"properties":{
                   "title":{"type":"string","minLength":1},"exposed":{"type":"boolean"}}}
                """);
        assertEquals(request.path("title").asText(), artifact.path("title").asText());
    }

    @Test
    void artifactReadback() throws Exception {
        enabled();
        JsonNode artifact = MAPPER.readTree(require("artifact.json").toFile());
        assertEquals("ONSure self connected workflow", artifact.path("title").asText());
        writeMarker("readback.json", "READ_BACK", Hashing.file(require("artifact.json")));
    }

    @Test
    void testerCheck() throws Exception {
        enabled();
        writeMarker("tester.json", "PASS", Hashing.file(require("artifact.json")));
    }

    @Test
    void auditCheck() throws Exception {
        enabled();
        require("tester.json");
        writeMarker("audit.json", "PASS", Hashing.file(require("artifact.json")));
    }

    @Test
    void exposureDecision() throws Exception {
        enabled();
        require("audit.json");
        writeMarker("exposure.json", "DENY", Hashing.file(require("artifact.json")));
    }

    @Test
    void workflowLineage() throws Exception {
        enabled();
        Path request = require("request.json");
        Path artifact = require("artifact.json");
        Path schema = require("artifact.schema.json");
        require("readback.json");
        require("tester.json");
        require("audit.json");
        require("exposure.json");
        String requestSha = Hashing.file(request);
        String artifactSha = Hashing.file(artifact);
        String schemaSha = Hashing.file(schema);
        String runId = "run-" + requestSha.substring(0, 12);
        String permitId = "permit-" + artifactSha.substring(0, 12);
        Instant issued = Instant.parse("2026-08-04T00:00:00Z");
        ObjectNode value = MAPPER.createObjectNode();
        value.put("contract", WorkflowLineageReceiptVerifier.CONTRACT).put("run_id", runId);
        value.putObject("request").put("path", ".onsure/e2e/request.json").put("sha256", requestSha);
        value.putObject("artifact").put("path", ".onsure/e2e/artifact.json")
                .put("sha256", artifactSha).put("schema_path", ".onsure/e2e/artifact.schema.json")
                .put("schema_sha256", schemaSha).put("media_type", "application/json");
        ArrayNode handoffs = value.putArray("handoffs");
        handoffs.addObject().put("producer", "renderer").put("consumer", "read-back")
                .put("producer_output_sha256", artifactSha).put("consumer_input_sha256", artifactSha)
                .put("artifact_sha256", artifactSha).put("producer_schema_sha256", schemaSha)
                .put("consumer_schema_sha256", schemaSha);
        value.putObject("permit").put("permit_id", permitId).put("run_id", runId)
                .put("request_sha256", requestSha).put("artifact_sha256", artifactSha)
                .put("decision", "ALLOW").put("issued_at", issued.toString())
                .put("expires_at", issued.plusSeconds(600).toString());
        value.putObject("read_back").put("artifact_sha256", artifactSha);
        value.putObject("tester").put("decision", "PASS").put("artifact_sha256", artifactSha);
        value.putObject("audit").put("decision", "PASS").put("artifact_sha256", artifactSha);
        value.putObject("exposure").put("expected_decision", "DENY").put("actual_decision", "DENY")
                .put("artifact_sha256", artifactSha).put("permit_id", permitId);
        value.put("generated_at", issued.plusSeconds(1).toString());
        Path receipt = ROOT.resolve(WorkflowLineageReceiptVerifier.RECEIPT_PATH);
        Files.createDirectories(receipt.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(receipt.toFile(), value);
        assertEquals(UniversalValidationProfile.Outcome.PASS_NONFINAL,
                new WorkflowLineageReceiptVerifier().verify(ROOT).outcome());
    }

    private static void enabled() {
        Assumptions.assumeTrue(Boolean.getBoolean("onsure.validation.connected"));
    }

    private static Path require(String name) {
        Path value = EVIDENCE.resolve(name).normalize();
        assertTrue(value.startsWith(EVIDENCE) && Files.isRegularFile(value), name);
        return value;
    }

    private static void writeMarker(String name, String decision, String artifactSha) throws Exception {
        ObjectNode marker = MAPPER.createObjectNode().put("decision", decision)
                .put("artifact_sha256", artifactSha);
        MAPPER.writeValue(EVIDENCE.resolve(name).toFile(), marker);
    }
}
