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
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

/** Two-person approval exchange for an exact, non-executable Program Understanding review. */
final class LocalProgramUnderstandingApprovalService {
    static final String CONTRACT = "ONSURE_PROGRAM_UNDERSTANDING_APPROVAL_V1";
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;
    private final Path root;
    private final Path lock;
    private final Clock clock;

    LocalProgramUnderstandingApprovalService(Path workspaceRoot) throws Exception {
        this(workspaceRoot, Clock.systemUTC());
    }

    LocalProgramUnderstandingApprovalService(Path workspaceRoot, Clock clock) throws Exception {
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.root = this.workspaceRoot.resolve(".onsure/management/program-understanding-approvals");
        this.lock = root.resolve(".approvals.lock");
        this.clock = clock;
        if (Files.exists(root, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(root)) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_ROOT_SYMLINK");
        }
        Files.createDirectories(root);
    }

    Map<String, Object> request(JsonNode input, LocalAccessControl.Identity requester) throws Exception {
        if (requester == null || !Set.of(LocalAccessControl.Role.ADMIN, LocalAccessControl.Role.OPERATOR)
                .contains(requester.role())) throw new IllegalArgumentException("PROGRAM_APPROVAL_REQUESTER_ROLE_INVALID");
        requireExactFields(input, Set.of("project_id", "target_id", "profile_file_sha256",
                "review_sha256", "reason", "ttl_seconds"));
        String projectId = id(input, "project_id");
        String targetId = id(input, "target_id");
        String profileSha = digest(input, "profile_file_sha256");
        String reviewSha = digest(input, "review_sha256");
        String reason = text(input, "reason", 500);
        int ttl = integer(input, "ttl_seconds", 60, 3600);
        Map<String, Object> review = currentReview(targetId, profileSha, reviewSha);
        if (!"READY_FOR_SEPARATE_APPROVAL".equals(review.get("review_state"))) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_REVIEW_NOT_READY");
        }
        if (!projectId.equals(review.get("project_id"))) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_PROJECT_BINDING_INVALID");
        }
        Instant now = clock.instant();
        String requestId = "program-understanding-approval-" + UUID.randomUUID();
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("contract", CONTRACT);
        value.put("request_id", requestId);
        value.put("state", "AWAITING_APPROVAL");
        value.put("project_id", projectId);
        value.put("target_id", targetId);
        value.put("source_sha256", review.get("source_sha256"));
        value.put("profile_file_sha256", profileSha);
        value.put("review_sha256", reviewSha);
        value.put("requested_at", now.toString());
        value.put("expires_at", now.plusSeconds(ttl).toString());
        value.put("requested_by", requester.actor());
        value.put("requested_role", requester.role().name());
        value.put("reason", reason);
        value.put("single_use_for_execution", true);
        value.put("execution_consumed", false);
        value.put("execution_state", "NOT_RUN");
        value.put("final_claim_allowed", false);
        value.put("request_sha256", requestDigest(value));
        ExclusiveFileLock.run(lock, () -> write(requestId, value));
        return Map.copyOf(value);
    }

    Map<String, Object> decide(JsonNode input, LocalAccessControl.Identity approver) throws Exception {
        if (approver == null || approver.role() != LocalAccessControl.Role.APPROVER) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_APPROVER_ROLE_REQUIRED");
        }
        requireExactFields(input, Set.of("request_id", "decision", "reason"));
        String requestId = text(input, "request_id", 180);
        if (!requestId.matches("program-understanding-approval-[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_REQUEST_ID_INVALID");
        }
        String decision = text(input, "decision", 16);
        if (!Set.of("APPROVE", "REJECT").contains(decision)) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_DECISION_INVALID");
        }
        String reason = text(input, "reason", 500);
        @SuppressWarnings("unchecked")
        final Map<String, Object>[] result = new Map[1];
        ExclusiveFileLock.run(lock, () -> {
            Map<String, Object> value = read(requestId);
            if (!"AWAITING_APPROVAL".equals(value.get("state"))) {
                throw new IllegalArgumentException("PROGRAM_APPROVAL_REQUEST_ALREADY_DECIDED");
            }
            if (!clock.instant().isBefore(Instant.parse(value.get("expires_at").toString()))) {
                throw new IllegalArgumentException("PROGRAM_APPROVAL_REQUEST_EXPIRED");
            }
            if (approver.actor().equals(value.get("requested_by"))) {
                throw new IllegalArgumentException("PROGRAM_APPROVAL_DISTINCT_APPROVER_REQUIRED");
            }
            currentReview(value.get("target_id").toString(), value.get("profile_file_sha256").toString(),
                    value.get("review_sha256").toString());
            value.put("state", "APPROVE".equals(decision) ? "APPROVED_NOT_EXECUTED" : "REJECTED");
            value.put("decided_at", clock.instant().toString());
            value.put("decided_by", approver.actor());
            value.put("decision_reason", reason);
            value.put("execution_consumed", false);
            value.put("execution_state", "NOT_RUN");
            value.put("receipt_sha256", digestValue(value, "receipt_sha256"));
            write(requestId, value);
            result[0] = Map.copyOf(value);
        });
        return result[0];
    }

    Map<String, Object> list(int limit) throws Exception {
        List<Map<String, Object>> values = new ArrayList<>();
        try (var files = Files.list(root)) {
            for (Path file : files.filter(path -> path.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(this::modified).reversed()).limit(Math.max(1, Math.min(limit, 100))).toList()) {
                values.add(Map.copyOf(read(file.getFileName().toString().replaceFirst("\\.json$", ""))));
            }
        }
        return Map.of("contract", CONTRACT, "requests", List.copyOf(values), "final_claim_allowed", false);
    }

    private Map<String, Object> currentReview(String targetId, String profileSha, String reviewSha) throws Exception {
        Path targetRoot = workspaceRoot.resolve(".onsure/program-understanding").resolve(targetId).normalize();
        Path profile = targetRoot.resolve("program-profile.json");
        Path review = targetRoot.resolve("review.json");
        if (!targetRoot.startsWith(workspaceRoot) || !safeFile(profile) || !safeFile(review)
                || !Hashing.file(profile).equals(profileSha)) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_PROFILE_STALE");
        }
        Map<String, Object> value = mapper.readValue(review.toFile(), new TypeReference<>() {});
        if (!"ONSURE_PROGRAM_UNDERSTANDING_REVIEW_V1".equals(value.get("contract"))
                || !targetId.equals(value.get("target_id")) || !profileSha.equals(value.get("profile_file_sha256"))
                || !reviewSha.equals(value.get("review_sha256"))
                || !reviewSha.equals(digestValue(value, "review_sha256"))) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_REVIEW_STALE_OR_TAMPERED");
        }
        return value;
    }

    private Map<String, Object> read(String requestId) throws Exception {
        Path file = root.resolve(requestId + ".json").normalize();
        if (!file.startsWith(root) || !safeFile(file) || Files.size(file) > 1_048_576L) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_REQUEST_NOT_FOUND");
        }
        Map<String, Object> value = mapper.readValue(file.toFile(), new TypeReference<>() {});
        if (!CONTRACT.equals(value.get("contract")) || !requestId.equals(value.get("request_id"))
                || !String.valueOf(value.get("request_sha256")).equals(requestDigest(value))) {
            throw new IllegalStateException("PROGRAM_APPROVAL_REQUEST_DIGEST_INVALID");
        }
        if (!"AWAITING_APPROVAL".equals(value.get("state"))
                && !String.valueOf(value.get("receipt_sha256")).equals(digestValue(value, "receipt_sha256"))) {
            throw new IllegalStateException("PROGRAM_APPROVAL_RECEIPT_DIGEST_INVALID");
        }
        return new LinkedHashMap<>(value);
    }

    private void write(String requestId, Map<String, Object> value) throws Exception {
        Path file = root.resolve(requestId + ".json").normalize();
        Path temporary = root.resolve(requestId + "." + UUID.randomUUID() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temporary); }
    }

    private String digestValue(Map<String, Object> value, String... excluded) throws Exception {
        Map<String, Object> copy = new TreeMap<>(value);
        for (String key : excluded) copy.remove(key);
        return Hashing.sha256(mapper.writeValueAsBytes(copy));
    }

    private String requestDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> immutable = new TreeMap<>();
        for (String key : List.of("contract", "request_id", "project_id", "target_id", "source_sha256",
                "profile_file_sha256", "review_sha256", "requested_at", "expires_at", "requested_by",
                "requested_role", "reason", "single_use_for_execution")) {
            if (!value.containsKey(key)) throw new IllegalStateException("PROGRAM_APPROVAL_REQUEST_FIELD_MISSING:" + key);
            immutable.put(key, value.get(key));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(immutable));
    }

    private static boolean safeFile(Path path) {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) && !Files.isSymbolicLink(path);
    }
    private long modified(Path path) {
        try { return Files.getLastModifiedTime(path, LinkOption.NOFOLLOW_LINKS).toMillis(); }
        catch (Exception ignored) { return 0; }
    }
    private static void requireExactFields(JsonNode input, Set<String> fields) {
        if (input == null || !input.isObject()) throw new IllegalArgumentException("PROGRAM_APPROVAL_OBJECT_REQUIRED");
        Set<String> actual = new java.util.HashSet<>(); input.fieldNames().forEachRemaining(actual::add);
        if (!actual.equals(fields)) throw new IllegalArgumentException("PROGRAM_APPROVAL_FIELDS_INVALID");
    }
    private static String text(JsonNode input, String field, int maximum) {
        JsonNode value = input.path(field);
        if (!value.isTextual() || value.asText().isBlank() || value.asText().length() > maximum)
            throw new IllegalArgumentException("PROGRAM_APPROVAL_TEXT_INVALID:" + field);
        return value.asText();
    }
    private static String id(JsonNode input, String field) {
        String value = text(input, field, 128);
        if (!value.matches("[A-Za-z0-9._-]{1,128}")) throw new IllegalArgumentException("PROGRAM_APPROVAL_ID_INVALID:" + field);
        return value;
    }
    private static String digest(JsonNode input, String field) {
        String value = text(input, field, 64);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("PROGRAM_APPROVAL_DIGEST_INVALID:" + field);
        return value;
    }
    private static int integer(JsonNode input, String field, int minimum, int maximum) {
        JsonNode value = input.path(field);
        if (!value.isIntegralNumber() || value.asInt() < minimum || value.asInt() > maximum)
            throw new IllegalArgumentException("PROGRAM_APPROVAL_NUMBER_INVALID:" + field);
        return value.asInt();
    }
}
