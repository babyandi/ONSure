package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.Map;

/**
 * Dual-read dispatcher bridge. Existing v1 operations remain on LocalWorkflowDispatcher while
 * Semantic Assurance v2 candidate operations are routed to the isolated v2 service.
 *
 * <p>Every semantic target operation first performs the existing authenticated
 * project.read-target path, so tenant/resource ownership and authenticated context substitution
 * checks are not bypassed. The registered target source root is then injected by this server-side
 * bridge; callers cannot choose or override the authorized path root. This is still a preflight,
 * not an atomic authorization transaction, so the bridge remains SELF_VALIDATION_NONFINAL.</p>
 */
public final class SemanticAssuranceV2DispatcherBridge {
    public static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_DISPATCHER_BRIDGE_V3";
    private final LocalWorkflowDispatcher legacy;
    private final SemanticAssuranceV2WorkflowService semantic;
    private final AuthenticatedWorkflowIdentity identity;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public SemanticAssuranceV2DispatcherBridge(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        if (workspaceRoot == null || identity == null) {
            throw new IllegalArgumentException("SEMANTIC_V2_BRIDGE_CONTEXT_REQUIRED");
        }
        this.identity = identity;
        this.legacy = new LocalWorkflowDispatcher(workspaceRoot, identity);
        this.semantic = new SemanticAssuranceV2WorkflowService(workspaceRoot, identity);
    }

    public Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (!SemanticAssuranceV2WorkflowService.supports(operation)) {
            return legacy.dispatch(operation, request);
        }
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("SEMANTIC_V2_REQUEST_OBJECT_REQUIRED");
        }
        requireSemanticRole(operation);
        requireTargetContext(request);
        rejectCallerInjectedAuthority(request);

        String projectId = request.path("project_id").asText();
        String targetId = request.path("target_id").asText();
        JsonNode readTargetRequest = mapper.createObjectNode()
                .put("project_id", projectId)
                .put("target_id", targetId);
        Map<String, Object> authorized = legacy.dispatch("project.read-target", readTargetRequest);
        ProductCatalog.RegisteredTarget registered = registeredTarget(authorized, projectId, targetId);

        ObjectNode routed = ((ObjectNode) request).deepCopy();
        routed.put("_authorized_target_root", registered.target().sourceRoot().toString());
        routed.put("_authorized_target_id", registered.target().targetId());
        routed.put("_authorized_project_id", registered.projectId());

        // v1 deployment installation is not target-scoped. Therefore no authorized deployment root
        // is injected yet; deployment.verify-installed must fail closed until a target-bound
        // deployment receipt/installation root exists.
        Map<String, Object> value = semantic.dispatch(operation, routed);
        return Map.of(
                "contract", CONTRACT,
                "route", "SEMANTIC_V2_CANDIDATE",
                "operation", operation,
                "result", value,
                "tenant_resource_preflight", "PROJECT_READ_TARGET_AUTHORIZED",
                "target_path_binding", "SERVER_RESOLVED_REGISTERED_TARGET_ROOT",
                "authorization_atomic_with_effect", false,
                "assurance_class", "SELF_VALIDATION_NONFINAL",
                "active_authority", false,
                "final_claim_allowed", false);
    }

    private ProductCatalog.RegisteredTarget registeredTarget(
            Map<String, Object> envelope, String projectId, String targetId) {
        Object payload = envelope.get("result");
        if (!(payload instanceof Map<?, ?> values)) {
            throw new IllegalStateException("SEMANTIC_V2_TARGET_PREFLIGHT_PAYLOAD_MISSING");
        }
        Object value = values.get("registered_target");
        if (!(value instanceof ProductCatalog.RegisteredTarget registered)) {
            throw new IllegalStateException("SEMANTIC_V2_REGISTERED_TARGET_TYPE_MISSING");
        }
        if (!projectId.equals(registered.projectId())
                || !targetId.equals(registered.target().targetId())) {
            throw new SecurityException("SEMANTIC_V2_REGISTERED_TARGET_IDENTITY_MISMATCH");
        }
        return registered;
    }

    private void rejectCallerInjectedAuthority(JsonNode request) {
        var fields = request.fieldNames();
        while (fields.hasNext()) {
            String field = fields.next();
            if (field.startsWith("_authorized_")) {
                throw new SecurityException("SEMANTIC_V2_SERVER_AUTHORITY_FIELD_SUBSTITUTION:" + field);
            }
        }
    }

    private void requireTargetContext(JsonNode request) {
        if (request.path("project_id").asText("").isBlank()) {
            throw new IllegalArgumentException("SEMANTIC_V2_PROJECT_ID_REQUIRED");
        }
        if (request.path("target_id").asText("").isBlank()) {
            throw new IllegalArgumentException("SEMANTIC_V2_TARGET_ID_REQUIRED");
        }
        JsonNode actor = request.path("actor");
        if (!actor.isMissingNode() && !actor.isNull() && !identity.actorId().equals(actor.asText())) {
            throw new SecurityException("AUTHENTICATED_ACTOR_SUBSTITUTION");
        }
        JsonNode tenant = request.path("tenant_context");
        if (!tenant.isMissingNode() && !tenant.isNull()) {
            throw new SecurityException("SEMANTIC_V2_TENANT_CONTEXT_MUST_BE_SERVER_BOUND");
        }
    }

    private void requireSemanticRole(String operation) {
        boolean auditor = identity.roles().contains(AuthenticatedWorkflowIdentity.Role.AUDITOR);
        boolean admin = identity.roles().contains(AuthenticatedWorkflowIdentity.Role.ADMIN);
        boolean operator = identity.roles().contains(AuthenticatedWorkflowIdentity.Role.OPERATOR);
        boolean approver = identity.roles().contains(AuthenticatedWorkflowIdentity.Role.APPROVER);

        boolean allowed = switch (operation) {
            case "semantic.applicability.evaluate", "semantic.denominator.discover",
                    "semantic.denominator.challenge", "semantic.denominator.lock",
                    "semantic.reperformance.run" -> auditor || operator || admin;
            case "semantic.authority.revalidate", "semantic.independence.assess",
                    "semantic.freshness.invalidate", "semantic.freshness.reconstruct",
                    "semantic.validator.requalify", "assurance.final-candidate.reconstruct",
                    "deployment.verify-installed" -> auditor || admin;
            case "assurance.human-accept" -> approver || admin;
            case "assurance.otester.accept", "assurance.oaudit.accept" -> auditor;
            case "git.push" -> operator || admin;
            default -> false;
        };
        if (!allowed) throw new SecurityException("SEMANTIC_V2_OPERATION_ROLE_DENIED:" + operation);
    }
}
