package kr.co.oruda.onsure.platform;

import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Fail-closed runtime boundary for enterprise capabilities that were previously design-only.
 *
 * <p>The runtime does not claim that an external product, provider, or deployment was tested.
 * It validates a source-bound request, enforces capability-specific controls, and records the
 * accepted transition in a durable hash ledger. External execution evidence remains NOT_RUN
 * until a product lane supplies and independently verifies it.
 */
public final class EnterpriseCapabilityRuntime {
    public enum Capability {
        REGULATORY_CONTROL,
        FINANCIAL_SECURITY,
        OPERATIONS,
        UNIFIED_WORKBENCH,
        WORK_ARTIFACT,
        TOOL_CONNECTOR,
        AUTOMATION,
        PACKAGE_DELIVERY,
        ORCHESTRATION_MEMORY,
        VENDOR_SUPPLY_CHAIN
    }

    public record Request(
            String requestId,
            Capability capability,
            String sourceSha256,
            String tenantId,
            String actor,
            Instant approvedUntil,
            Set<String> controls,
            Map<String, String> claims,
            String parentReceiptSha256) {
        public Request {
            controls = controls == null ? Set.of() : Set.copyOf(controls);
            claims = claims == null ? Map.of() : Map.copyOf(claims);
        }
    }

    public record Result(
            String decision,
            String requestId,
            Capability capability,
            List<String> violations,
            Map<String, Object> receipt) {
        public Result {
            violations = List.copyOf(violations);
            receipt = Map.copyOf(receipt);
        }
    }

    private static final Map<Capability, Set<String>> REQUIRED_CONTROLS = Map.of(
            Capability.REGULATORY_CONTROL, Set.of(
                    "SOURCE_VERSION_PINNED", "APPLICABILITY_RECORDED", "LEGAL_APPROVAL_REQUIRED"),
            Capability.FINANCIAL_SECURITY, Set.of(
                    "MFA", "RBAC_ABAC", "SOD", "TENANT_ISOLATION", "KMS_HSM", "WORM_AUDIT"),
            Capability.OPERATIONS, Set.of(
                    "IDEMPOTENCY", "CHECKPOINT", "PAUSE_RESUME", "RTO_RPO", "BACKUP_RESTORE"),
            Capability.UNIFIED_WORKBENCH, Set.of(
                    "PROJECT_BOUND", "SESSION_BOUND", "TASK_GRAPH_BOUND", "CHECKPOINT_BOUND"),
            Capability.WORK_ARTIFACT, Set.of(
                    "SOURCE_CITATIONS", "NATIVE_OBJECT_READBACK", "RENDER_QA"),
            Capability.TOOL_CONNECTOR, Set.of(
                    "SIGNED_MANIFEST", "LEAST_PRIVILEGE", "NETWORK_BOUNDARY", "DATA_BOUNDARY"),
            Capability.AUTOMATION, Set.of(
                    "EXECUTION_LIMIT", "IDEMPOTENCY", "RETRY_LIMIT", "STOP_CONDITION", "NOTIFICATION"),
            Capability.PACKAGE_DELIVERY, Set.of(
                    "SIGNATURE", "HASH", "SBOM", "MALWARE_SCAN", "LICENSE_SCAN",
                    "ISOLATED_INSTALL", "ROLLBACK"),
            Capability.ORCHESTRATION_MEMORY, Set.of(
                    "PROJECT_SCOPE", "PROVENANCE", "EXPIRY", "PROMOTION_APPROVAL"),
            Capability.VENDOR_SUPPLY_CHAIN, Set.of(
                    "ORIGIN", "CONTRACT", "REGION", "RETENTION", "SUBPROCESSOR",
                    "PROVENANCE", "EXIT_PLAN"));

    private final Path authorityRoot;
    private final String expectedSourceSha256;

    public EnterpriseCapabilityRuntime(Path authorityRoot, String expectedSourceSha256) {
        this.authorityRoot = authorityRoot.toAbsolutePath().normalize();
        this.expectedSourceSha256 = requireSha(expectedSourceSha256, "EXPECTED_SOURCE_SHA_INVALID");
    }

