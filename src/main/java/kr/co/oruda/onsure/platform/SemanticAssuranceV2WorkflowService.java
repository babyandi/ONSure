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

/**
 * Package-local candidate runtime boundary for Semantic Assurance v2 operations.
 *
 * <p>The service is intentionally not a public product surface. Calls must arrive through a
 * server-bound bridge that injects target authority context after durable tenant/resource
 * authorization. Strong independence, human acceptance and qualification claims fail closed until
 * their cryptographic/runtime verifiers are wired.</p>
 */
final class SemanticAssuranceV2WorkflowService {
    static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_WORKFLOW_SERVICE_V2";
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
    private static final Set<String> CAPABILITIES = Set.of(
            "SA-01","SA-02","SA-03","SA-04","SA-05","SA-06","SA-07",
            "SA-08","SA-09","SA-10","SA-11","SA-12","SA-13","SA-14");

    private final Path workspaceRoot;
    private final AuthenticatedWorkflowIdentity identity;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final SemanticAssuranceV2Reconstructor reconstructor = new SemanticAssuranceV2Reconstructor();

    SemanticAssuranceV2WorkflowService(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        if (workspaceRoot == null || identity == null) throw new IllegalArgumentException("V2_WORKFLOW_CONTEXT_REQUIRED");
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.identity = identity;
    }

