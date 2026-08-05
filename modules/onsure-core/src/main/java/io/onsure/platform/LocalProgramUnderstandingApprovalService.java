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
            value.put("decision", decision);
            value.put("decided_at", clock.instant().toString());
            value.put("decided_by", approver.actor());
            value.put("decision_reason", reason);
            value.put("execution_consumed", false);
            value.put("execution_state", "NOT_RUN");
            value.put("receipt_sha256", decisionDigest(value));
            write(requestId, value);
            result[0] = Map.copyOf(value);
        });
        return result[0];
    }

    Map<String, Object> consume(JsonNode input, LocalAccessControl.Identity operator) throws Exception {
        if (operator == null || !Set.of(LocalAccessControl.Role.ADMIN, LocalAccessControl.Role.OPERATOR)
                .contains(operator.role())) throw new IllegalArgumentException("PROGRAM_APPROVAL_CONSUMER_ROLE_INVALID");
        requireExactFields(input, Set.of("request_id", "receipt_sha256", "execution_scope"));
        String requestId = text(input, "request_id", 180);
        if (!requestId.matches("program-understanding-approval-[0-9a-f-]{36}")) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_REQUEST_ID_INVALID");
        }
        String receiptSha = digest(input, "receipt_sha256");
        String scope = text(input, "execution_scope", 64);
        if (!"ISOLATED_SYNTHETIC_LOOPBACK".equals(scope)) {
            throw new IllegalArgumentException("PROGRAM_APPROVAL_EXECUTION_SCOPE_INVALID");
        }
        @SuppressWarnings("unchecked")
        final Map<String, Object>[] result = new Map[1];
        ExclusiveFileLock.run(lock, () -> {
            Map<String, Object> value = read(requestId);
            if (!"APPROVED_NOT_EXECUTED".equals(value.get("state"))
                    || Boolean.TRUE.equals(value.get("execution_consumed"))) {
                throw new IllegalArgumentException("PROGRAM_APPROVAL_ALREADY_CONSUMED_OR_NOT_APPROVED");
            }
            if (!receiptSha.equals(value.get("receipt_sha256"))) {
                throw new IllegalArgumentException("PROGRAM_APPROVAL_RECEIPT_BINDING_INVALID");
            }
            if (!clock.instant().isBefore(Instant.parse(value.get("expires_at").toString()))) {
                throw new IllegalArgumentException("PROGRAM_APPROVAL_REQUEST_EXPIRED");
            }
            Map<String, Object> review = currentReview(value.get("target_id").toString(), value.get("profile_file_sha256").toString(),
                    value.get("review_sha256").toString());
            String authorizationId = "inferred-e2e-auth-" + UUID.randomUUID();
            Map<String, Object> executionPlan = executionAuthorizationPlan(value, review, authorizationId, scope);
            byte[] planBytes = mapper.writeValueAsBytes(executionPlan);
            String planSha256 = Hashing.sha256(planBytes);
            Path planFile = workspaceRoot.resolve(".onsure/inferred-e2e-authorizations")
                    .resolve(authorizationId).resolve("execution-plan.json").normalize();
            writeAuthorizationPlan(planFile, executionPlan);
            value.put("state", "CONSUMED_FOR_EXECUTION_AUTHORIZATION");
            value.put("execution_consumed", true);
            value.put("consumed_at", clock.instant().toString());
            value.put("consumed_by", operator.actor());
            value.put("authorized_execution_scope", scope);
            value.put("execution_authorization_id", authorizationId);
            value.put("execution_plan_file", workspaceRoot.relativize(planFile).toString().replace('\\', '/'));
            value.put("execution_plan_sha256", planSha256);
            value.put("execution_plan_state", executionPlan.get("plan_state"));
            value.put("execution_state", "NOT_RUN");
            value.put("consumption_sha256", consumptionDigest(value));
            try {
                write(requestId, value);
            } catch (Exception error) {
                Files.deleteIfExists(planFile);
                throw error;
            }
            result[0] = Map.copyOf(value);
        });
        return result[0];
    }

    Map<String, Object> claimExecution(String requestId, String authorizationId, String planSha256,
            LocalAccessControl.Identity operator) throws Exception {
        if (operator == null || !Set.of(LocalAccessControl.Role.ADMIN, LocalAccessControl.Role.OPERATOR)
                .contains(operator.role())) throw new IllegalArgumentException("PROGRAM_EXECUTION_OPERATOR_ROLE_INVALID");
        @SuppressWarnings("unchecked") final Map<String, Object>[] result = new Map[1];
        ExclusiveFileLock.run(lock, () -> {
            Map<String, Object> value = read(requestId);
            if (!"CONSUMED_FOR_EXECUTION_AUTHORIZATION".equals(value.get("state"))
                    || !"NOT_RUN".equals(value.get("execution_state"))) {
                throw new IllegalArgumentException("PROGRAM_EXECUTION_AUTHORIZATION_ALREADY_CLAIMED");
            }
            if (!authorizationId.equals(value.get("execution_authorization_id"))
                    || !planSha256.equals(value.get("execution_plan_sha256"))) {
                throw new IllegalArgumentException("PROGRAM_EXECUTION_PLAN_BINDING_INVALID");
            }
            String runId = "inferred-e2e-run-" + UUID.randomUUID();
            value.put("state", "EXECUTION_RUNNING");
            value.put("execution_state", "RUNNING");
            value.put("execution_run_id", runId);
            value.put("execution_started_at", clock.instant().toString());
            value.put("execution_started_by", operator.actor());
            write(requestId, value);
            result[0] = Map.copyOf(value);
        });
        return result[0];
    }

    Map<String, Object> completeExecution(String requestId, String runId, String outcome,
            String runtimeReceiptSha256) throws Exception {
        if (!Set.of("PASS_NONFINAL", "FAIL", "BLOCKED").contains(outcome)
                || !runtimeReceiptSha256.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("PROGRAM_EXECUTION_COMPLETION_INVALID");
        }
        @SuppressWarnings("unchecked") final Map<String, Object>[] result = new Map[1];
        ExclusiveFileLock.run(lock, () -> {
            Map<String, Object> value = read(requestId);
            if (!"EXECUTION_RUNNING".equals(value.get("state"))
                    || !runId.equals(value.get("execution_run_id"))) {
                throw new IllegalArgumentException("PROGRAM_EXECUTION_RUN_BINDING_INVALID");
            }
            value.put("state", "EXECUTION_COMPLETED");
            value.put("execution_state", outcome);
            value.put("execution_completed_at", clock.instant().toString());
            value.put("runtime_receipt_sha256", runtimeReceiptSha256);
            value.put("execution_record_sha256", runtimeDigest(value));
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
                && !String.valueOf(value.get("receipt_sha256")).equals(decisionDigest(value))) {
            throw new IllegalStateException("PROGRAM_APPROVAL_RECEIPT_DIGEST_INVALID");
        }
        if (Set.of("CONSUMED_FOR_EXECUTION_AUTHORIZATION", "EXECUTION_RUNNING", "EXECUTION_COMPLETED")
                .contains(value.get("state"))
                && !String.valueOf(value.get("consumption_sha256")).equals(
                        consumptionDigest(value))) {
            throw new IllegalStateException("PROGRAM_APPROVAL_CONSUMPTION_DIGEST_INVALID");
        }
        if ("EXECUTION_COMPLETED".equals(value.get("state"))
                && !String.valueOf(value.get("execution_record_sha256")).equals(runtimeDigest(value))) {
            throw new IllegalStateException("PROGRAM_EXECUTION_RECORD_DIGEST_INVALID");
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

    private void writeAuthorizationPlan(Path file, Map<String, Object> value) throws Exception {
        Path authorizationRoot = workspaceRoot.resolve(".onsure/inferred-e2e-authorizations").normalize();
        if (!file.startsWith(authorizationRoot)) throw new IllegalStateException("PROGRAM_EXECUTION_PLAN_PATH_INVALID");
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + "." + UUID.randomUUID() + ".tmp");
        mapper.writeValue(temporary.toFile(), value);
        try { Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE); }
        catch (java.nio.file.AtomicMoveNotSupportedException ignored) { Files.move(temporary, file); }
        finally { Files.deleteIfExists(temporary); }
    }

    private Map<String, Object> executionAuthorizationPlan(
            Map<String, Object> approval, Map<String, Object> review, String authorizationId, String scope) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> drafts = (List<Map<String, Object>>) review
                .getOrDefault("reviewed_e2e_plan_draft", List.of());
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> draft : drafts) {
            Map<String, Object> operation = operation(draft);
            String method = operation.getOrDefault("http_method", "NONE").toString();
            boolean destructive = "DELETE".equals(method);
            @SuppressWarnings("unchecked")
            List<String> schemaRefs = (List<String>) operation.getOrDefault("request_schema_refs", List.of());
            boolean schemaDeclared = Boolean.TRUE.equals(operation.get("request_schema_declared"));
            String state = destructive ? "BLOCKED_DESTRUCTIVE_OPERATION"
                    : List.of("POST", "PUT", "PATCH").contains(method) && !schemaDeclared
                    ? "BLOCKED_SYNTHETIC_FIXTURE_SCHEMA_MISSING"
                    : List.of("POST", "PUT", "PATCH").contains(method)
                    ? "READY_FOR_SYNTHETIC_FIXTURE_GENERATION"
                    : operation.getOrDefault("http_path", "").toString().contains("{")
                    ? "READY_FOR_PATH_PARAMETER_FIXTURE_GENERATION" : "READY_FOR_ISOLATED_LOOPBACK_RUNNER";
            Map<String, Object> candidate = new LinkedHashMap<>();
            candidate.put("plan_id", draft.getOrDefault("plan_id", "UNVERIFIED"));
            candidate.put("flow_id", draft.getOrDefault("flow_id", "UNVERIFIED"));
            candidate.put("http_method", method);
            candidate.put("http_path", operation.getOrDefault("http_path", "NOT_APPLICABLE"));
            candidate.put("request_schema_refs", schemaRefs);
            candidate.put("request_schema_declared", schemaDeclared);
            candidate.put("response_statuses", operation.getOrDefault("response_statuses", List.of()));
            candidate.put("openapi_source_path", operation.getOrDefault("source_path", "NOT_APPLICABLE"));
            candidate.put("openapi_source_sha256", operation.getOrDefault("evidence_sha256", "NOT_APPLICABLE"));
            candidate.put("fixture_strategy", !schemaDeclared
                    ? "NO_BODY_OR_REVIEWED_FIXTURE_REFERENCE_REQUIRED" : "DETERMINISTIC_SYNTHETIC_FROM_OPENAPI_SCHEMA");
            candidate.put("oracle_strategy", "DECLARED_RESPONSE_STATUS_AND_SCHEMA_PLUS_DIGEST_RECEIPT");
            candidate.put("state", state);
            candidate.put("customer_data_allowed", false);
            candidate.put("destructive_action_allowed", false);
            candidates.add(Map.copyOf(candidate));
        }
        long blocked = candidates.stream().filter(candidate -> candidate.get("state").toString().startsWith("BLOCKED_"))
                .count();
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("contract", "ONSURE_INFERRED_E2E_EXECUTION_AUTHORIZATION_V1");
        plan.put("execution_authorization_id", authorizationId);
        plan.put("project_id", approval.get("project_id"));
        plan.put("target_id", approval.get("target_id"));
        plan.put("source_sha256", approval.get("source_sha256"));
        plan.put("profile_file_sha256", approval.get("profile_file_sha256"));
        plan.put("review_sha256", approval.get("review_sha256"));
        plan.put("approval_request_sha256", approval.get("request_sha256"));
        plan.put("approval_request_id", approval.get("request_id"));
        plan.put("approval_receipt_sha256", approval.get("receipt_sha256"));
        plan.put("execution_scope", scope);
        plan.put("candidate_count", candidates.size());
        plan.put("blocked_candidate_count", blocked);
        plan.put("authorized_candidates", List.copyOf(candidates));
        plan.put("plan_state", blocked == 0 && !candidates.isEmpty()
                ? "AUTHORIZED_NOT_RUN" : "PARTIAL_AUTHORIZATION_BLOCKED_NOT_RUN");
        plan.put("execution_state", "NOT_RUN");
        plan.put("source_mutation_allowed", false);
        plan.put("customer_data_allowed", false);
        plan.put("destructive_action_allowed", false);
        plan.put("final_claim_allowed", false);
        return Map.copyOf(plan);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> operation(Map<String, Object> draft) {
        Object steps = draft.get("proposed_steps");
        if (!(steps instanceof List<?> list)) return Map.of();
        for (Object step : list) {
            if (step instanceof Map<?, ?> map && map.get("source_derived_invocation") instanceof Map<?, ?> operation) {
                return (Map<String, Object>) operation;
            }
        }
        return Map.of();
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

    private String decisionDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> immutable = new TreeMap<>();
        for (String key : List.of("request_sha256", "decision", "decided_at", "decided_by",
                "decision_reason", "final_claim_allowed")) {
            if (!value.containsKey(key)) throw new IllegalStateException("PROGRAM_APPROVAL_DECISION_FIELD_MISSING:" + key);
            immutable.put(key, value.get(key));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(immutable));
    }

    private String consumptionDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> immutable = new TreeMap<>();
        for (String key : List.of("request_sha256", "receipt_sha256", "consumed_at", "consumed_by",
                "authorized_execution_scope", "execution_authorization_id", "execution_plan_file",
                "execution_plan_sha256", "execution_plan_state")) {
            if (!value.containsKey(key)) throw new IllegalStateException("PROGRAM_APPROVAL_CONSUMPTION_FIELD_MISSING:" + key);
            immutable.put(key, value.get(key));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(immutable));
    }

    private String runtimeDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> immutable = new TreeMap<>();
        for (String key : List.of("consumption_sha256", "execution_run_id", "execution_started_at",
                "execution_started_by", "execution_completed_at", "execution_state", "runtime_receipt_sha256")) {
            if (!value.containsKey(key)) throw new IllegalStateException("PROGRAM_EXECUTION_RECORD_FIELD_MISSING:" + key);
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