    public Result execute(Request request, Instant now) throws Exception {
        List<String> violations = validate(request, now);
        if (!violations.isEmpty()) {
            return new Result("BLOCK", request == null ? "" : safe(request.requestId()),
                    request == null ? null : request.capability(), violations,
                    Map.of("final_claim_allowed", false, "assurance_class", "SELF_VALIDATION_NONFINAL"));
        }

        Path requestRoot = containedRequestRoot(request.requestId());
        DurableStateLedger ledger = new DurableStateLedger(
                requestRoot, "ONSURE_ENTERPRISE_CAPABILITY_STATE_V1",
                "ONSURE_ENTERPRISE_CAPABILITY_EVENT_V1", "request_id", request.requestId());
        Map<String, Object> state = new LinkedHashMap<>();
        state.put("capability", request.capability().name());
        state.put("source_sha256", request.sourceSha256());
        state.put("tenant_id", request.tenantId());
        state.put("parent_receipt_sha256", request.parentReceiptSha256());
        state.put("controls", new ArrayList<>(new java.util.TreeSet<>(request.controls())));
        state.put("claims", new java.util.TreeMap<>(request.claims()));
        state.put("external_execution", "NOT_RUN");
        state.put("independent_verification", "NOT_RUN");
        Map<String, Object> receipt = ledger.initialize(
                state, "REQUEST_ACCEPTED_NONFINAL", request.actor(),
                Map.of("capability", request.capability().name(),
                        "source_sha256", request.sourceSha256()));
        return new Result("PASS_NONFINAL", request.requestId(), request.capability(), List.of(), receipt);
    }

    public List<String> validate(Request request, Instant now) {
        LinkedHashSet<String> errors = new LinkedHashSet<>();
        if (request == null) return List.of("REQUEST_MISSING");
        if (!safe(request.requestId()).matches("[A-Za-z0-9][A-Za-z0-9._-]{2,95}")) {
            errors.add("REQUEST_ID_INVALID");
        }
        if (request.capability() == null) errors.add("CAPABILITY_MISSING");
        if (!expectedSourceSha256.equals(request.sourceSha256())) errors.add("SOURCE_SHA_MISMATCH");
        if (safe(request.tenantId()).isBlank()) errors.add("TENANT_ID_MISSING");
        if (safe(request.actor()).isBlank()) errors.add("ACTOR_MISSING");
        if (request.approvedUntil() == null || now == null || !request.approvedUntil().isAfter(now)) {
            errors.add("APPROVAL_EXPIRED_OR_MISSING");
        }
        if (!isSha(request.parentReceiptSha256())) errors.add("PARENT_RECEIPT_SHA_INVALID");
        if (request.capability() != null) {
            for (String control : REQUIRED_CONTROLS.get(request.capability())) {
                if (!request.controls().contains(control)) errors.add("CONTROL_MISSING:" + control);
            }
        }
        if (!"NOT_RUN".equals(request.claims().get("external_execution"))) {
            errors.add("EXTERNAL_EXECUTION_OVERCLAIMED");
        }
        if (!"NOT_RUN".equals(request.claims().get("independent_verification"))) {
            errors.add("INDEPENDENT_VERIFICATION_OVERCLAIMED");
        }
        return List.copyOf(errors);
    }

    private Path containedRequestRoot(String requestId) throws Exception {
        Files.createDirectories(authorityRoot);
        if (Files.isSymbolicLink(authorityRoot)) throw new IllegalStateException("AUTHORITY_ROOT_SYMLINK");
        Path candidate = authorityRoot.resolve(requestId).normalize();
        if (!candidate.startsWith(authorityRoot)) throw new IllegalStateException("REQUEST_PATH_ESCAPE");
        if (Files.exists(candidate, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalStateException("REQUEST_REPLAY");
        }
        return candidate;
    }

    public static Set<String> requiredControls(Capability capability) {
        if (capability == null) return Set.of();
        return REQUIRED_CONTROLS.get(capability);
    }

    private static String requireSha(String value, String error) {
        if (!isSha(value)) throw new IllegalArgumentException(error);
        return value;
    }

    private static boolean isSha(String value) {
        return value != null && value.matches("[0-9a-f]{64}");
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
