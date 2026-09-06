package kr.co.oruda.onsure.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.test.web.server.LocalServerPort;

/** Real embedded-server checks for the Enterprise Web security/runtime boundary. */
@SpringBootTest(
        webEnvironment = WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.security.user.name=runtime-reviewer",
            "spring.security.user.password=runtime-secret"
        })
class OnsureWebRuntimeIntegrationTest {

    @LocalServerPort
    int port;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .build();

    @Test
    void publicHealthIsExplicitlyNonfinalAndCarriesSecurityHeaders() throws Exception {
        HttpResponse<String> response = get("/healthz", null);

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("ONSURE_WEB_VERTICAL_SLICE_NONFINAL"));
        assertEquals("DENY", response.headers().firstValue("X-Frame-Options").orElse(null));
        assertEquals("nosniff", response.headers().firstValue("X-Content-Type-Options").orElse(null));
        String csp = response.headers().firstValue("Content-Security-Policy").orElse("");
        assertTrue(csp.contains("frame-ancestors 'none'"));
        assertTrue(csp.contains("default-src 'self'"));
    }

    @Test
    void dashboardAndCoreApiRequireAuthenticationOnRealServer() throws Exception {
        HttpResponse<String> dashboard = get("/", null);
        HttpResponse<String> api = get("/api/web/v1/projects", null);

        assertEquals(302, dashboard.statusCode());
        assertTrue(dashboard.headers().firstValue("Location").orElse("").contains("/login"));
        assertEquals(302, api.statusCode());
        assertTrue(api.headers().firstValue("Location").orElse("").contains("/login"));
    }

    @Test
    void attackerOriginIsNotGrantedCorsAccess() throws Exception {
        HttpResponse<String> response = get("/api/web/v1/projects", "https://attacker.invalid");

        assertEquals(302, response.statusCode());
        assertFalse(response.headers().firstValue("Access-Control-Allow-Origin").isPresent());
    }

    private HttpResponse<String> get(String path, String origin) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder()
                .uri(URI.create("http://127.0.0.1:" + port + path))
                .GET();
        if (origin != null) request.header("Origin", origin);
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
}
