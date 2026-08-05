package io.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StaticWorkflowInventoryTest {
    @TempDir Path temp;

    @Test
    void discoversCrossLanguageWorkflowCandidatesWithoutGrantingExecution() throws Exception {
        Files.writeString(temp.resolve("package.json"), """
                {"scripts":{"render":"node render.js","test:audit":"node audit.js"}}
                """);
        Files.writeString(temp.resolve("pom.xml"), """
                <project><modules><module>modules/api</module></modules></project>
                """);
        Files.createDirectories(temp.resolve("src"));
        Files.writeString(temp.resolve("src/Main.java"),
                "class Main { public static void main(String[] args) {} }");
        Files.writeString(temp.resolve("src/run.py"),
                "if __name__ == '__main__':\n    print('run')\n");
        Files.createDirectories(temp.resolve("contracts/openapi"));
        Files.writeString(temp.resolve("contracts/openapi/api.yaml"), """
                openapi: 3.1.0
                paths:
                  /runs:
                    post:
                      operationId: createRun
                      tags: [Runs]
                      requestBody:
                        content:
                          application/json:
                            schema: {$ref: '#/components/schemas/CreateRun'}
                      responses:
                        '201': {description: created}
                """);
        Files.createDirectories(temp.resolve("db/migration"));
        Files.writeString(temp.resolve("db/migration/V1__init.sql"), "select 1;");

        Map<String, Object> inventory = StaticWorkflowInventory.detect(temp);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> candidates = (List<Map<String, Object>>) inventory.get("candidates");

        assertEquals("DISCOVERY_ONLY_REVIEW_REQUIRED", inventory.get("execution_policy"));
        assertEquals(Hashing.tree(temp), inventory.get("source_digest"));
        assertEquals(6, ((Number) inventory.get("source_file_count")).intValue());
        assertEquals(false, inventory.get("auto_execute"));
        assertTrue(candidates.stream().map(value -> value.get("kind")).toList().containsAll(List.of(
                "NODE_SCRIPT", "MAVEN_PROJECT", "MAVEN_MODULE", "JAVA_ENTRYPOINT",
                "PYTHON_ENTRYPOINT", "OPENAPI_OPERATION", "DATABASE_MIGRATION")));
        assertTrue(candidates.stream().allMatch(value -> Boolean.TRUE.equals(value.get("review_required"))));
        assertTrue(candidates.stream().allMatch(value -> Boolean.FALSE.equals(value.get("auto_execute"))));
        assertFalse(candidates.toString().contains("node render.js"));
        Map<String, Object> operation = candidates.stream()
                .filter(value -> "OPENAPI_OPERATION".equals(value.get("kind")))
                .findFirst().orElseThrow();
        assertEquals("POST", operation.get("http_method"));
        assertEquals("/runs", operation.get("http_path"));
        assertEquals("CREATE", operation.get("lifecycle_action"));
        assertEquals(List.of("#/components/schemas/CreateRun"), operation.get("request_schema_refs"));
        assertEquals(List.of("201"), operation.get("response_statuses"));
    }

    @Test
    void treatsAnEmptySecurityAlternativeAsAnonymousAccess() throws Exception {
        Files.writeString(temp.resolve("openapi.yaml"), """
                openapi: 3.1.0
                security: [{bearerAuth: []}]
                paths:
                  /public-or-authenticated:
                    get:
                      security: [{}, {bearerAuth: []}]
                      responses: {'200': {description: ok}}
                  /authenticated:
                    get:
                      responses: {'200': {description: ok}}
                components:
                  securitySchemes: {bearerAuth: {type: http, scheme: bearer}}
                """);

        @SuppressWarnings("unchecked") List<Map<String, Object>> candidates =
                (List<Map<String, Object>>) StaticWorkflowInventory.detect(temp).get("candidates");
        Map<String, Object> anonymous = candidates.stream()
                .filter(value -> "/public-or-authenticated".equals(value.get("http_path")))
                .findFirst().orElseThrow();
        Map<String, Object> authenticated = candidates.stream()
                .filter(value -> "/authenticated".equals(value.get("http_path")))
                .findFirst().orElseThrow();

        assertEquals(false, anonymous.get("security_declared"));
        assertEquals(true, authenticated.get("security_declared"));
    }
}
