package io.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.assurance.ExclusiveFileLock;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Two-person, file-backed Gateway setting request and approval workflow. */
final class LocalGatewaySettingsService {
    static final String CONTRACT = "ONSURE_LLM_GATEWAY_SETTING_CHANGE_V1";
    private static final Set<String> REQUEST_FIELDS = Set.of(
            "provider", "model", "requests_per_second", "cost_per_token_micros", "reason");
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path root;
    private final Path lock;
    private final Map<String, String> environment;

    LocalGatewaySettingsService(Path workspaceRoot, Map<String, String> environment) throws Exception {
        this.root = workspaceRoot.toAbsolutePath().normalize().resolve(".onsure/management/gateway-requests");
        this.lock = root.resolve(".requests.lock");
        this.environment = Map.copyOf(environment == null ? Map.of() : environment);
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("GATEWAY_SETTINGS_ROOT_SYMLINK");
        }
        Files.createDirectories(root);
    }

    Map<String, Object> current() {
        String provider = environment.getOrDefault("ONSURE_LLM_PROVIDER", "local-mock");
        String model = environment.getOrDefault("ONSURE_LLM_MODEL",
                "openai".equals(provider)
                        ? environment.getOrDefault("ONSURE_OPENAI_MODEL", "UNVERIFIED")
                        : "onsure-local-mock-v1");
        return Map.ofEntries(
                Map.entry("contract", CONTRACT),
                Map.entry("provider", provider),
                Map.entry("model", model),
                Map.entry("requests_per_second", number("ONSURE_LLM_MOCK_REQUESTS_PER_SECOND", 20)),
                Map.entry("cost_per_token_micros", number("ONSURE_LLM_MOCK_COST_PER_TOKEN_MICROS", 0)),
                Map.entry("automatic_apply_enabled", false),
                Map.entry("secrets_accepted_by_api", false),
                Map.entry("approval_policy", "DISTINCT_APPROVER_REQUIRED"),
                Map.entry("final_claim_allowed", false));
    }

    Map<String, Object> request(JsonNode input, LocalAccessControl.Identity requester) throws Exception {
        requireRole(requester, LocalAccessControl.Role.ADMIN, "SETTING_REQUEST_ADMIN_REQUIRED");
        requireExactFields(input, REQUEST_FIELDS);
        String provider = text(input, "provider", 32);
        if (!Set.of("local-mock", "openai").contains(provider)) {
            throw new IllegalArgumentException("GATEWAY_PROVIDER_INVALID");
        }
        String model = text(input, "model", 160);
        if (!model.matches("[A-Za-z0-9._:/-]{1,160}")) throw new IllegalArgumentException("GATEWAY_MODEL_INVALID");
        int rate = integer(input, "requests_per_second", 1, 10_000);
        long cost = longNumber(input, "cost_per_token_micros", 0, 1_000_000_000L);
        String reason = text(input, "reason", 500);
        String requestId = "gateway-setting-" + UUID.randomUUID();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("request_id", requestId);
        value.put("state", "AWAITING_APPROVAL");
        value.put("requested_at", Instant.now().toString());
        value.put("requested_by", requester.actor());
        value.put("requested_role", requester.role().name());
        value.put("change", Map.of(
                "provider", provider, "model", model,
                "requests_per_second", rate, "cost_per_token_micros", cost));
        value.put("reason", reason);
        value.put("secret_material_present", false);
        value.put("automatic_apply_enabled", false);
        value.put("final_claim_allowed", false);
        value.put("request_sha256", requestDigest(value));
        ExclusiveFileLock.run(lock, () -> write(requestId, value));
        return Map.copyOf(value);
    }

    Map<String, Object> approve(JsonNode input, LocalAccessControl.Identity approver) throws Exception {
        requireRole(approver, LocalAccessControl.Role.APPROVER, "SETTING_APPROVER_ROLE_REQUIRED");
        Set<String> fields = Set.of("request_id", "decision", "reason");
        requireExactFields(input, fields);
        String requestId = text(input, "request_id", 180);
        if (!requestId.matches("gateway-setting-[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("GATEWAY_SETTING_REQUEST_ID_INVALID");
        }
        String decision = text(input, "decision", 16);
        if (!Set.of("APPROVE", "REJECT").contains(decision)) {
            throw new IllegalArgumentException("GATEWAY_SETTING_DECISION_INVALID");
        }
        String reason = text(input, "reason", 500);
        final Map<String, Object>[] result = new Map[1];
        ExclusiveFileLock.run(lock, () -> {
            Map<String, Object> value = read(requestId);
            if (!"AWAITING_APPROVAL".equals(value.get("state"))) {
                throw new IllegalArgumentException("GATEWAY_SETTING_REQUEST_ALREADY_DECIDED");
            }
            if (approver.actor().equals(value.get("requested_by"))) {
                throw new IllegalArgumentException("GATEWAY_SETTING_DISTINCT_APPROVER_REQUIRED");
            }
            value.put("state", "APPROVE".equals(decision)
                    ? "APPROVED_PENDING_EXTERNAL_APPLY" : "REJECTED");
            value.put("decided_at", Instant.now().toString());
            value.put("decided_by", approver.actor());
            value.put("decision_reason", reason);
            value.put("automatic_apply_enabled", false);
            value.put("final_claim_allowed", false);
            value.put("receipt_sha256", receiptDigest(value));
            write(requestId, value);
            result[0] = Map.copyOf(value);
        });
        return result[0];
    }

    Map<String, Object> list(int limit) throws Exception {
        int bounded = Math.max(1, Math.min(limit, 100));
        List<Map<String, Object>> values = new ArrayList<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::modified).reversed()).limit(bounded).toList()) {
                String id = file.getFileName().toString().replaceFirst("\\.json$", "");
                values.add(Map.copyOf(read(id)));
            }
        }
        return Map.of(
                "contract", CONTRACT,
                "current", current(),
                "requests", List.copyOf(values),
                "final_claim_allowed", false);
    }

    private Map<String, Object> read(String requestId) throws Exception {
        Path file = root.resolve(requestId + ".json").normalize();
        if (!file.startsWith(root) || !Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                || Files.isSymbolicLink(file) || Files.size(file) > 1_048_576L) {
            throw new IllegalArgumentException("GATEWAY_SETTING_REQUEST_NOT_FOUND");
        }
        Map<String, Object> value = mapper.readValue(file.toFile(), new TypeReference<>() {});
        String claimed = String.valueOf(value.get("request_sha256"));
        if (!CONTRACT.equals(value.get("contract")) || !requestId.equals(value.get("request_id"))
                || !claimed.matches("[0-9a-f]{64}") || !claimed.equals(requestDigest(value))) {
            throw new IllegalStateException("GATEWAY_SETTING_REQUEST_DIGEST_INVALID");
        }
        boolean awaiting = "AWAITING_APPROVAL".equals(value.get("state"));
        if (!awaiting) {
            String receipt = String.valueOf(value.get("receipt_sha256"));
            if (!Set.of("APPROVED_PENDING_EXTERNAL_APPLY", "REJECTED").contains(value.get("state"))
                    || !receipt.matches("[0-9a-f]{64}") || !receipt.equals(receiptDigest(value))) {
                throw new IllegalStateException("GATEWAY_SETTING_RECEIPT_DIGEST_INVALID");
            }
        } else if (value.containsKey("receipt_sha256")) {
            throw new IllegalStateException("GATEWAY_SETTING_PREMATURE_RECEIPT");
        }
        return new LinkedHashMap<>(value);
    }

    private void write(String requestId, Map<String, Object> value) throws Exception {
        Path file = root.resolve(requestId + ".json").normalize();
        Path temporary = root.resolve(requestId + "." + UUID.randomUUID() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try {
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(temporary);
        }
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    java.nio.file.attribute.PosixFilePermission.OWNER_READ,
                    java.nio.file.attribute.PosixFilePermission.OWNER_WRITE));
        } catch (UnsupportedOperationException ignored) { }
    }

    private String requestDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> request = new TreeMap<>();
        for (String key : List.of(
                "contract", "request_id", "requested_at", "requested_by", "requested_role",
                "change", "reason", "secret_material_present")) {
            if (!value.containsKey(key)) throw new IllegalStateException("GATEWAY_SETTING_REQUEST_FIELD_MISSING:" + key);
            request.put(key, value.get(key));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(request));
    }

    private String receiptDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> copy = new TreeMap<>(value);
        copy.remove("receipt_sha256");
        return Hashing.sha256(mapper.writeValueAsBytes(copy));
    }

    private long number(String key, long fallback) {
        try { return Math.max(0L, Long.parseLong(environment.getOrDefault(key, String.valueOf(fallback)))); }
        catch (NumberFormatException invalid) { return fallback; }
    }

    private long modified(Path path) {
        try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(); }
        catch (Exception ignored) { return 0L; }
    }

    private static void requireExactFields(JsonNode input, Set<String> expected) {
        if (input == null || !input.isObject()) throw new IllegalArgumentException("GATEWAY_SETTING_OBJECT_REQUIRED");
        Set<String> actual = new java.util.HashSet<>();
        input.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(expected)) throw new IllegalArgumentException("GATEWAY_SETTING_FIELDS_INVALID");
    }

    private static String text(JsonNode input, String field, int maximum) {
        JsonNode value = input.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maximum) {
            throw new IllegalArgumentException("GATEWAY_SETTING_TEXT_INVALID:" + field);
        }
        return value.asText();
    }

    private static int integer(JsonNode input, String field, int minimum, int maximum) {
        long value = longNumber(input, field, minimum, maximum);
        return Math.toIntExact(value);
    }

    private static long longNumber(JsonNode input, String field, long minimum, long maximum) {
        JsonNode value = input.path(field);
        if (!value.isIntegralNumber() || value.asLong() < minimum || value.asLong() > maximum) {
            throw new IllegalArgumentException("GATEWAY_SETTING_NUMBER_INVALID:" + field);
        }
        return value.asLong();
    }

    private static void requireRole(
            LocalAccessControl.Identity identity, LocalAccessControl.Role role, String code) {
        if (identity == null || identity.role() != role) throw new IllegalArgumentException(code);
    }
}
