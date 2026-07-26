package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.io.ByteArrayOutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;

/** Loopback-only authenticated API used by the VS Code extension and local CLI. */
public final class LocalAuthenticatedApiServer {
    public static final String CONTRACT = "ONSURE_LOCAL_AUTHENTICATED_API_V1";
    private static final int MAX_BODY_BYTES = 1_048_576;
    private static final int DEFAULT_PORT = 47311;

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;
    private final byte[] tokenDigest;
    private final AtomicLong requestSequence = new AtomicLong();
    private HttpServer server;

    public LocalAuthenticatedApiServer(Path workspaceRoot, String token) {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(this.workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(this.workspaceRoot)) {
            throw new IllegalArgumentException("WORKSPACE_ROOT_INVALID");
        }
        if (token == null || token.length() < 32 || token.length() > 4096) {
            throw new IllegalArgumentException("LOCAL_API_TOKEN_LENGTH_INVALID");
        }
        this.tokenDigest = digest(token.getBytes(StandardCharsets.UTF_8));
    }

    public synchronized void start(int port) throws Exception {
        if (server != null) throw new IllegalStateException("LOCAL_API_ALREADY_RUNNING");
        if (port < 1024 || port > 65535) throw new IllegalArgumentException("LOCAL_API_PORT_INVALID");
        InetAddress loopback = InetAddress.getLoopbackAddress();
        server = HttpServer.create(new InetSocketAddress(loopback, port), 32);
        server.createContext("/v1/health", exchange -> respond(exchange, 200, Map.of(
                "contract", CONTRACT,
                "state", "RUNNING",
                "binding", "LOOPBACK_ONLY",
                "workspace_root", workspaceRoot.toString(),
                "final_claim_allowed", false)));
        server.createContext("/v1/status", authenticated(this::status));
        server.createContext("/v1/program-profile", authenticated(this::programProfile));
        server.createContext("/v1/validate", authenticated(this::validate));
        server.createContext("/v1/run-artifact", authenticated(this::runArtifact));
        server.setExecutor(Executors.newFixedThreadPool(4, runnable -> {
            Thread thread = new Thread(runnable, "onsure-local-api");
            thread.setDaemon(true);
            return thread;
        }));
        server.start();
    }

    public synchronized void stop() {
        if (server != null) {
            server.stop(1);
            server = null;
        }
    }

    private HttpHandler authenticated(HttpHandler handler) {
        return exchange -> {
            try {
                String authorization = exchange.getRequestHeaders().getFirst("Authorization");
                if (authorization == null || !authorization.startsWith("Bearer ")
                        || !MessageDigest.isEqual(
                                digest(authorization.substring("Bearer ".length()).getBytes(StandardCharsets.UTF_8)),
                                tokenDigest)) {
                    respond(exchange, 401, error("UNAUTHORIZED", "A valid local bearer token is required."));
                    return;
                }
                handler.handle(exchange);
            } catch (Exception error) {
                respond(exchange, 500, error("INTERNAL_ERROR", safe(error)));
            }
        };
    }

    private void status(HttpExchange exchange) throws java.io.IOException {
        requireMethod(exchange, "GET");
        respond(exchange, 200, Map.ofEntries(
                Map.entry("contract", CONTRACT),
                Map.entry("state", "RUNNING"),
                Map.entry("request_sequence", requestSequence.incrementAndGet()),
                Map.entry("workspace_root", workspaceRoot.toString()),
                Map.entry("program_learning", "AVAILABLE_STATIC_CANDIDATE"),
                Map.entry("behavior_learning", "AVAILABLE_EXECUTABLE_FIXTURE_CANDIDATE"),
                Map.entry("validation", "AVAILABLE_SELF_VALIDATION_NONFINAL"),
                Map.entry("patch_application", "APPROVAL_AND_WORKTREE_REQUIRED"),
                Map.entry("git_delivery", "EXPLICIT_SIGNED_APPROVAL_REQUIRED"),
                Map.entry("independent_otester", "NOT_RUN"),
                Map.entry("independent_oaudit", "NOT_RUN"),
                Map.entry("final_lock_allowed", false),
                Map.entry("production_go", false),
                Map.entry("commercial_go", false)));
    }

    private void programProfile(HttpExchange exchange) throws java.io.IOException {
        requireMethod(exchange, "POST");
        try {
            JsonNode request = readJson(exchange);
            Path source = resolveRequiredPath(request, "source_root", true);
            Path output = resolveOutputPath(request, "output_file",
                    workspaceRoot.resolve(".onsure/profiles/program-profile.json"));
            Map<String, Object> result = new ProgramLearningService().learn(
                    source,
                    requiredId(request, "project_id"),
                    requiredId(request, "program_id"),
                    output);
            respond(exchange, 200, Map.of(
                    "contract", CONTRACT,
                    "request_id", requestId(),
                    "profile", result,
                    "output_file", output.toString(),
                    "decision", "NON_FINAL"));
        } catch (Exception error) {
            respond(exchange, 400, error("PROGRAM_PROFILE_FAILED", safe(error)));
        }
    }

