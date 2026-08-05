package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicLong;

/** Loopback-only authenticated API used by the VS Code extension and local CLI. */
public final class LocalAuthenticatedApiServer {
    public static final String CONTRACT = "ONSURE_LOCAL_AUTHENTICATED_API_V1";
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int MAX_ARTIFACT_BYTES = 10_485_760;
    private static final int DEFAULT_PORT = 47311;
    private static final String OPENAPI_RESOURCE = "/openapi/onsure-local-api.v1.json";
    private static final String ADMIN_INDEX_RESOURCE = "/admin/index.html";
    private static final String ADMIN_SCRIPT_RESOURCE = "/admin/app.js";
    private static final String ADMIN_STYLE_RESOURCE = "/admin/styles.css";
    private static final Set<String> ROUTE_PATHS = Set.of(
            "/v1/openapi.json", "/v1/health", "/v1/status", "/v1/workflow",
            "/v1/program-profile", "/v1/validate", "/v1/run-artifact",
            "/v1/workspace-snapshot", "/v1/autopilot-control", "/v1/management-overview",
            "/v1/session", "/v1/programs", "/v1/programs/validate",
            "/v1/validation-scorecards",
            "/v1/gateway-settings/requests", "/v1/gateway-settings/approvals", "/v1/audit-events");

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;
    private final Map<String, String> environment;
    private final LocalAccessControl accessControl;
    private final AtomicLong requestSequence = new AtomicLong();
    private final Semaphore concurrentRequests = new Semaphore(4, true);
    private final JsonNode openApiDocument;
    private HttpServer server;
    private ExecutorService executor;

    @FunctionalInterface
    private interface CheckedHttpHandler {
        void handle(HttpExchange exchange) throws Exception;
    }

    public LocalAuthenticatedApiServer(Path workspaceRoot, String token) {
        this(workspaceRoot, token, System.getenv());
    }

