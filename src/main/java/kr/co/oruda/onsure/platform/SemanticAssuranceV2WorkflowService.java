package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Candidate runtime boundary for Semantic Assurance v2 operations.
 *
 * <p>All methods are explicitly NON_FINAL. They provide a concrete implementation boundary for
 * dispatcher wiring without granting merge/final/production authority.</p>
 */
public final class SemanticAssuranceV2WorkflowService {
    public static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_WORKFLOW_SERVICE_V1";
    private static final Set<String> OPERATIONS = Set.of(
            "semantic.applicability.evaluate",
            "semantic.denominator.discover",
            "semantic.denominator.challenge",
            "semantic.denominator.lock",
            "semantic.reperformance.run",
            "semantic.authority.revalidate",
            "semantic.independence.assess",
            "semantic.freshness.invalidate",
            "semantic.freshness.reconstruct",
            "semantic.validator.requalify",
            "assurance.otester.accept",
            "assurance.oaudit.accept",
            "assurance.human-accept",
            "assurance.final-candidate.reconstruct",
            "git.push",
            "deployment.verify-installed");

    private final Path workspaceRoot;
    private final AuthenticatedWorkflowIdentity identity;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final SemanticAssuranceV2Reconstructor reconstructor = new SemanticAssuranceV2Reconstructor();

    public SemanticAssuranceV2WorkflowService(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        if (workspaceRoot == null || identity == null) throw new IllegalArgumentException("V2_WORKFLOW_CONTEXT_REQUIRED");
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.identity = identity;
    }

    public static boolean supports(String operation) {
        return OPERATIONS.contains(operation);
    }

