package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OpenApiSyntheticFixtureEngineTest {
    @TempDir Path temp;

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
    void blocksAuthenticationAndRequiredNonPathParametersUntilMaterialized() throws Exception {
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

        Path query = write("""
                openapi: 3.1.0
                info: {title: Test, version: '1'}
                paths:
                  /orders:
                    get:
                      parameters: [{name: tenant, in: query, required: true, schema: {type: string}}]
                      responses: {'200': {description: ok}}
                """);
        OpenApiSyntheticFixtureEngine queryEngine = new OpenApiSyntheticFixtureEngine(
                temp, candidate(query, "GET", "/orders"));
        IllegalArgumentException parameter = assertThrows(IllegalArgumentException.class,
                () -> queryEngine.prepare("GET", "/orders"));
        assertEquals("OPENAPI_REQUIRED_PARAMETER_UNSUPPORTED:QUERY:TENANT", parameter.getMessage());
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