    static boolean supports(String operation) {
        return OPERATIONS.contains(operation);
    }

    Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (!supports(operation)) throw new IllegalArgumentException("SEMANTIC_V2_OPERATION_UNSUPPORTED:" + operation);
        if (request == null || !request.isObject()) throw new IllegalArgumentException("SEMANTIC_V2_REQUEST_OBJECT_REQUIRED");
        requireServerBoundContext(request);
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
            case "git.push" -> externalEffectNotImplemented(operation);
            case "deployment.verify-installed" -> verifyInstalled(request);
            default -> throw new IllegalStateException("SEMANTIC_V2_OPERATION_SWITCH_GAP:" + operation);
        };
        return envelope(operation, result);
    }

    private Map<String, Object> applicability(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        JsonNode capabilities = request.path("capabilities");
        if (!capabilities.isArray()) return failClosed("INPUT_REQUIRED", List.of("CAPABILITY_SET_REQUIRED"));
        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (JsonNode item : capabilities) {
            String id = requiredText(item, "capability_id");
            if (!CAPABILITIES.contains(id)) return failClosed("HOLD", List.of("UNKNOWN_CAPABILITY:" + id));
            if (!ids.add(id)) return failClosed("HOLD", List.of("DUPLICATE_CAPABILITY:" + id));
            String disposition = item.path("disposition").asText("INPUT_REQUIRED");
            String rationale = item.path("rationale").asText("");
            if ("NOT_APPLICABLE_JUSTIFIED".equals(disposition) && rationale.isBlank()) disposition = "INPUT_REQUIRED";
            if (!Set.of("APPLICABLE", "NOT_APPLICABLE_JUSTIFIED", "INPUT_REQUIRED", "HOLD").contains(disposition)) {
                return failClosed("HOLD", List.of("CAPABILITY_DISPOSITION_INVALID:" + id));
            }
            rows.add(Map.of("capability_id", id, "disposition", disposition, "rationale", rationale));
        }
        if (!ids.equals(CAPABILITIES)) {
            java.util.HashSet<String> missing = new java.util.HashSet<>(CAPABILITIES);
            missing.removeAll(ids);
            return failClosed("HOLD", List.of("CAPABILITY_DENOMINATOR_INCOMPLETE:" + String.join(",", missing)));
        }
        Map<String, Object> out = base("SEMANTIC_APPLICABILITY_SET", targetId);
        out.put("items", List.copyOf(rows));
        out.put("population_digest", digest(rows));
        out.put("decision", rows.stream().anyMatch(row -> Set.of("INPUT_REQUIRED", "HOLD").contains(row.get("disposition")))
                ? "HOLD" : "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> denominator(JsonNode request, String mode) {
        String targetId = requiredText(request, "target_id");
        JsonNode items = request.path("items");
        if (!items.isArray() || items.isEmpty()) return failClosed("INPUT_REQUIRED", List.of("DENOMINATOR_ITEMS_REQUIRED"));
        List<Map<String, Object>> normalized = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (JsonNode item : items) {
            String id = requiredText(item, "item_id");
            if (!ids.add(id)) return failClosed("HOLD", List.of("DUPLICATE_DENOMINATOR_ID:" + id));
            String sha = requiredDigest(item, "item_sha256");
            String disposition = item.path("disposition").asText("INCLUDED");
            if (!Set.of("INCLUDED", "NOT_APPLICABLE_JUSTIFIED", "EXCLUDED_WITH_AUTHORITY", "SUPERSEDED_LEGACY").contains(disposition)) {
                return failClosed("HOLD", List.of("DENOMINATOR_DISPOSITION_INVALID:" + id));
            }
            if (("NOT_APPLICABLE_JUSTIFIED".equals(disposition) || "EXCLUDED_WITH_AUTHORITY".equals(disposition))
                    && !item.path("disposition_receipt_sha256").asText("").matches("[0-9a-f]{64}")) {
                return failClosed("HOLD", List.of("DENOMINATOR_DISPOSITION_EVIDENCE_REQUIRED:" + id));
            }
            normalized.add(Map.of("item_id", id, "item_sha256", sha, "disposition", disposition));
        }
        Map<String, Object> out = base("DENOMINATOR_" + mode, targetId);
        out.put("mode", mode);
        out.put("item_count", normalized.size());
        out.put("items", List.copyOf(normalized));
        out.put("population_digest", digest(normalized));
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> denominatorLock(JsonNode request) {
        Map<String, Object> out = denominator(request, "LOCK");
        if (!"NON_FINAL".equals(out.get("decision"))) return out;
        String epoch = request.path("epoch").asText("");
        if (epoch.isBlank()) return failClosed("INPUT_REQUIRED", List.of("DENOMINATOR_EPOCH_REQUIRED"));
        Map<String, Object> mutable = new LinkedHashMap<>(out);
        mutable.put("epoch", epoch);
        mutable.put("locked_at", Instant.now().toString());
        mutable.put("lock_is_final_authority", false);
        return immutable(mutable);
    }

    private Map<String, Object> reperformance(JsonNode request) throws Exception {
        Path subject = requiredPathWithin(request, "subject_path", "_authorized_target_root");
        String expected = requiredDigest(request, "subject_sha256");
        String actual = Hashing.file(subject);
        boolean same = expected.equals(actual);
        Map<String, Object> out = base("REPERFORMANCE_RESULT", requiredText(request, "target_id"));
        out.put("subject_path", Hashing.relative(workspaceRoot, subject));
        out.put("expected_sha256", expected);
        out.put("actual_sha256", actual);
        out.put("readback_equal", same);
        out.put("oracle_state", request.path("oracle_state").asText("NOT_RUN"));
        out.put("decision", same && "PASS".equals(request.path("oracle_state").asText()) ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    private Map<String, Object> authorityRevalidate(JsonNode request) {
        List<String> missing = new ArrayList<>();
        for (String field : List.of("principal_profile_sha256", "authority_epoch", "purpose", "effect_at")) {
            if (request.path(field).asText("").isBlank()) missing.add(field);
        }
        if (!request.path("authority_readback_receipt_sha256").asText("").matches("[0-9a-f]{64}")) {
            missing.add("AUTHORITY_READBACK_RECEIPT_REQUIRED");
        }
        Map<String, Object> out = base("AUTHORITY_REVALIDATION", requiredText(request, "target_id"));
        out.put("principal_profile_sha256", request.path("principal_profile_sha256").asText(""));
        out.put("authority_epoch", request.path("authority_epoch").asText(""));
        out.put("valid_at_effect", false);
        out.put("reasons", List.copyOf(missing));
        out.put("decision", "HOLD");
        out.put("limitation", "AUTHORITY_EFFECT_TIME_VERIFIER_NOT_WIRED");
        return immutable(out);
    }

    private Map<String, Object> independenceAssess(JsonNode request) {
        Map<String, Object> out = base("INDEPENDENCE_ASSESSMENT", requiredText(request, "target_id"));
        out.put("independent", false);
        out.put("decision", "HOLD");
        out.put("limitation", "INDEPENDENCE_PROFILE_CRYPTOGRAPHIC_VERIFIER_NOT_WIRED");
        out.put("self_attested_fields_ignored", true);
        return immutable(out);
    }

    private Map<String, Object> freshness(JsonNode request, String state) {
        Map<String, Object> out = base("FRESHNESS_EVENT", requiredText(request, "target_id"));
        out.put("state", state);
        out.put("trigger", request.path("trigger").asText("UNSPECIFIED"));
        out.put("affected_receipts", stringList(request.path("affected_receipts")));
        out.put("freshness_epoch", request.path("freshness_epoch").asText("UNASSIGNED"));
        out.put("decision", "HOLD");
        out.put("persistent_invalidation_applied", false);
        return immutable(out);
    }

    private Map<String, Object> requalify(JsonNode request) {
        Map<String, Object> out = base("VALIDATOR_REQUALIFICATION", requiredText(request, "target_id"));
        out.put("qualification_state", "NOT_QUALIFIED");
        out.put("decision", "HOLD");
        out.put("limitation", "QUALIFICATION_EXECUTION_AND_INDEPENDENT_REPERFORMANCE_NOT_WIRED");
        out.put("self_attested_metrics_ignored", true);
        return immutable(out);
    }

    private Map<String, Object> independentAccept(JsonNode request, String lane) {
        Map<String, Object> out = base("INDEPENDENT_" + lane + "_ACCEPTANCE", requiredText(request, "target_id"));
        out.put("lane", lane);
        out.put("decision", "HOLD");
        out.put("accepted", false);
        out.put("limitation", "INDEPENDENT_RECEIPT_SIGNATURE_PROFILE_AND_QUALIFICATION_VERIFIER_NOT_WIRED");
        out.put("caller_declared_independent_ignored", true);
        return immutable(out);
    }

    private Map<String, Object> humanAccept(JsonNode request) {
        Map<String, Object> out = base("HUMAN_ACCEPTANCE", requiredText(request, "target_id"));
        out.put("decision", "HOLD");
        out.put("accepted", false);
        out.put("limitation", "SIGNED_HUMAN_ACCEPTANCE_AUTHORITY_VERIFIER_NOT_WIRED");
        out.put("caller_declared_acceptance_ignored", true);
        return immutable(out);
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
        if (request.path("_authorized_deployment_root").asText("").isBlank()) {
            return failClosed("BLOCKED", List.of("TARGET_BOUND_DEPLOYMENT_IDENTITY_NOT_AVAILABLE"));
        }
        Path artifact = requiredPathWithin(request, "verified_artifact_path", "_authorized_target_root");
        Path deployed = requiredPathWithin(request, "deployed_artifact_path", "_authorized_deployment_root");
        String verified = Hashing.file(artifact);
        String installed = Hashing.file(deployed);
        boolean same = verified.equals(installed);
        Map<String, Object> out = base("VERIFIED_TO_DEPLOYED", requiredText(request, "target_id"));
        out.put("verified_artifact_sha256", verified);
        out.put("deployed_artifact_sha256", installed);
        out.put("identity_equal", same);
        out.put("decision", same ? "NON_FINAL" : "FAIL");
        return immutable(out);
    }

    private Map<String, Object> externalEffectNotImplemented(String operation) {
        return failClosed("BLOCKED", List.of("EXTERNAL_EFFECT_RUNTIME_NOT_WIRED:" + operation));
    }

    private Map<String, Object> envelope(String operation, Map<String, Object> result) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contract", CONTRACT);
        envelope.put("operation", operation);
        envelope.put("authenticated_actor", identity.actorId());
        envelope.put("authenticated_tenant", identity.tenantId());
        envelope.put("result", result);
        envelope.put("server_bound_context", true);
        envelope.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        envelope.put("independent_authority", false);
        envelope.put("final_claim_allowed", false);
        envelope.put("created_at", Instant.now().toString());
        envelope.put("envelope_sha256", digest(envelope));
        return immutable(envelope);
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
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    private void requireServerBoundContext(JsonNode request) {
        String target = requiredText(request, "target_id");
        String project = requiredText(request, "project_id");
        if (!target.equals(request.path("_authorized_target_id").asText(""))) {
            throw new SecurityException("SEMANTIC_V2_AUTHORIZED_TARGET_MISMATCH");
        }
        if (!project.equals(request.path("_authorized_project_id").asText(""))) {
            throw new SecurityException("SEMANTIC_V2_AUTHORIZED_PROJECT_MISMATCH");
        }
        authorizedRoot(request, "_authorized_target_root");
    }

    private Path requiredPathWithin(JsonNode request, String field, String rootField) {
        String text = requiredText(request, field);
        Path root = authorizedRoot(request, rootField);
        Path candidate = workspaceRoot.resolve(text).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            throw new SecurityException("SEMANTIC_V2_PATH_OUTSIDE_AUTHORIZED_ROOT:" + field);
        }
        return candidate;
    }

    private Path authorizedRoot(JsonNode request, String rootField) {
        String value = request.path(rootField).asText("");
        if (value.isBlank()) throw new SecurityException("SEMANTIC_V2_SERVER_BOUND_ROOT_REQUIRED:" + rootField);
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (!root.startsWith(workspaceRoot) || !Files.isDirectory(root)) {
            throw new SecurityException("SEMANTIC_V2_SERVER_BOUND_ROOT_INVALID:" + rootField);
        }
        return root;
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

    private Map<String, Object> immutable(Map<String, Object> value) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private String digest(Object value) {
        try {
            return Hashing.sha256(mapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("SEMANTIC_V2_DIGEST_FAILED", e);
        }
    }
}