    public Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (!supports(operation)) throw new IllegalArgumentException("SEMANTIC_V2_OPERATION_UNSUPPORTED:" + operation);
        if (request == null || !request.isObject()) throw new IllegalArgumentException("SEMANTIC_V2_REQUEST_OBJECT_REQUIRED");
        Map<String, Object> result = switch (operation) {
            case "semantic.applicability.evaluate" -> applicability(request);
            case "semantic.denominator.discover" -> denominator(request, "DISCOVERED");
            case "semantic.denominator.challenge" -> denominator(request, "CHALLENGED");
            case "semantic.denominator.lock" -> denominatorLock(request);
            case "semantic.reperformance.run" -> reperformance(request);
            case "semantic.authority.revalidate" -> authorityRevalidate(request);
            case "semantic.independence.assess" -> independenceAssess(request);
            case "semantic.freshness.invalidate" -> freshness(request, "INVALIDATED");
            case "semantic.freshness.reconstruct" -> freshness(request, "REASSESSMENT_REQUIRED");
            case "semantic.validator.requalify" -> requalify(request);
            case "assurance.otester.accept" -> independentAccept(request, "OTESTER");
            case "assurance.oaudit.accept" -> independentAccept(request, "OAUDIT");
            case "assurance.human-accept" -> humanAccept(request);
            case "assurance.final-candidate.reconstruct" -> finalCandidate(request);
            case "git.push" -> externalEffectNotImplemented(operation, request);
            case "deployment.verify-installed" -> verifyInstalled(request);
            default -> throw new IllegalStateException("SEMANTIC_V2_OPERATION_SWITCH_GAP:" + operation);
        };
        return envelope(operation, result);
    }

    private Map<String, Object> applicability(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        List<Map<String, Object>> rows = new ArrayList<>();
        JsonNode capabilities = request.path("capabilities");
        if (!capabilities.isArray() || capabilities.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("CAPABILITY_SET_REQUIRED"));
        }
        for (JsonNode item : capabilities) {
            String id = requiredText(item, "capability_id");
            String disposition = item.path("disposition").asText("INPUT_REQUIRED");
            String rationale = item.path("rationale").asText("");
            if ("NOT_APPLICABLE_JUSTIFIED".equals(disposition) && rationale.isBlank()) {
                disposition = "INPUT_REQUIRED";
            }
            rows.add(Map.of(
                    "capability_id", id,
                    "disposition", disposition,
                    "rationale", rationale));
        }
        Map<String, Object> out = base("SEMANTIC_APPLICABILITY_SET", targetId);
        out.put("items", rows);
        out.put("population_digest", digest(rows));
        out.put("decision", rows.stream().anyMatch(row -> "INPUT_REQUIRED".equals(row.get("disposition"))) ? "HOLD" : "NON_FINAL");
        return Map.copyOf(out);
    }

    private Map<String, Object> denominator(JsonNode request, String mode) {
        String targetId = requiredText(request, "target_id");
        JsonNode items = request.path("items");
        if (!items.isArray()) return failClosed("INPUT_REQUIRED", List.of("DENOMINATOR_ITEMS_REQUIRED"));
        List<Map<String, Object>> normalized = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (JsonNode item : items) {
            String id = requiredText(item, "item_id");
            if (!ids.add(id)) return failClosed("HOLD", List.of("DUPLICATE_DENOMINATOR_ID:" + id));
            String sha = requiredDigest(item, "item_sha256");
            String disposition = item.path("disposition").asText("INCLUDED");
            normalized.add(Map.of("item_id", id, "item_sha256", sha, "disposition", disposition));
        }
        Map<String, Object> out = base("DENOMINATOR_" + mode, targetId);
        out.put("mode", mode);
        out.put("item_count", normalized.size());
        out.put("items", normalized);
        out.put("population_digest", digest(normalized));
        out.put("decision", "NON_FINAL");
        return Map.copyOf(out);
    }

    private Map<String, Object> denominatorLock(JsonNode request) {
        Map<String, Object> out = denominator(request, "LOCK");
        if (!"NON_FINAL".equals(out.get("decision"))) return out;
        Map<String, Object> mutable = new LinkedHashMap<>(out);
        mutable.put("epoch", request.path("epoch").asText("UNASSIGNED"));
        mutable.put("locked_at", Instant.now().toString());
        mutable.put("lock_is_final_authority", false);
        return Map.copyOf(mutable);
    }

    private Map<String, Object> reperformance(JsonNode request) throws Exception {
        Path subject = requiredInputPath(request, "subject_path");
        String expected = requiredDigest(request, "subject_sha256");
        String actual = Hashing.file(subject);
        boolean same = expected.equals(actual);
        Map<String, Object> out = base("REPERFORMANCE_RESULT", request.path("target_id").asText("UNKNOWN_TARGET"));
        out.put("subject_path", Hashing.relative(workspaceRoot, subject));
        out.put("expected_sha256", expected);
        out.put("actual_sha256", actual);
        out.put("readback_equal", same);
        out.put("oracle_state", request.path("oracle_state").asText("NOT_RUN"));
        out.put("decision", same && "PASS".equals(request.path("oracle_state").asText()) ? "NON_FINAL" : "HOLD");
        return Map.copyOf(out);
    }

    private Map<String, Object> authorityRevalidate(JsonNode request) {
        List<String> missing = new ArrayList<>();
        for (String field : List.of("principal_profile_sha256", "authority_epoch", "purpose", "effect_at")) {
            if (request.path(field).asText("").isBlank()) missing.add(field);
        }
        boolean revoked = request.path("revoked").asBoolean(true);
        boolean expired = request.path("expired").asBoolean(true);
        if (revoked) missing.add("AUTHORITY_REVOKED");
        if (expired) missing.add("AUTHORITY_EXPIRED");
        Map<String, Object> out = base("AUTHORITY_REVALIDATION", request.path("target_id").asText("UNKNOWN_TARGET"));
        out.put("principal_profile_sha256", request.path("principal_profile_sha256").asText(""));
        out.put("authority_epoch", request.path("authority_epoch").asText(""));
        out.put("valid_at_effect", missing.isEmpty());
        out.put("reasons", missing);
        out.put("decision", missing.isEmpty() ? "NON_FINAL" : "HOLD");
        return Map.copyOf(out);
    }

    private Map<String, Object> independenceAssess(JsonNode request) {
        String principalA = request.path("producer_principal_id").asText("");
        String principalB = request.path("verifier_principal_id").asText("");
        String adminA = request.path("producer_admin_owner_id").asText("");
        String adminB = request.path("verifier_admin_owner_id").asText("");
        boolean principalDistinct = !principalA.isBlank() && !principalA.equals(principalB);
        boolean adminDistinct = !adminA.isBlank() && !adminA.equals(adminB);
        boolean implementationDistinct = request.path("implementation_independent").asBoolean(false);
        boolean oracleDistinct = request.path("oracle_independent").asBoolean(false);
        boolean discoveryDistinct = request.path("discovery_independent").asBoolean(false);
        boolean knowledgeDistinct = request.path("knowledge_independent").asBoolean(false);
        boolean pass = principalDistinct && adminDistinct && implementationDistinct && oracleDistinct
                && discoveryDistinct && knowledgeDistinct;
        Map<String, Object> out = base("INDEPENDENCE_PROFILE", request.path("target_id").asText("UNKNOWN_TARGET"));
        out.put("principal_independence", principalDistinct);
        out.put("credential_admin_independence", adminDistinct);
        out.put("implementation_independence", implementationDistinct);
        out.put("oracle_independence", oracleDistinct);
        out.put("discovery_independence", discoveryDistinct);
        out.put("knowledge_independence", knowledgeDistinct);
        out.put("independent", pass);
        out.put("decision", pass ? "NON_FINAL" : "HOLD");
        return Map.copyOf(out);
    }

    private Map<String, Object> freshness(JsonNode request, String state) {
        Map<String, Object> out = base("FRESHNESS_EVENT", request.path("target_id").asText("UNKNOWN_TARGET"));
        out.put("state", state);
        out.put("trigger", request.path("trigger").asText("UNSPECIFIED"));
        out.put("affected_receipts", stringList(request.path("affected_receipts")));
        out.put("freshness_epoch", request.path("freshness_epoch").asText("UNASSIGNED"));
        out.put("decision", "HOLD");
        return Map.copyOf(out);
    }

    private Map<String, Object> requalify(JsonNode request) {
        int denominator = request.path("critical_denominator").asInt(-1);
        int misses = request.path("critical_miss_count").asInt(-1);
        boolean isolated = request.path("isolated_execution_proven").asBoolean(false);
        boolean benchmarkPrecommitted = request.path("benchmark_precommitted").asBoolean(false);
        boolean pass = denominator > 0 && misses == 0 && isolated && benchmarkPrecommitted;
        Map<String, Object> out = base("VALIDATOR_REQUALIFICATION", request.path("target_id").asText("VALIDATOR"));
        out.put("critical_denominator", denominator);
        out.put("critical_miss_count", misses);
        out.put("isolated_execution_proven", isolated);
        out.put("benchmark_precommitted", benchmarkPrecommitted);
        out.put("qualification_state", pass ? "QUALIFIED_NONFINAL" : "FAILED");
        out.put("decision", pass ? "NON_FINAL" : "HOLD");
        return Map.copyOf(out);
    }

    private Map<String, Object> independentAccept(JsonNode request, String lane) {
        boolean independent = request.path("independent").asBoolean(false);
        boolean qualified = "QUALIFIED".equals(request.path("qualification_state").asText());
        String receipt = request.path("receipt_sha256").asText("");
        boolean signed = request.path("signature_verified").asBoolean(false);
        boolean current = "CURRENT".equals(request.path("freshness_state").asText());
        boolean pass = independent && qualified && signed && current && receipt.matches("[0-9a-f]{64}");
        Map<String, Object> out = base("INDEPENDENT_" + lane + "_ACCEPTANCE", request.path("target_id").asText("UNKNOWN_TARGET"));
        out.put("lane", lane);
        out.put("accepted_receipt_sha256", receipt);
        out.put("independent", independent);
        out.put("qualification_state", request.path("qualification_state").asText("NOT_QUALIFIED"));
        out.put("freshness_state", request.path("freshness_state").asText("STATUS_UNKNOWN"));
        out.put("signature_verified", signed);
        out.put("decision", pass ? "NON_FINAL" : "HOLD");
        return Map.copyOf(out);
    }

    private Map<String, Object> humanAccept(JsonNode request) {
        String profile = request.path("principal_profile_sha256").asText("");
        String receipt = request.path("acceptance_receipt_sha256").asText("");
        boolean explicit = request.path("explicit_acceptance").asBoolean(false);
        boolean current = "CURRENT".equals(request.path("freshness_state").asText());
        boolean pass = explicit && current && profile.matches("[0-9a-f]{64}") && receipt.matches("[0-9a-f]{64}");
        Map<String, Object> out = base("HUMAN_ACCEPTANCE", request.path("target_id").asText("UNKNOWN_TARGET"));
        out.put("principal_profile_sha256", profile);
        out.put("acceptance_receipt_sha256", receipt);
        out.put("explicit_acceptance", explicit);
        out.put("decision", pass ? "NON_FINAL" : "HOLD");
        return Map.copyOf(out);
    }

    private Map<String, Object> finalCandidate(JsonNode request) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        if (request.path("evidence").isArray()) {
            for (JsonNode row : request.path("evidence")) evidence.add(mapper.convertValue(row, Map.class));
        }
        Map<String, String> epochs = new LinkedHashMap<>();
        for (String key : List.of("scope", "requirement", "denominator", "policy", "oracle", "validator_qualification", "authority")) {
            epochs.put(key, request.path("epochs").path(key).asText(""));
        }
        return reconstructor.reconstructFinalCandidate(
                requiredText(request, "target_id"),
                requiredDigest(request, "source_tree_sha256"),
                requiredDigest(request, "artifact_digest"),
                evidence,
                epochs,
                request.path("otester_receipt_sha256").asText(null),
                request.path("oaudit_receipt_sha256").asText(null),
                request.path("human_acceptance_receipt_sha256").asText(null),
                request.path("open_p0").asInt(0),
                request.path("open_p1").asInt(0));
    }

    private Map<String, Object> verifyInstalled(JsonNode request) throws Exception {
        Path artifact = requiredInputPath(request, "verified_artifact_path");
        Path deployed = requiredInputPath(request, "deployed_artifact_path");
        String verified = Hashing.file(artifact);
        String installed = Hashing.file(deployed);
        boolean same = verified.equals(installed);
        Map<String, Object> out = base("VERIFIED_TO_DEPLOYED", request.path("target_id").asText("UNKNOWN_TARGET"));
        out.put("verified_artifact_sha256", verified);
        out.put("deployed_artifact_sha256", installed);
        out.put("identity_equal", same);
        out.put("decision", same ? "NON_FINAL" : "FAIL");
        return Map.copyOf(out);
    }

    private Map<String, Object> externalEffectNotImplemented(String operation, JsonNode request) {
        return failClosed("BLOCKED", List.of("EXTERNAL_EFFECT_RUNTIME_NOT_WIRED:" + operation));
    }

    private Map<String, Object> envelope(String operation, Map<String, Object> result) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contract", CONTRACT);
        envelope.put("operation", operation);
        envelope.put("authenticated_actor", identity.actorId());
        envelope.put("authenticated_tenant", identity.tenantId());
        envelope.put("result", result);
        envelope.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        envelope.put("independent_authority", false);
        envelope.put("final_claim_allowed", false);
        envelope.put("created_at", Instant.now().toString());
        envelope.put("envelope_sha256", digest(envelope));
        return Map.copyOf(envelope);
    }

    private Map<String, Object> base(String artifactType, String targetId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("artifact_type", artifactType);
        out.put("target_id", targetId);
        out.put("tenant_id", identity.tenantId());
        out.put("actor_id", identity.actorId());
        out.put("created_at", Instant.now().toString());
        out.put("final_claim_allowed", false);
        return out;
    }

    private Map<String, Object> failClosed(String decision, List<String> reasons) {
        Map<String, Object> out = base("FAIL_CLOSED_RESULT", "UNKNOWN_TARGET");
        out.put("decision", decision);
        out.put("reasons", reasons);
        return Map.copyOf(out);
    }

    private Path requiredInputPath(JsonNode request, String field) throws Exception {
        String text = requiredText(request, field);
        Path path = workspaceRoot.resolve(text).normalize();
        if (!path.startsWith(workspaceRoot) || !Files.isRegularFile(path)) {
            throw new IllegalArgumentException("SEMANTIC_V2_INPUT_PATH_INVALID:" + field);
        }
        return path;
    }

    private String requiredText(JsonNode request, String field) {
        String value = request.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("SEMANTIC_V2_FIELD_REQUIRED:" + field);
        return value;
    }

    private String requiredDigest(JsonNode request, String field) {
        String value = requiredText(request, field);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SEMANTIC_V2_DIGEST_INVALID:" + field);
        return value;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        ArrayList<String> values = new ArrayList<>();
        for (JsonNode item : node) if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText());
        return List.copyOf(values);
    }

    private String digest(Object value) {
        try {
            return Hashing.sha256(mapper.writeValueAsBytes(new TreeMap<>(mapper.convertValue(value, Map.class))));
        } catch (Exception e) {
            throw new IllegalStateException("SEMANTIC_V2_DIGEST_FAILED", e);
        }
    }
}
