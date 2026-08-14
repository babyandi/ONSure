package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/** Loopback-only authenticated API used by the VS Code extension and local CLI. */
public final class LocalAuthenticatedApiServer {
    public static final String CONTRACT = "ONSURE_LOCAL_AUTHENTICATED_API_V2";
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int MAX_ARTIFACT_BYTES = 10_485_760;
    private static final int DEFAULT_PORT = 47311;

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;
    private final byte[] tokenDigest;
    private final AuthenticatedWorkflowIdentity identity;
    private final AtomicLong requestSequence = new AtomicLong();
    private final Semaphore concurrentRequests = new Semaphore(4, true);
    private HttpServer server;
    private ExecutorService executor;

    @FunctionalInterface
    private interface CheckedHttpHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    public LocalAuthenticatedApiServer(Path workspaceRoot, String token) {
        this(workspaceRoot, token, AuthenticatedWorkflowIdentity.localAdministrator());
    }

    public LocalAuthenticatedApiServer(
            Path workspaceRoot, String token, AuthenticatedWorkflowIdentity authenticatedIdentity) {
        this.workspaceRoot = requireWorkspace(workspaceRoot);
        if (token == null || token.length() < 32 || token.length() > 4096) {
            throw new IllegalArgumentException("LOCAL_API_TOKEN_LENGTH_INVALID");
        }
        this.tokenDigest = digest(token.getBytes(StandardCharsets.UTF_8));
        if (authenticatedIdentity == null) {
            throw new IllegalArgumentException("AUTHENTICATED_IDENTITY_REQUIRED");
        }
        this.identity = authenticatedIdentity;
    }

    public synchronized void start(int port) throws Exception {
        startAndGetPort(port);
    }

