package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiSyntheticFixtureEngineTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void blocksExternalSchemaReferences() throws Exception {
        Path source = write("""
                openapi: 3.1.0
                info: {title: Test, version: '1'}
                paths:
                  /orders:
                    post:
                      requestBody:
                        content:
                          application/json:
                            schema: {$ref: 'https://example.invalid/customer-schema.json'}
                      responses: {'204': {description: ok}}
                """);
        OpenApiSyntheticFixtureEngine engine = new OpenApiSyntheticFixtureEngine(temp, candidate(source, "POST", "/orders"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.prepare("POST", "/orders"));
        assertEquals("OPENAPI_EXTERNAL_REF_BLOCKED", error.getMessage());
    }

    @Test
    void blocksUnconstrainedPathTemplatesAndUntrustedPatterns() throws Exception {
        Path source = write("""
                openapi: 3.1.0
                info: {title: Test, version: '1'}
                paths:
                  /orders/{id}:
                    post:
                      parameters:
                        - {name: id, in: path, required: true, schema: {type: string, pattern: '^[0-9]+$'}}
                      requestBody:
                        content: {application/json: {schema: {type: object}}}
                      responses: {'204': {description: ok}}
                """);
        OpenApiSyntheticFixtureEngine engine = new OpenApiSyntheticFixtureEngine(
                temp, candidate(source, "POST", "/orders/{id}"));
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> engine.prepare("POST", "/orders/{id}"));
        assertEquals("OPENAPI_PATTERN_FIXTURE_REVIEW_REQUIRED", error.getMessage());
    }

    @Test
    void rejectsChangedOpenApiSourceBeforePreparingAnyRequest() throws Exception {
        Path source = write("""
                openapi: 3.1.0
                info: {title: Test, version: '1'}
                paths: {/health: {get: {responses: {'204': {description: ok}}}}}
                """);
        Map<String, Object> candidate = candidate(source, "GET", "/health");
        Files.writeString(source, Files.readString(source) + "\n# drift\n");
        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> new OpenApiSyntheticFixtureEngine(temp, candidate));
        assertEquals("OPENAPI_SOURCE_FILE_STALE", error.getMessage());
    }

    @Test
    void blocksAuthenticationUntilReferencedAndMaterializesRequiredNonPathParameters() throws Exception {
        Path secured = write("""
                openapi: 3.1.0
                info: {title: Test, version: '1'}
                security: [{bearerAuth: []}]
                paths: {/orders: {get: {responses: {'200': {description: ok}}}}}
                components:
                  securitySchemes: {bearerAuth: {type: http, scheme: bearer}}
                """);
        OpenApiSyntheticFixtureEngine securedEngine = new OpenApiSyntheticFixtureEngine(
                temp, candidate(secured, "GET", "/orders"));
        IllegalArgumentException auth = assertThrows(IllegalArgumentException.class,
                () -> securedEngine.prepare("GET", "/orders"));
        assertEquals("OPENAPI_AUTHENTICATION_CONTEXT_NOT_MATERIALIZED", auth.getMessage());
        IllegalArgumentException injection = assertThrows(IllegalArgumentException.class,
                () -> securedEngine.prepare("GET", "/orders", Map.of("ONSURE_TEST_AUTH", "token\r\nInjected: yes"),
                        Map.of("authentication", "env:ONSURE_TEST_AUTH")));
        assertEquals("OPENAPI_HEADER_VALUE_INVALID", injection.getMessage());

        Path query = write("""
                openapi: 3.1.0
                info: {title: Test, version: '1'}
                paths:
                  /orders:
                    get:
                      parameters:
                        - {name: tenant, in: query, required: true, schema: {type: string}}
                        - {name: X-Trace, in: header, required: true, schema: {type: string}}
                        - {name: session, in: cookie, required: true, schema: {type: string}}
                      responses: {'200': {description: ok}}
                """);
        OpenApiSyntheticFixtureEngine queryEngine = new OpenApiSyntheticFixtureEngine(
                temp, candidate(query, "GET", "/orders"));
        OpenApiSyntheticFixtureEngine.PreparedRequest prepared = queryEngine.prepare("GET", "/orders");
        assertEquals("tenant=ONSURE_SYNTHETIC", prepared.query());
        assertEquals("ONSURE_SYNTHETIC", prepared.headers().get("X-Trace"));
        assertEquals("session=ONSURE_SYNTHETIC", prepared.headers().get("Cookie"));
        assertEquals(java.util.List.of("tenant"), prepared.queryParameterNames());
        assertEquals(java.util.List.of("session"), prepared.cookieNames());

        OpenApiSyntheticFixtureEngine.PreparedRequest bound = queryEngine.prepare(
                "GET", "/orders", Map.of(), Map.of(),
                new OpenApiSyntheticFixtureEngine.BoundRequestValues(
                        Map.of(), Map.of("tenant", "tenant-bound-42"),
                        Map.of("X-Trace", "trace-bound-42")));
        assertEquals("tenant=tenant-bound-42", bound.query());
        assertEquals("trace-bound-42", bound.headers().get("X-Trace"));
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () ->
                queryEngine.prepare("GET", "/orders", Map.of(), Map.of(),
                        new OpenApiSyntheticFixtureEngine.BoundRequestValues(
                                Map.of(), Map.of("unknown", "value"), Map.of())));
        assertEquals("OPENAPI_BOUND_QUERY_PARAMETER_UNKNOWN", unknown.getMessage());
        IllegalArgumentException boundInjection = assertThrows(IllegalArgumentException.class, () ->
                queryEngine.prepare("GET", "/orders", Map.of(), Map.of(),
                        new OpenApiSyntheticFixtureEngine.BoundRequestValues(
                                Map.of(), Map.of(), Map.of("X-Trace", "value\r\ninjected"))));
        assertEquals("OPENAPI_BOUND_HEADER_PARAMETER_INVALID:X-TRACE", boundInjection.getMessage());
    }

    @Test
    void injectsBoundBodyScalarThenRevalidatesTheCompleteRequestSchema() throws Exception {
        Path source = write("""
                openapi: 3.1.0
                info: {title: Test, version: '1'}
                paths:
                  /orders:
                    patch:
                      requestBody:
                        required: true
                        content:
                          application/json:
                            schema:
                              type: object
                              required: [revision]
                              additionalProperties: false
                              properties: {revision: {type: string, minLength: 3}}
                      responses: {'200': {description: ok}}
                """);
        OpenApiSyntheticFixtureEngine engine = new OpenApiSyntheticFixtureEngine(
                temp, candidate(source, "PATCH", "/orders"));
        OpenApiSyntheticFixtureEngine.PreparedRequest prepared = engine.prepare(
                "PATCH", "/orders", Map.of(), Map.of(),
                new OpenApiSyntheticFixtureEngine.BoundRequestValues(
                        Map.of(), Map.of(), Map.of(),
                        Map.of("/revision", mapper.valueToTree("revision-bound-42"))));
        assertEquals("revision-bound-42", mapper.readTree(prepared.body()).path("revision").asText());

        IllegalArgumentException mismatch = assertThrows(IllegalArgumentException.class, () -> engine.prepare(
                "PATCH", "/orders", Map.of(), Map.of(),
                new OpenApiSyntheticFixtureEngine.BoundRequestValues(
                        Map.of(), Map.of(), Map.of(), Map.of("/revision", mapper.valueToTree(42)))));
        assertEquals("OPENAPI_BOUND_BODY_SCHEMA_MISMATCH", mismatch.getMessage());
        IllegalArgumentException unknown = assertThrows(IllegalArgumentException.class, () -> engine.prepare(
                "PATCH", "/orders", Map.of(), Map.of(),
                new OpenApiSyntheticFixtureEngine.BoundRequestValues(
                        Map.of(), Map.of(), Map.of(), Map.of("/unknown", mapper.valueToTree("value")))));
        assertEquals("OPENAPI_BOUND_BODY_POINTER_UNKNOWN", unknown.getMessage());
    }

    private Path write(String value) throws Exception {
        Path source = temp.resolve("openapi.yaml");
        Files.writeString(source, value);
        return source;
    }

    private static Map<String, Object> candidate(Path source, String method, String route) throws Exception {
        return Map.of(
                "openapi_source_path", source.getFileName().toString(),
                "openapi_source_sha256", Hashing.file(source),
                "http_method", method, "http_path", route);
    }
}