    private void validate(HttpExchange exchange) throws java.io.IOException {
        requireMethod(exchange, "POST");
        try {
            JsonNode request = readJson(exchange);
            Path source = resolveRequiredPath(request, "source_root", true);
            Path store = resolveOutputPath(request, "store_root",
                    workspaceRoot.resolve(".onsure/validation-data"));
            String adapterId = request.path("adapter_id").asText(GenericManifestTargetAdapter.ID);
            if (!GenericManifestTargetAdapter.ID.equals(adapterId)) {
                throw new IllegalArgumentException("LOCAL_CORE_API_SUPPORTS_GENERIC_ADAPTER_ONLY");
            }
            ValidationTarget target = new ValidationTarget(
                    requiredId(request, "target_id"),
                    request.path("target_name").asText(requiredId(request, "target_id")),
                    TargetType.valueOf(request.path("target_type").asText()),
                    source,
                    request.path("immutable_source_reference").asText(
                            SourceReferenceBinding.treeReference(source)),
                    adapterId,
                    request.path("policy_profile").asText("ONSURE_DEFAULT_POLICY_V1"),
                    request.path("execution_profile").asText("LOCAL_DEVELOPMENT"));
            ValidationEngine.RunResult result = ValidationEngine.defaultEngine(store).run(target);
            respond(exchange, 200, Map.of(
                    "contract", CONTRACT,
                    "request_id", requestId(),
                    "decision", result.report().decision().name(),
                    "assurance_class", "SELF_VALIDATION_NONFINAL",
                    "run_root", result.runRoot().toString(),
                    "report", result.report(),
                    "final_claim_allowed", false));
        } catch (Exception error) {
            respond(exchange, 400, error("VALIDATION_FAILED", safe(error)));
        }
    }

    private void runArtifact(HttpExchange exchange) throws java.io.IOException {
        requireMethod(exchange, "POST");
        try {
            JsonNode request = readJson(exchange);
            Path runRoot = resolveRequiredPath(request, "run_root", true);
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
            if (Files.size(file) > MAX_BODY_BYTES * 10L) {
                throw new IllegalArgumentException("RUN_ARTIFACT_TOO_LARGE");
            }
            JsonNode artifactBody = mapper.readTree(file.toFile());
            respond(exchange, 200, Map.of(
                    "contract", CONTRACT,
                    "request_id", requestId(),
                    "run_root", runRoot.toString(),
                    "artifact", artifact,
                    "body", artifactBody,
                    "sha256", HexFormat.of().formatHex(digest(Files.readAllBytes(file)))));
        } catch (Exception error) {
            respond(exchange, 400, error("RUN_ARTIFACT_FAILED", safe(error)));
        }
    }

    private Path resolveRequiredPath(JsonNode request, String field, boolean mustExist) throws Exception {
        String value = request.path(field).asText();
        if (value.isBlank()) throw new IllegalArgumentException(field.toUpperCase() + "_MISSING");
        Path path = Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(workspaceRoot)) throw new IllegalArgumentException("PATH_OUTSIDE_WORKSPACE:" + field);
        if (mustExist && (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IllegalArgumentException("PATH_INVALID:" + field);
        }
        return path;
    }

    private Path resolveOutputPath(JsonNode request, String field, Path fallback) throws Exception {
        String value = request.path(field).asText();
        Path path = value.isBlank() ? fallback.toAbsolutePath().normalize()
                : Path.of(value).toAbsolutePath().normalize();
        if (!path.startsWith(workspaceRoot)) throw new IllegalArgumentException("PATH_OUTSIDE_WORKSPACE:" + field);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("SYMLINK_OUTPUT_PROHIBITED:" + field);
        }
        return path;
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
            return mapper.readTree(output.toByteArray());
        }
    }

    private static void requireMethod(HttpExchange exchange, String method) {
        if (!method.equals(exchange.getRequestMethod())) {
            throw new IllegalArgumentException("METHOD_NOT_ALLOWED");
        }
    }

    private void respond(HttpExchange exchange, int status, Object body) throws java.io.IOException {
        byte[] bytes = mapper.writeValueAsBytes(body);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.getResponseHeaders().set("X-Content-Type-Options", "nosniff");
        exchange.getResponseHeaders().set("X-Frame-Options", "DENY");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "null");
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

    private static String requiredId(JsonNode request, String field) {
        String value = request.path(field).asText();
        if (!value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        }
        return value;
    }

    private static byte[] digest(byte[] value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value); }
        catch (Exception error) { throw new IllegalStateException(error); }
    }

    private static String safe(Exception error) {
        String value = error.getMessage();
        return value == null || value.isBlank() ? error.getClass().getSimpleName() : value;
    }

    public static void main(String[] args) throws Exception {
        String token = System.getenv("ONSURE_LOCAL_API_TOKEN");
        String root = System.getenv("ONSURE_WORKSPACE_ROOT");
        int port = args.length > 0 ? Integer.parseInt(args[0])
                : Integer.parseInt(System.getenv().getOrDefault("ONSURE_LOCAL_API_PORT", Integer.toString(DEFAULT_PORT)));
        if (root == null || root.isBlank()) throw new IllegalStateException("ONSURE_WORKSPACE_ROOT_REQUIRED");
        LocalAuthenticatedApiServer server = new LocalAuthenticatedApiServer(Path.of(root), token);
        server.start(port);
        System.out.println("ONSURE_LOCAL_API_READY loopback:" + port);
        Thread.currentThread().join();
    }
}