    public synchronized int startAndGetPort(int port) throws Exception {
        if (server != null) throw new IllegalStateException("LOCAL_API_ALREADY_RUNNING");
        if (port != 0 && (port < 1024 || port > 65535)) {
            throw new IllegalArgumentException("LOCAL_API_PORT_INVALID");
        }
        server = HttpServer.create(new InetSocketAddress(
                InetAddress.getByName("127.0.0.1"), port), 32);
        server.createContext("/v1/health", exchange -> {
            if (!"GET".equals(exchange.getRequestMethod())) {
                respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required."));
                return;
            }
            respond(exchange, 200, Map.of(
                    "contract", CONTRACT,
                    "state", "RUNNING",
                    "binding", "127.0.0.1",
                    "assurance_class", "SELF_VALIDATION_NONFINAL",
                    "final_claim_allowed", false));
        });
        server.createContext("/v1/status", authenticated(this::status));
        server.createContext("/v1/workflow", authenticated(this::workflow));
        server.createContext("/v1/program-profile", authenticated(exchange -> compatibilityWorkflow(
                exchange, "program.learn")));
        server.createContext("/v1/validate", authenticated(exchange -> compatibilityWorkflow(
                exchange, "validation.run")));
        server.createContext("/v1/run-artifact", authenticated(this::runArtifact));
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "onsure-local-api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        return server.getAddress().getPort();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private HttpHandler authenticated(CheckedHttpHandler handler) {
        return exchange -> {
            boolean acquired = false;
            try {
                if (!isLoopback(exchange)) {
                    respond(exchange, 403, error("NON_LOOPBACK_CLIENT", "Loopback client required."));
                    return;
                }
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (origin != null && !origin.isBlank() && !"null".equals(origin)) {
                    respond(exchange, 403, error("BROWSER_ORIGIN_PROHIBITED", "Browser origins are not accepted."));
                    return;
                }
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")
                        || !MessageDigest.isEqual(
                                digest(authorization.substring("Bearer ".length())
                                        .getBytes(StandardCharsets.UTF_8)), tokenDigest)) {
                    respond(exchange, 401, error("UNAUTHORIZED", "A valid local bearer token is required."));
                    return;
                }
                acquired = concurrentRequests.tryAcquire();
                if (!acquired) {
                    respond(exchange, 429, error("LOCAL_API_BUSY", "Concurrent request limit reached."));
                    return;
                }
                handler.handle(exchange);
            } catch (IllegalArgumentException invalid) {
                respond(exchange, 400, error("INVALID_REQUEST", safe(invalid)));
            } catch (SecurityException denied) {
                respond(exchange, 403, error("FORBIDDEN", safe(denied)));
            } catch (Exception failure) {
                respond(exchange, 500, error("INTERNAL_ERROR", safe(failure)));
            } finally {
                if (acquired) concurrentRequests.release();
            }
        };
    }

    private void status(HttpExchange exchange) throws java.io.IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required."));
            return;
        }
        respond(exchange, 200, Map.ofEntries(
                Map.entry("contract", CONTRACT),
                Map.entry("state", "RUNNING"),
                Map.entry("request_sequence", requestSequence.incrementAndGet()),
                Map.entry("workspace_root", workspaceRoot.toString()),
                Map.entry("authenticated_actor", identity.actorId()),
                Map.entry("authenticated_tenant", identity.tenantId()),
                Map.entry("authenticated_roles", identity.roles().stream().map(Enum::name).sorted().toList()),
                Map.entry("program_learning", "AVAILABLE_STATIC_CANDIDATE"),
                Map.entry("behavior_learning", "AVAILABLE_EXECUTABLE_FIXTURE_CANDIDATE"),
                Map.entry("planning_review_rca", "AVAILABLE_SELF_VALIDATION_NONFINAL"),
                Map.entry("validation", "AVAILABLE_SELF_VALIDATION_NONFINAL"),
                Map.entry("patch_application", "TRUSTED_APPROVAL_AND_WORKTREE_REQUIRED"),
                Map.entry("improvement_proof", "SAME_CONTEXT_REGRESSION_REQUIRED"),
                Map.entry("git_delivery", "TRUSTED_APPROVAL_DRAFT_PR_ONLY"),
                Map.entry("license", "AVAILABLE_FILE_BACKED_NONFINAL"),
                Map.entry("service_case", "SIGNED_EXTERNAL_VERIFICATION_REQUIRED"),
                Map.entry("independent_otester", "NOT_RUN"),
                Map.entry("independent_oaudit", "NOT_RUN"),
                Map.entry("final_lock_allowed", false),
                Map.entry("production_go", false),
                Map.entry("commercial_go", false)));
    }

    private void workflow(HttpExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required."));
            return;
        }
        JsonNode envelope = readJson(exchange);
        String operation = envelope.path("operation").asText();
        JsonNode request = envelope.path("request");
        Map<String, Object> result = new SemanticAssuranceV2DispatcherBridge(workspaceRoot, identity)
                .dispatch(operation, request);
        respond(exchange, 200, Map.of(
                "contract", CONTRACT,
                "request_id", requestId(),
                "workflow", result,
                "final_claim_allowed", false));
    }

    private void compatibilityWorkflow(HttpExchange exchange, String operation) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required."));
            return;
        }
        Map<String, Object> result = new SemanticAssuranceV2DispatcherBridge(workspaceRoot, identity)
                .dispatch(operation, readJson(exchange));
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) result.get("result");
        respond(exchange, 200, Map.of(
                "contract", CONTRACT,
                "request_id", requestId(),
                "operation", operation,
                "result", payload,
                "output_file", payload.getOrDefault("output_file", "NOT_APPLICABLE"),
                "run_root", payload.getOrDefault("run_root", "NOT_APPLICABLE"),
                "decision", payload.getOrDefault("decision", "NON_FINAL"),
                "final_claim_allowed", false));
    }

    private void runArtifact(HttpExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required."));
            return;
        }
        JsonNode request = readJson(exchange);
        Map<String, Object> body = new TenantRbacService(workspaceRoot).execute(
                identity, "artifact.read", request, () -> readRunArtifact(request));
        respond(exchange, 200, body);
    }

    private Map<String, Object> readRunArtifact(JsonNode request) throws Exception {
        Path runRoot = requiredWorkspacePath(request, "run_root", true);
        String artifact = request.path("artifact").asText("validation-report.json");
        if (!artifact.matches("[A-Za-z0-9._-]{1,128}")) {
            throw new IllegalArgumentException("ARTIFACT_NAME_INVALID");
        }
        Path file = runRoot.resolve(artifact).normalize();
        if (!file.startsWith(runRoot)
                || Files.isSymbolicLink(file)
                || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("RUN_ARTIFACT_NOT_FOUND");
        }
        if (Files.size(file) > MAX_ARTIFACT_BYTES) {
            throw new IllegalArgumentException("RUN_ARTIFACT_TOO_LARGE");
        }
        JsonNode body = mapper.readTree(file.toFile());
        return Map.of(
                "contract", CONTRACT,
                "request_id", requestId(),
                "run_root", runRoot.toString(),
                "artifact", artifact,
                "body", body,
                "sha256", HexFormat.of().formatHex(digest(Files.readAllBytes(file))));
    }

    private JsonNode readJson(HttpExchange exchange) throws Exception {
        Headers headers = exchange.getRequestHeaders();
        String contentType = headers.getFirst("Content-Type");
        if (contentType == null || !contentType.toLowerCase().startsWith("application/json")) {
            throw new IllegalArgumentException("CONTENT_TYPE_APPLICATION_JSON_REQUIRED");
        }
        try (var input = exchange.getRequestBody(); var output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int total = 0;
            for (int read; (read = input.read(buffer)) >= 0;) {
                total += read;
                if (total > MAX_BODY_BYTES) throw new IllegalArgumentException("REQUEST_BODY_TOO_LARGE");
                output.write(buffer, 0, read);
            }
            JsonNode value = mapper.readTree(output.toByteArray());
            if (value == null || !value.isObject()) {
                throw new IllegalArgumentException("JSON_OBJECT_REQUIRED");
            }
            return value;
        }
    }

    private Path requiredWorkspacePath(JsonNode request, String field, boolean mustExist) {
        String value = request.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException(field.toUpperCase() + "_MISSING");
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(workspaceRoot)) throw new IllegalArgumentException("PATH_OUTSIDE_WORKSPACE:" + field);
        Path current = path;
        while (current != null && current.startsWith(workspaceRoot)) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("PATH_SYMLINK_PROHIBITED:" + field);
            }
            if (current.equals(workspaceRoot)) break;
            current = current.getParent();
        }
        if (mustExist && (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IllegalArgumentException("PATH_INVALID:" + field);
        }
        return path;
    }

    private void respond(HttpExchange exchange, int status, Object body) throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        finally { exchange.close(); }
    }

    private Map<String, Object> error(String code, String message) {
        return Map.of(
                "contract", CONTRACT,
                "request_id", requestId(),
                "decision", "FAIL",
                "error", code,
                "message", message,
                "final_claim_allowed", false);
    }

    private String requestId() {
        return "LOCAL-" + Instant.now().toEpochMilli() + "-" + requestSequence.incrementAndGet();
    }

    private static boolean isLoopback(HttpExchange exchange) {
        return exchange.getRemoteAddress() != null
                && exchange.getRemoteAddress().getAddress() != null
                && exchange.getRemoteAddress().getAddress().isLoopbackAddress();
    }

    private static Path requireWorkspace(Path value) {
        if (value == null) throw new IllegalArgumentException("WORKSPACE_ROOT_REQUIRED");
        Path path = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("WORKSPACE_ROOT_INVALID");
        }
        return path;
    }

    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception failure) { throw new IllegalStateException(failure); }
    }

    private static String safe(Exception failure) {
        if (failure instanceof IllegalArgumentException
                || failure instanceof IllegalStateException
                || failure instanceof SecurityException) {
            String value = failure.getMessage();
            return value == null || value.isBlank() ? failure.getClass().getSimpleName() : value;
        }
        return failure.getClass().getSimpleName();
    }

    public static void main(String[] args) throws Exception {
        String token = System.getenv("ONSURE_LOCAL_API_TOKEN");
        String root = System.getenv("ONSURE_WORKSPACE_ROOT");
        int port = args.length > 0 ? Integer.parseInt(args[0])
                : Integer.parseInt(System.getenv().getOrDefault(
                        "ONSURE_LOCAL_API_PORT", Integer.toString(DEFAULT_PORT)));
        if (root == null || root.isBlank()) throw new IllegalStateException("ONSURE_WORKSPACE_ROOT_REQUIRED");
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(Path.of(root), token);
        int actualPort = server.startAndGetPort(port);
        System.out.println("ONSURE_LOCAL_API_READY 127.0.0.1:" + actualPort);
        Thread.currentThread().join();
    }
}