    LocalAuthenticatedApiServer(Path workspaceRoot, String token, Map<String, String> environment) {
        this.workspaceRoot = requireWorkspace(workspaceRoot);
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
        this.accessControl = new LocalAccessControl(token, this.environment);
        this.openApiDocument = loadOpenApiDocument();
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
        server.createContext("/v1/openapi.json", this::openApi);
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
        server.createContext("/v1/status", authenticated(LocalAccessControl.Permission.VIEW, this::status));
        server.createContext("/v1/session", authenticated(LocalAccessControl.Permission.VIEW, this::session));
        server.createContext("/v1/workflow", authenticated(
                LocalAccessControl.Permission.DISPATCH_WORKFLOW, this::workflow));
        server.createContext("/v1/program-profile", authenticated(
                LocalAccessControl.Permission.OPERATE_PROGRAMS,
                exchange -> compatibilityWorkflow(exchange, "program.learn")));
        server.createContext("/v1/validate", authenticated(
                LocalAccessControl.Permission.OPERATE_PROGRAMS,
                exchange -> compatibilityWorkflow(exchange, "validation.run")));
        server.createContext("/v1/run-artifact", authenticated(
                LocalAccessControl.Permission.VIEW, this::runArtifact));
        server.createContext("/v1/workspace-snapshot", authenticated(
                LocalAccessControl.Permission.VIEW, this::workspaceSnapshot));
        server.createContext("/v1/autopilot-control", authenticated(
                LocalAccessControl.Permission.CONTROL, this::autopilotControl));
        server.createContext("/v1/management-overview", authenticated(
                LocalAccessControl.Permission.VIEW, this::managementOverview));
        server.createContext("/v1/programs", authenticated(
                LocalAccessControl.Permission.VIEW, this::programs));
        server.createContext("/v1/programs/validate", authenticated(
                LocalAccessControl.Permission.OPERATE_PROGRAMS, this::programValidate));
        server.createContext("/v1/validation-scorecards", authenticated(
                LocalAccessControl.Permission.VIEW, this::validationScorecards));
        server.createContext("/v1/gateway-settings/requests", authenticated(
                LocalAccessControl.Permission.VIEW, this::gatewaySettingRequests));
        server.createContext("/v1/gateway-settings/approvals", authenticated(
                LocalAccessControl.Permission.APPROVE_SETTINGS, this::gatewaySettingApprovals));
        server.createContext("/v1/audit-events", authenticated(
                LocalAccessControl.Permission.VIEW, this::auditEvents));
        server.createContext("/admin", this::adminAsset);
        executor = Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "onsure-local-api");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        return server.getAddress().getPort();
    }

    static Set<String> routePaths() {
        return ROUTE_PATHS;
    }

    private void openApi(HttpExchange exchange) throws java.io.IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required."));
            return;
        }
        respond(exchange, 200, openApiDocument, "application/vnd.oai.openapi+json;version=3.1.0");
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

    private HttpHandler authenticated(LocalAccessControl.Permission permission, CheckedHttpHandler handler) {
        return exchange -> {
            boolean acquired = false;
            try {
                if (!isLoopback(exchange)) {
                    respond(exchange, 403, error("NON_LOOPBACK_CLIENT", "Loopback client required."));
                    return;
                }
                String origin = exchange.getRequestHeaders().getFirst("Origin");
                if (!allowedOrigin(exchange, origin)) {
                    respond(exchange, 403, error("BROWSER_ORIGIN_PROHIBITED", "Browser origins are not accepted."));
                    return;
                }
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                LocalAccessControl.Identity identity = accessControl.authenticate(authorization);
                if (identity == null) {
                    respond(exchange, 401, error("UNAUTHORIZED", "A valid local bearer token is required."));
                    return;
                }
                if (!LocalAccessControl.allowed(identity, permission)) {
                    respond(exchange, 403, error("ROLE_PERMISSION_DENIED", "The authenticated role lacks this permission."));
                    return;
                }
                exchange.setAttribute("onsure.identity", identity);
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

    private void session(HttpExchange exchange) throws java.io.IOException {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required."));
            return;
        }
        LocalAccessControl.Identity identity = identity(exchange);
        respond(exchange, 200, Map.of(
                "contract", "ONSURE_LOCAL_SESSION_V1",
                "actor", identity.actor(), "role", identity.role().name(),
                "token_sha256", identity.tokenSha256(),
                "final_claim_allowed", false));
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
        Map<String, Object> result = new LocalWorkflowDispatcher(
                workspaceRoot, workflowIdentity(identity(exchange)))
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
        Map<String, Object> result = new LocalWorkflowDispatcher(
                workspaceRoot, workflowIdentity(identity(exchange)))
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
                workflowIdentity(identity(exchange)), "artifact.read", request,
                () -> readRunArtifact(request));
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
                "sha256", Hashing.sha256(Files.readAllBytes(file)));
    }

    private void workspaceSnapshot(HttpExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required."));
            return;
        }
        JsonNode request = readJson(exchange);
        Map<String, Object> snapshot = new LocalWorkspaceSnapshotService(workspaceRoot).snapshot(
                request.path("project_id").asText(), request.path("target_id").asText());
        respond(exchange, 200, Map.of(
                "contract", CONTRACT,
                "request_id", requestId(),
                "snapshot", snapshot,
                "final_claim_allowed", false));
    }

    private void autopilotControl(HttpExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required."));
            return;
        }
        JsonNode request = readJson(exchange);
        Map<String, Object> control = new LocalAutopilotControlService(workspaceRoot)
                .request(request.path("action").asText());
        respond(exchange, 200, Map.of(
                "contract", CONTRACT,
                "request_id", requestId(),
                "control", control,
                "final_claim_allowed", false));
    }

    private void managementOverview(HttpExchange exchange) throws Exception {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required."));
            return;
        }
        respond(exchange, 200, new LocalManagementOverviewService(workspaceRoot).overview());
    }

    private void programs(HttpExchange exchange) throws Exception {
        if ("GET".equals(exchange.getRequestMethod())) {
            Map<String, Object> overview = new LocalManagementOverviewService(workspaceRoot).overview();
            respond(exchange, 200, Map.of(
                    "contract", LocalProgramManagementService.CONTRACT,
                    "programs", overview.get("programs"),
                    "program_count", overview.get("program_count"),
                    "final_claim_allowed", false));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET or POST is required."));
            return;
        }
        LocalAccessControl.Identity identity = identity(exchange);
        if (!LocalAccessControl.allowed(identity, LocalAccessControl.Permission.OPERATE_PROGRAMS)) {
            respond(exchange, 403, error("ROLE_PERMISSION_DENIED", "Operator role is required."));
            return;
        }
        Map<String, Object> registered = new LocalProgramManagementService(workspaceRoot).register(readJson(exchange));
        new LocalManagementAuditLedger(workspaceRoot).append(
                identity, "PROGRAM_REGISTER", "ACCEPTED", Map.of(
                        "project_id", registered.get("project_id"),
                        "target_id", registered.get("target_id"),
                        "observed_source_sha256", registered.get("observed_source_sha256")));
        respond(exchange, 200, registered);
    }

    private void programValidate(HttpExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required."));
            return;
        }
        LocalAccessControl.Identity identity = identity(exchange);
        Map<String, Object> validation = new LocalProgramManagementService(workspaceRoot, environment)
                .validate(readJson(exchange));
        new LocalManagementAuditLedger(workspaceRoot).append(
                identity, "PROGRAM_VALIDATE", "COMPLETED", Map.of(
                        "run_id", validation.get("run_id"), "decision", validation.get("decision"),
                        "finding_count", validation.get("finding_count"),
                        "source_mutation_detected", validation.get("source_mutation_detected")));
        respond(exchange, 200, validation);
    }

    private void validationScorecards(HttpExchange exchange) throws Exception {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required."));
            return;
        }
        Map<String, Object> overview = new LocalManagementOverviewService(workspaceRoot, environment,
                java.net.http.HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(1)).build())
                .overview();
        @SuppressWarnings("unchecked")
        var programs = (java.util.List<Map<String, Object>>) overview.get("programs");
        var scorecards = programs.stream().map(program -> Map.of(
                "project_id", program.get("project_id"),
                "program_id", program.get("program_id"),
                "program_name", program.get("program_name"),
                "source_reference", program.get("source_reference"),
                "latest_validation", program.get("latest_validation"),
                "final_claim_allowed", false)).toList();
        respond(exchange, 200, Map.of(
                "contract", "ONSURE_VALIDATION_SCORECARD_PORTFOLIO_V1",
                "generated_at", overview.get("generated_at"),
                "scorecards", scorecards,
                "scorecard_count", scorecards.stream().filter(item -> {
                    Object validation = item.get("latest_validation");
                    return validation instanceof Map<?, ?> map
                            && map.get("scorecard") instanceof Map<?, ?> score
                            && ValidationScorecard.CONTRACT.equals(score.get("contract"));
                }).count(),
                "interpretation", "Evidence coverage only; independent assurance and final approval remain separate.",
                "final_claim_allowed", false));
    }

    private void gatewaySettingRequests(HttpExchange exchange) throws Exception {
        LocalGatewaySettingsService settings = new LocalGatewaySettingsService(workspaceRoot, environment);
        if ("GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 200, settings.list(50));
            return;
        }
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET or POST is required."));
            return;
        }
        LocalAccessControl.Identity identity = identity(exchange);
        if (!LocalAccessControl.allowed(identity, LocalAccessControl.Permission.REQUEST_SETTINGS)) {
            respond(exchange, 403, error("ROLE_PERMISSION_DENIED", "Administrator role is required."));
            return;
        }
        Map<String, Object> requested = settings.request(readJson(exchange), identity);
        new LocalManagementAuditLedger(workspaceRoot).append(
                identity, "GATEWAY_SETTING_REQUEST", "AWAITING_APPROVAL", Map.of(
                        "request_id", requested.get("request_id"),
                        "request_sha256", requested.get("request_sha256")));
        respond(exchange, 200, requested);
    }

    private void gatewaySettingApprovals(HttpExchange exchange) throws Exception {
        if (!"POST".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "POST is required."));
            return;
        }
        LocalAccessControl.Identity identity = identity(exchange);
        Map<String, Object> decision = new LocalGatewaySettingsService(workspaceRoot, environment)
                .approve(readJson(exchange), identity);
        new LocalManagementAuditLedger(workspaceRoot).append(
                identity, "GATEWAY_SETTING_APPROVAL", String.valueOf(decision.get("state")), Map.of(
                        "request_id", decision.get("request_id"),
                        "request_sha256", decision.get("request_sha256")));
        respond(exchange, 200, decision);
    }

    private void auditEvents(HttpExchange exchange) throws Exception {
        if (!"GET".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET is required."));
            return;
        }
        respond(exchange, 200, new LocalManagementAuditLedger(workspaceRoot).recent(100));
    }

    private void adminAsset(HttpExchange exchange) throws java.io.IOException {
        if (!isLoopback(exchange)) {
            respond(exchange, 403, error("NON_LOOPBACK_CLIENT", "Loopback client required."));
            return;
        }
        if (!"GET".equals(exchange.getRequestMethod()) && !"HEAD".equals(exchange.getRequestMethod())) {
            respond(exchange, 405, error("METHOD_NOT_ALLOWED", "GET or HEAD is required."));
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String resource;
        String contentType;
        if ("/admin".equals(path) || "/admin/".equals(path) || "/admin/index.html".equals(path)) {
            resource = ADMIN_INDEX_RESOURCE;
            contentType = "text/html; charset=utf-8";
        } else if ("/admin/app.js".equals(path)) {
            resource = ADMIN_SCRIPT_RESOURCE;
            contentType = "text/javascript; charset=utf-8";
        } else if ("/admin/styles.css".equals(path)) {
            resource = ADMIN_STYLE_RESOURCE;
            contentType = "text/css; charset=utf-8";
        } else {
            respond(exchange, 404, error("NOT_FOUND", "Admin asset not found."));
            return;
        }
        try (InputStream input = LocalAuthenticatedApiServer.class.getResourceAsStream(resource)) {
            if (input == null) {
                respond(exchange, 500, error("ADMIN_RESOURCE_MISSING", "Admin asset unavailable."));
                return;
            }
            byte[] bytes = input.readAllBytes();
            exchange.getResponseHeaders().set("Content-Type", contentType);
            exchange.getResponseHeaders().set("Cache-Control", "no-store");
            exchange.getResponseHeaders().set("Pragma", "no-cache");
            exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
            exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
            exchange.getResponseHeaders().set("Referrer-Policy", "no-referrer");
            exchange.getResponseHeaders().set("Content-Security-Policy",
                    "default-src 'none'; script-src 'self'; style-src 'self'; connect-src 'self'; "
                            + "img-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'");
            exchange.sendResponseHeaders(200, "HEAD".equals(exchange.getRequestMethod()) ? -1 : bytes.length);
            if (!"HEAD".equals(exchange.getRequestMethod())) {
                try (var output = exchange.getResponseBody()) { output.write(bytes); }
            }
        } finally {
            exchange.close();
        }
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
        respond(exchange, status, body, "application/json; charset=utf-8");
    }

    private void respond(HttpExchange exchange, int status, Object body, String contentType)
            throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("Pragma", "no-cache");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Content-Security-Policy", "default-src 'none'");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
        finally { exchange.close(); }
    }

    private JsonNode loadOpenApiDocument() {
        try (InputStream input = LocalAuthenticatedApiServer.class.getResourceAsStream(OPENAPI_RESOURCE)) {
            if (input == null) throw new IllegalStateException("OPENAPI_RESOURCE_MISSING");
            JsonNode document = mapper.readTree(input);
            if (!"3.1.0".equals(document.path("openapi").asText())) {
                throw new IllegalStateException("OPENAPI_VERSION_INVALID");
            }
            Set<String> documented = new java.util.TreeSet<>();
            document.path("paths").fieldNames().forEachRemaining(documented::add);
            if (!documented.equals(new java.util.TreeSet<>(ROUTE_PATHS))) {
                throw new IllegalStateException("OPENAPI_ROUTE_DRIFT");
            }
            return document;
        } catch (java.io.IOException invalid) {
            throw new IllegalStateException("OPENAPI_RESOURCE_INVALID", invalid);
        }
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

    private static boolean allowedOrigin(HttpExchange exchange, String origin) {
        if (origin == null || origin.isBlank() || "null".equals(origin)) return true;
        try {
            URI value = URI.create(origin);
            return "http".equals(value.getScheme())
                    && "127.0.0.1".equals(value.getHost())
                    && value.getPort() == exchange.getLocalAddress().getPort()
                    && (value.getPath() == null || value.getPath().isEmpty())
                    && value.getQuery() == null && value.getFragment() == null;
        } catch (IllegalArgumentException invalid) {
            return false;
        }
    }

    private static LocalAccessControl.Identity identity(HttpExchange exchange) {
        Object value = exchange.getAttribute("onsure.identity");
        if (!(value instanceof LocalAccessControl.Identity identity)) {
            throw new IllegalStateException("AUTHENTICATED_IDENTITY_MISSING");
        }
        return identity;
    }

    private static AuthenticatedWorkflowIdentity workflowIdentity(LocalAccessControl.Identity identity) {
        AuthenticatedWorkflowIdentity.Role role = switch (identity.role()) {
            case VIEWER -> AuthenticatedWorkflowIdentity.Role.VIEWER;
            case OPERATOR -> AuthenticatedWorkflowIdentity.Role.OPERATOR;
            case ADMIN -> AuthenticatedWorkflowIdentity.Role.ADMIN;
            case APPROVER -> AuthenticatedWorkflowIdentity.Role.APPROVER;
        };
        return AuthenticatedWorkflowIdentity.local(identity.actor(), role, "LOCAL_WORKSPACE");
    }

    private static Path requireWorkspace(Path value) {
        if (value == null) throw new IllegalArgumentException("WORKSPACE_ROOT_REQUIRED");
        Path path = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("WORKSPACE_ROOT_INVALID");
        }
        return path;
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
