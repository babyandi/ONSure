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
import java.time.Duration;
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
    private static final double MINIMUM_AUTOMATIC_BINDING_CONFIDENCE = 0.90d;
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
        value.put("record_format_version", 2);
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
            if (!clock.instant().isBefore(Instant.parse(value.get("expires_at").toString()))) {
                throw new IllegalArgumentException("PROGRAM_EXECUTION_AUTHORIZATION_EXPIRED");
            }
            Path planFile = workspaceRoot.resolve(value.get("execution_plan_file").toString()).normalize();
            if (!planFile.startsWith(workspaceRoot) || !safeFile(planFile)
                    || !Hashing.file(planFile).equals(planSha256)) {
                throw new IllegalArgumentException("PROGRAM_EXECUTION_PLAN_STALE_OR_TAMPERED");
            }
            String runId = "inferred-e2e-run-" + UUID.randomUUID();
            value.put("state", "EXECUTION_RUNNING");
            value.put("execution_state", "RUNNING");
            value.put("execution_run_id", runId);
            value.put("execution_started_at", clock.instant().toString());
            value.put("execution_started_by", operator.actor());
            value.put("execution_claim_sha256", claimDigest(value));
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

    Map<String, Object> recoverInterruptedExecution(String requestId, String authorizationId,
            String planSha256, LocalAccessControl.Identity operator, Duration staleAfter) throws Exception {
        if (operator == null || !Set.of(LocalAccessControl.Role.ADMIN, LocalAccessControl.Role.OPERATOR)
                .contains(operator.role())) throw new IllegalArgumentException("PROGRAM_EXECUTION_OPERATOR_ROLE_INVALID");
        if (staleAfter == null || staleAfter.compareTo(Duration.ofSeconds(30)) < 0
                || staleAfter.compareTo(Duration.ofHours(1)) > 0)
            throw new IllegalArgumentException("PROGRAM_EXECUTION_RECOVERY_THRESHOLD_INVALID");
        @SuppressWarnings("unchecked") final Map<String, Object>[] result = new Map[1];
        ExclusiveFileLock.run(lock, () -> {
            Map<String, Object> value = read(requestId);
            if (!authorizationId.equals(value.get("execution_authorization_id"))
                    || !planSha256.equals(value.get("execution_plan_sha256")))
                throw new IllegalArgumentException("PROGRAM_EXECUTION_PLAN_BINDING_INVALID");
            if ("CONSUMED_FOR_EXECUTION_AUTHORIZATION".equals(value.get("state"))
                    && "NOT_RUN".equals(value.get("execution_state"))) {
                result[0] = Map.of("recovery_state", "NO_RECOVERY_REQUIRED");
                return;
            }
            if ("EXECUTION_COMPLETED".equals(value.get("state"))) {
                result[0] = Map.of("recovery_state", "ALREADY_COMPLETED");
                return;
            }
            if (!"EXECUTION_RUNNING".equals(value.get("state"))
                    || !"RUNNING".equals(value.get("execution_state")))
                throw new IllegalArgumentException("PROGRAM_EXECUTION_RECOVERY_STATE_INVALID");
            Instant started = Instant.parse(value.get("execution_started_at").toString());
            if (clock.instant().isBefore(started.plus(staleAfter)))
                throw new IllegalArgumentException("PROGRAM_EXECUTION_STILL_ACTIVE");
            Path planFile = workspaceRoot.resolve(value.get("execution_plan_file").toString()).normalize();
            if (!planFile.startsWith(workspaceRoot) || !safeFile(planFile)
                    || !Hashing.file(planFile).equals(planSha256))
                throw new IllegalArgumentException("PROGRAM_EXECUTION_PLAN_STALE_OR_TAMPERED");
            Map<String, Object> plan = mapper.readValue(planFile.toFile(), new TypeReference<>() {});
            Path receiptFile = planFile.resolveSibling("runtime-receipt.json");
            if (Files.exists(receiptFile, LinkOption.NOFOLLOW_LINKS)) {
                Map<String, Object> receipt = verifiedInterruptedReceipt(
                        receiptFile, value.get("execution_run_id").toString(), authorizationId, planSha256);
                String receiptSha = Hashing.file(receiptFile);
                recordRecovery(value, operator, "COMPLETED_FROM_DURABLE_RECEIPT");
                value.put("state", "EXECUTION_COMPLETED");
                value.put("execution_state", receipt.get("outcome"));
                value.put("execution_completed_at", receipt.get("completed_at"));
                value.put("runtime_receipt_sha256", receiptSha);
                value.put("execution_record_sha256", runtimeDigest(value));
                write(requestId, value);
                result[0] = Map.of("recovery_state", "RECOVERED_COMPLETED",
                        "runtime_receipt_sha256", receiptSha,
                        "runtime_receipt_file", workspaceRoot.relativize(receiptFile).toString().replace('\\', '/'));
                return;
            }
            if (readOnlyRetrySafe(plan)) {
                recordRecovery(value, operator, "READ_ONLY_RETRY_ALLOWED");
                value.put("state", "CONSUMED_FOR_EXECUTION_AUTHORIZATION");
                value.put("execution_state", "NOT_RUN");
                write(requestId, value);
                result[0] = Map.of("recovery_state", "RECOVERED_RETRY_ALLOWED");
            } else {
                recordRecovery(value, operator, "WRITE_OUTCOME_UNKNOWN_REAPPROVAL_REQUIRED");
                value.put("state", "EXECUTION_RECOVERY_REQUIRED");
                value.put("execution_state", "RECOVERY_REQUIRED");
                write(requestId, value);
                result[0] = Map.of("recovery_state", "RECOVERY_REAPPROVAL_REQUIRED");
            }
        });
        return result[0];
    }

    Map<String, Object> attachExecutionComparison(String requestId, String runId,
            String comparisonFile, String comparisonSha256) throws Exception {
        if (comparisonFile == null || Path.of(comparisonFile).isAbsolute()
                || comparisonSha256 == null || !comparisonSha256.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("PROGRAM_EXECUTION_COMPARISON_BINDING_INVALID");
        Path file = workspaceRoot.resolve(comparisonFile).normalize();
        Path authorizationRoot = workspaceRoot.resolve(".onsure/inferred-e2e-authorizations").normalize();
        if (!file.startsWith(authorizationRoot) || !safeFile(file)
                || !Hashing.file(file).equals(comparisonSha256))
            throw new IllegalArgumentException("PROGRAM_EXECUTION_COMPARISON_BINDING_INVALID");
        @SuppressWarnings("unchecked") final Map<String, Object>[] result = new Map[1];
        ExclusiveFileLock.run(lock, () -> {
            Map<String, Object> value = read(requestId);
            if (!"EXECUTION_COMPLETED".equals(value.get("state"))
                    || !runId.equals(value.get("execution_run_id")))
                throw new IllegalArgumentException("PROGRAM_EXECUTION_RUN_BINDING_INVALID");
            if (value.containsKey("runtime_comparison_sha256")) {
                if (!comparisonSha256.equals(value.get("runtime_comparison_sha256")))
                    throw new IllegalArgumentException("PROGRAM_EXECUTION_COMPARISON_ALREADY_BOUND");
                result[0] = Map.copyOf(value);
                return;
            }
            value.put("runtime_comparison_file", comparisonFile);
            value.put("runtime_comparison_sha256", comparisonSha256);
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
        if (Set.of("CONSUMED_FOR_EXECUTION_AUTHORIZATION", "EXECUTION_RUNNING", "EXECUTION_COMPLETED",
                "EXECUTION_RECOVERY_REQUIRED")
                .contains(value.get("state"))
                && !String.valueOf(value.get("consumption_sha256")).equals(
                        consumptionDigest(value))) {
            throw new IllegalStateException("PROGRAM_APPROVAL_CONSUMPTION_DIGEST_INVALID");
        }
        if ("EXECUTION_COMPLETED".equals(value.get("state"))
                && !String.valueOf(value.get("execution_record_sha256")).equals(runtimeDigest(value))) {
            throw new IllegalStateException("PROGRAM_EXECUTION_RECORD_DIGEST_INVALID");
        }
        if (value.containsKey("recovery_count")
                && (!validRecoveryHistory(value)
                || !String.valueOf(value.get("recovery_record_sha256")).equals(recoveryDigest(value))))
            throw new IllegalStateException("PROGRAM_EXECUTION_RECOVERY_DIGEST_INVALID");
        if (value.containsKey("execution_run_id")
                && (((Number) value.getOrDefault("record_format_version", 1)).intValue() >= 2
                || value.containsKey("execution_claim_sha256"))) {
            if (!String.valueOf(value.get("execution_claim_sha256")).equals(claimDigest(value)))
                throw new IllegalStateException("PROGRAM_EXECUTION_CLAIM_DIGEST_INVALID");
        }
        return new LinkedHashMap<>(value);
    }

    private Map<String, Object> verifiedInterruptedReceipt(Path file, String runId,
            String authorizationId, String planSha256) throws Exception {
        if (!safeFile(file) || Files.size(file) > 2_097_152L)
            throw new IllegalArgumentException("PROGRAM_EXECUTION_RECOVERY_RECEIPT_INVALID");
        Map<String, Object> receipt = mapper.readValue(file.toFile(), new TypeReference<>() {});
        Object steps = receipt.get("steps");
        if (!LocalInferredE2EHttpRunner.CONTRACT.equals(receipt.get("contract"))
                || !runId.equals(receipt.get("run_id"))
                || !authorizationId.equals(receipt.get("execution_authorization_id"))
                || !planSha256.equals(receipt.get("execution_plan_sha256"))
                || !Set.of("PASS_NONFINAL", "FAIL", "BLOCKED").contains(receipt.get("outcome"))
                || Boolean.TRUE.equals(receipt.get("customer_data_stored"))
                || Boolean.TRUE.equals(receipt.get("response_bodies_stored"))
                || !Boolean.FALSE.equals(receipt.get("final_claim_allowed"))
                || !(steps instanceof List<?> list)
                || ((Number) receipt.getOrDefault("step_count", -1)).intValue() != list.size())
            throw new IllegalArgumentException("PROGRAM_EXECUTION_RECOVERY_RECEIPT_INVALID");
        Instant.parse(receipt.get("completed_at").toString());
        return receipt;
    }

    @SuppressWarnings("unchecked")
    private static boolean readOnlyRetrySafe(Map<String, Object> plan) {
        Object raw = plan.get("authorized_candidates");
        if (!(raw instanceof List<?> candidates)) return false;
        boolean runnable = false;
        for (Object item : candidates) {
            if (!(item instanceof Map<?, ?> candidate)) return false;
            String state = String.valueOf(candidate.get("state"));
            if (state.startsWith("BLOCKED_")) continue;
            runnable = true;
            if (!Set.of("GET", "HEAD", "OPTIONS").contains(String.valueOf(candidate.get("http_method"))))
                return false;
        }
        return runnable;
    }

    private void recordRecovery(Map<String, Object> value, LocalAccessControl.Identity operator,
            String action) throws Exception {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> existing = (List<Map<String, Object>>) value
                .getOrDefault("recovery_history", List.of());
        List<Map<String, Object>> history = new ArrayList<>(existing);
        int count = history.size() + 1;
        String previous = history.isEmpty() ? "GENESIS"
                : history.get(history.size() - 1).get("entry_sha256").toString();
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("sequence", count);
        entry.put("recovered_at", clock.instant().toString());
        entry.put("recovered_by", operator.actor());
        entry.put("from_run_id", value.get("execution_run_id"));
        entry.put("action", action);
        entry.put("previous_entry_sha256", previous);
        entry.put("entry_sha256", recoveryEntryDigest(entry));
        history.add(Map.copyOf(entry));
        value.put("recovery_count", count);
        value.put("recovery_history", List.copyOf(history));
        value.put("last_recovery_at", entry.get("recovered_at"));
        value.put("last_recovery_by", operator.actor());
        value.put("last_recovery_from_run_id", value.get("execution_run_id"));
        value.put("last_recovery_action", action);
        value.put("recovery_record_sha256", recoveryDigest(value));
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
        Map<String, String> runtimeReferences = runtimeReferenceIds(review);
        Set<String> reviewedServiceBoundaries = new java.util.TreeSet<>();
        for (Map<String, Object> draft : drafts) {
            Object boundary = operation(draft).get("service_boundary_id");
            if (boundary != null && boundary.toString().matches("SERVICE-[0-9a-f]{16}")) {
                reviewedServiceBoundaries.add(boundary.toString());
            }
        }
        boolean multiServiceRuntimeUnsupported = reviewedServiceBoundaries.size() > 1;
        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Map<String, Object> draft : drafts) {
            Map<String, Object> operation = operation(draft);
            String method = operation.getOrDefault("http_method", "NONE").toString();
            boolean destructive = "DELETE".equals(method);
            @SuppressWarnings("unchecked")
            List<String> schemaRefs = (List<String>) operation.getOrDefault("request_schema_refs", List.of());
            boolean schemaDeclared = Boolean.TRUE.equals(operation.get("request_schema_declared"));
            boolean securityDeclared = Boolean.TRUE.equals(operation.get("security_declared"));
            String state = multiServiceRuntimeUnsupported ? "BLOCKED_MULTI_SERVICE_RUNTIME_NOT_IMPLEMENTED"
                    : destructive ? "BLOCKED_DESTRUCTIVE_OPERATION"
                    : securityDeclared && !runtimeReferences.containsKey("authentication")
                    ? "BLOCKED_AUTHENTICATION_REFERENCE_MISSING"
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
            candidate.put("security_declared", securityDeclared);
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
        List<Map<String, Object>> lifecycles = authorizedLifecycles(approval, review, candidates);
        candidates = applyBindingBlocks(candidates, lifecycles);
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
        plan.put("authorization_expires_at", approval.get("expires_at"));
        plan.put("runtime_reference_ids", runtimeReferences);
        plan.put("execution_scope", scope);
        plan.put("candidate_count", candidates.size());
        plan.put("blocked_candidate_count", blocked);
        plan.put("authorized_candidates", List.copyOf(candidates));
        plan.put("authorized_lifecycles", lifecycles);
        plan.put("binding_authorization_policy", Map.of(
                "minimum_confidence", MINIMUM_AUTOMATIC_BINDING_CONFIDENCE,
                "allowed_inference_basis", List.of("OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY",
                        "OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_SCHEMA_SINGLETON_ARRAY",
                        "OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_BODY_TYPE_COMPATIBLE"),
                "executable_consumer_locations", List.of("PATH", "QUERY", "HEADER", "BODY"),
                "candidate_only_consumer_locations", List.of(),
                "separate_review_required", true,
                "separate_approval_required", true,
                "unqualified_binding_outcome", "BLOCKED_NOT_RUN",
                "raw_value_storage_allowed", false));
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
    private static List<Map<String, Object>> authorizedLifecycles(
            Map<String, Object> approval, Map<String, Object> review,
            List<Map<String, Object>> candidates) {
        Object raw = review.get("reviewed_api_lifecycle_candidates");
        if (!(raw instanceof List<?> reviewed)) return List.of();
        Map<String, String> planByFlow = new TreeMap<>();
        for (Map<String, Object> candidate : candidates) {
            planByFlow.put(candidate.get("flow_id").toString(), candidate.get("plan_id").toString());
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Object item : reviewed) {
            if (!(item instanceof Map<?, ?> lifecycle)) continue;
            String lifecycleId = String.valueOf(lifecycle.get("lifecycle_id"));
            if (!lifecycleId.matches("LIFECYCLE-[0-9a-f]{16}")) continue;
            List<String> operationPlanIds = new ArrayList<>();
            Object operationsRaw = lifecycle.get("operations");
            if (operationsRaw instanceof List<?> operations) for (Object operationItem : operations) {
                if (!(operationItem instanceof Map<?, ?> operation)) continue;
                String planId = planByFlow.get(String.valueOf(operation.get("flow_id")));
                if (planId != null && !operationPlanIds.contains(planId)) operationPlanIds.add(planId);
            }
            if (operationPlanIds.size() < 2) continue;
            List<Map<String, Object>> bindings = new ArrayList<>();
            List<Map<String, Object>> blockedBindings = new ArrayList<>();
            Object bindingsRaw = lifecycle.get("proposed_bindings");
            if (bindingsRaw instanceof List<?> proposedBindings) for (Object bindingItem : proposedBindings) {
                if (!(bindingItem instanceof Map<?, ?> binding)) continue;
                String producerPlanId = planByFlow.get(String.valueOf(binding.get("producer_flow_id")));
                String consumerPlanId = planByFlow.get(String.valueOf(binding.get("consumer_flow_id")));
                String bindingId = String.valueOf(binding.get("binding_id"));
                String pointer = String.valueOf(binding.get("producer_json_pointer"));
                String location = String.valueOf(binding.get("consumer_location"));
                String parameter = String.valueOf(binding.get("consumer_parameter_name"));
                if (producerPlanId == null || consumerPlanId == null
                        || !operationPlanIds.contains(producerPlanId) || !operationPlanIds.contains(consumerPlanId)
                        || !bindingId.matches("BINDING-[0-9a-f]{16}")
                        || !pointer.matches("(?:/(?:[A-Za-z0-9._-]|~[0123]){1,128}){1,16}")
                        || !Set.of("PATH", "QUERY", "HEADER", "BODY").contains(location)
                        || !validConsumerParameter(location, parameter)) continue;
                String basis = String.valueOf(binding.get("inference_basis"));
                double confidence = binding.get("inference_confidence") instanceof Number number
                        ? number.doubleValue() : -1.0d;
                boolean reviewableInference = "INFERRED_REVIEW_REQUIRED".equals(binding.get("semantic_state"))
                        && Boolean.FALSE.equals(binding.get("auto_execute"))
                        && Boolean.FALSE.equals(binding.get("runtime_verified"))
                        && Boolean.FALSE.equals(binding.get("value_storage_allowed"))
                        && Boolean.FALSE.equals(binding.get("score_eligible"));
                Map<String, Object> authorization = new LinkedHashMap<>();
                authorization.put("binding_id", bindingId);
                authorization.put("producer_plan_id", producerPlanId);
                authorization.put("producer_json_pointer", pointer);
                authorization.put("consumer_plan_id", consumerPlanId);
                authorization.put("consumer_location", location);
                authorization.put("consumer_parameter_name", parameter);
                authorization.put("producer_schema_type",
                        String.valueOf(binding.containsKey("producer_schema_type")
                                ? binding.get("producer_schema_type") : "UNKNOWN"));
                authorization.put("consumer_schema_type",
                        String.valueOf(binding.containsKey("consumer_schema_type")
                                ? binding.get("consumer_schema_type") : "UNKNOWN"));
                authorization.put("inference_basis", basis);
                authorization.put("inference_confidence", confidence);
                authorization.put("review_sha256", review.get("review_sha256"));
                authorization.put("approval_receipt_sha256", approval.get("receipt_sha256"));
                authorization.put("auto_execute_before_approval", false);
                authorization.put("value_storage_allowed", false);
                boolean executableLocation = Set.of("PATH", "QUERY", "HEADER", "BODY").contains(location);
                if (reviewableInference && executableLocation
                        && Set.of("OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY",
                                "OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_SCHEMA_SINGLETON_ARRAY",
                                "OPENAPI_RESPONSE_SCHEMA_EXACT_PROPERTY_BODY_TYPE_COMPATIBLE").contains(basis)
                        && confidence >= MINIMUM_AUTOMATIC_BINDING_CONFIDENCE && confidence <= 1.0d) {
                    authorization.put("review_state", "REVIEWED_AND_SEPARATELY_APPROVED");
                    authorization.put("state", "AUTHORIZED_NOT_RUN");
                    bindings.add(Map.copyOf(authorization));
                } else {
                    authorization.put("review_state", "REVIEW_REQUIRED_NOT_AUTHORIZED");
                    authorization.put("state", "BLOCKED_BINDING_REVIEW_REQUIRED");
                    authorization.put("blocked_reason", basis.startsWith("OPENAPI_BODY_SCHEMA_TYPE_")
                            ? "BODY_SCHEMA_TYPE_INCOMPATIBLE_OR_UNVERIFIED"
                            : executableLocation
                            ? "INFERENCE_CONFIDENCE_OR_BASIS_NOT_AUTOMATICALLY_AUTHORIZABLE"
                            : "CONSUMER_LOCATION_RUNNER_NOT_IMPLEMENTED");
                    blockedBindings.add(Map.copyOf(authorization));
                }
            }
            Map<String, Object> authorized = new LinkedHashMap<>();
            authorized.put("lifecycle_id", lifecycleId);
            authorized.put("business_object", String.valueOf(lifecycle.get("business_object")));
            authorized.put("operation_plan_ids", List.copyOf(operationPlanIds));
            authorized.put("bindings", List.copyOf(bindings));
            authorized.put("binding_count", bindings.size());
            authorized.put("blocked_bindings", List.copyOf(blockedBindings));
            authorized.put("blocked_binding_count", blockedBindings.size());
            authorized.put("execution_state", blockedBindings.isEmpty()
                    ? "AUTHORIZED_NOT_RUN" : "PARTIAL_AUTHORIZATION_BLOCKED_NOT_RUN");
            authorized.put("response_values_storage_allowed", false);
            authorized.put("final_claim_allowed", false);
            result.add(Map.copyOf(authorized));
        }
        return List.copyOf(result);
    }

    private static boolean validConsumerParameter(String location, String parameter) {
        if ("BODY".equals(location))
            return parameter.matches("(?:/(?:[A-Za-z0-9._-]|~[01]){1,128}){1,16}");
        return parameter.matches("[A-Za-z][A-Za-z0-9._-]{0,127}");
    }

    private static List<Map<String, Object>> applyBindingBlocks(
            List<Map<String, Object>> candidates, List<Map<String, Object>> lifecycles) {
        Set<String> blockedConsumers = new java.util.HashSet<>();
        for (Map<String, Object> lifecycle : lifecycles) {
            Object raw = lifecycle.get("blocked_bindings");
            if (!(raw instanceof List<?> blocked)) continue;
            for (Object item : blocked) if (item instanceof Map<?, ?> binding)
                blockedConsumers.add(String.valueOf(binding.get("consumer_plan_id")));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> candidate : candidates) {
            if (!blockedConsumers.contains(String.valueOf(candidate.get("plan_id")))
                    || String.valueOf(candidate.get("state")).startsWith("BLOCKED_")) {
                result.add(candidate);
                continue;
            }
            Map<String, Object> blocked = new LinkedHashMap<>(candidate);
            blocked.put("state", "BLOCKED_BINDING_REVIEW_REQUIRED");
            blocked.put("binding_execution_state", "NOT_RUN");
            result.add(Map.copyOf(blocked));
        }
        return List.copyOf(result);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> runtimeReferenceIds(Map<String, Object> review) {
        Map<String, String> result = new TreeMap<>();
        Object raw = review.get("answers");
        if (!(raw instanceof List<?> answers)) return Map.of();
        for (Object item : answers) {
            if (!(item instanceof Map<?, ?> answer)
                    || !"CONFIRMED".equals(answer.get("answer_state"))
                    || !(answer.get("evidence_reference_id") instanceof String reference)
                    || !reference.matches("env:[A-Z][A-Z0-9_]{1,127}")) continue;
            String question = String.valueOf(answer.get("question_id"));
            if ("AUTHENTICATION_CONTEXT".equals(question)) result.put("authentication", reference);
            if ("SAFE_TEST_IDENTITY".equals(question)) result.put("test_identity", reference);
        }
        return Map.copyOf(result);
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
        if (value.containsKey("record_format_version"))
            immutable.put("record_format_version", value.get("record_format_version"));
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
        if (value.containsKey("runtime_comparison_sha256")) {
            immutable.put("runtime_comparison_file", value.get("runtime_comparison_file"));
            immutable.put("runtime_comparison_sha256", value.get("runtime_comparison_sha256"));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(immutable));
    }

    private String claimDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> immutable = new TreeMap<>();
        for (String key : List.of("consumption_sha256", "execution_run_id", "execution_started_at",
                "execution_started_by")) {
            if (!value.containsKey(key)) throw new IllegalStateException("PROGRAM_EXECUTION_CLAIM_FIELD_MISSING:" + key);
            immutable.put(key, value.get(key));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(immutable));
    }

    private String recoveryDigest(Map<String, Object> value) throws Exception {
        Map<String, Object> immutable = new TreeMap<>();
        for (String key : List.of("consumption_sha256", "recovery_count", "recovery_history", "last_recovery_at",
                "last_recovery_by", "last_recovery_from_run_id", "last_recovery_action")) {
            if (!value.containsKey(key)) throw new IllegalStateException("PROGRAM_EXECUTION_RECOVERY_FIELD_MISSING:" + key);
            immutable.put(key, value.get(key));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(immutable));
    }

    private String recoveryEntryDigest(Map<String, Object> entry) throws Exception {
        Map<String, Object> immutable = new TreeMap<>();
        for (String key : List.of("sequence", "recovered_at", "recovered_by", "from_run_id",
                "action", "previous_entry_sha256")) {
            if (!entry.containsKey(key)) return "INVALID";
            immutable.put(key, entry.get(key));
        }
        return Hashing.sha256(mapper.writeValueAsBytes(immutable));
    }

    private boolean validRecoveryHistory(Map<String, Object> value) throws Exception {
        if (!(value.get("recovery_history") instanceof List<?> history)
                || !(value.get("recovery_count") instanceof Number count)
                || count.intValue() != history.size() || history.isEmpty()) return false;
        String previous = "GENESIS";
        int sequence = 1;
        for (Object raw : history) {
            if (!(raw instanceof Map<?, ?> entry)
                    || !Integer.valueOf(sequence).equals(entry.get("sequence"))
                    || !previous.equals(entry.get("previous_entry_sha256"))) return false;
            @SuppressWarnings("unchecked") Map<String, Object> typed = (Map<String, Object>) entry;
            String digest = recoveryEntryDigest(typed);
            if (!digest.equals(entry.get("entry_sha256"))) return false;
            previous = digest;
            sequence++;
        }
        return true;
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
