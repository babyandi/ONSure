package io.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

final class WorkflowLineageTestFixture {
    private static final ObjectMapper MAPPER = new ObjectMapper().findAndRegisterModules();

    private WorkflowLineageTestFixture() {}

    static Path write(Path root, String title) throws Exception {
        Path evidence = Files.createDirectories(root.resolve(".onsure/e2e"));
        Path request = evidence.resolve("request.json");
        Path artifact = evidence.resolve("artifact.json");
        Path schema = evidence.resolve("artifact.schema.json");
        Files.writeString(request, "{\"title\":\"" + title + "\"}");
        Files.writeString(artifact, "{\"title\":\"" + title + "\",\"exposed\":false}");
        Files.writeString(schema, """
                {"$schema":"https://json-schema.org/draft/2020-12/schema","type":"object",
                 "additionalProperties":false,"required":["title","exposed"],"properties":{
                   "title":{"type":"string","minLength":1},"exposed":{"type":"boolean"}}}
                """);
        String requestSha = Hashing.file(request);
        String artifactSha = Hashing.file(artifact);
        String schemaSha = Hashing.file(schema);
        String permitId = "permit-" + artifactSha.substring(0, 12);
        String runId = "run-" + requestSha.substring(0, 12);
        Instant issued = Instant.parse("2026-08-04T00:00:00Z");

        ObjectNode value = MAPPER.createObjectNode();
        value.put("contract", WorkflowLineageReceiptVerifier.CONTRACT);
        value.put("run_id", runId);
        value.putObject("request").put("path", ".onsure/e2e/request.json").put("sha256", requestSha);
        value.putObject("artifact")
                .put("path", ".onsure/e2e/artifact.json")
                .put("sha256", artifactSha)
                .put("schema_path", ".onsure/e2e/artifact.schema.json")
                .put("schema_sha256", schemaSha)
                .put("media_type", "application/json");
        ArrayNode handoffs = value.putArray("handoffs");
        handoffs.addObject().put("producer", "renderer").put("consumer", "read-back")
                .put("producer_output_sha256", artifactSha)
                .put("consumer_input_sha256", artifactSha)
                .put("artifact_sha256", artifactSha)
                .put("producer_schema_sha256", schemaSha)
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
        Path receipt = root.resolve(WorkflowLineageReceiptVerifier.RECEIPT_PATH);
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(receipt.toFile(), value);
        return receipt;
    }

    static ObjectNode read(Path receipt) throws Exception {
        return (ObjectNode) MAPPER.readTree(receipt.toFile());
    }

    static void write(Path receipt, ObjectNode value) throws Exception {
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(receipt.toFile(), value);
    }
}
