package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/**
 * Dual-read dispatcher bridge. Existing v1 operations remain on LocalWorkflowDispatcher while
 * Semantic Assurance v2 candidate operations are routed to the isolated v2 service.
 *
 * <p>Semantic operations execute inside the existing TenantRbacService durable ownership
 * transaction under their real semantic operation name. RegisteredTarget.sourceRoot is resolved
 * server-side and caller-injected authority fields are rejected.</p>
 *
 * <p>This is still a candidate bridge: it has not been compile/JUnit/independently verified and is
 * not installed as the product's active contract selector.</p>
 */
public final class SemanticAssuranceV2DispatcherBridge {
    public static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_DISPATCHER_BRIDGE_V6";
    private final Path workspaceRoot;
    private final LocalWorkflowDispatcher legacy;
    private final SemanticAssuranceV2WorkflowService semantic;
    private final AuthenticatedWorkflowIdentity identity;

    public SemanticAssuranceV2DispatcherBridge(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        if (workspaceRoot == null || identity == null) {
            throw new IllegalArgumentException("SEMANTIC_V2_BRIDGE_CONTEXT_REQUIRED");
        }
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.identity = identity;
        this.legacy = new LocalWorkflowDispatcher(this.workspaceRoot, identity);
        this.semantic = new SemanticAssuranceV2WorkflowService(this.workspaceRoot, identity);
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

        return new TenantRbacService(workspaceRoot).execute(
                identity,
                operation,
                request,
                () -> executeAuthorizedSemantic(operation, request));
    }

    private Map<String, Object> executeAuthorizedSemantic(String operation, JsonNode request) throws Exception {
        String projectId = request.path("project_id").asText();
        String targetId = request.path("target_id").asText();
        ProductCatalog.RegisteredTarget registered = registeredTarget(projectId, targetId);
        Path authorizedTargetRoot = registered.target().sourceRoot().toAbsolutePath().normalize();

        if ("semantic.reperformance.run".equals(operation)) {
            requirePathWithinTarget(request, "subject_path", authorizedTargetRoot);
        }
        if ("deployment.verify-installed".equals(operation)) {
            return blocked(operation, "TARGET_BOUND_DEPLOYMENT_IDENTITY_NOT_AVAILABLE");
        }

        ObjectNode routed = ((ObjectNode) request).deepCopy();
        routed.put("_authorized_target_root", authorizedTargetRoot.toString());
        routed.put("_authorized_target_id", registered.target().targetId());
        routed.put("_authorized_project_id", registered.projectId());

        Map<String, Object> value = semantic.dispatch(operation, routed);
        return envelope(operation, value, "TENANT_RBAC_SEMANTIC_OPERATION_TRANSACTION", "SERVER_RESOLVED_REGISTERED_TARGET_ROOT");
    }

    private ProductCatalog.RegisteredTarget registeredTarget(String projectId, String targetId) throws Exception {
        ProductCatalog catalog = new ProductCatalog(workspaceRoot.resolve(".onsure/product-catalog"));
        return catalog.targets(projectId).stream()
                .filter(value -> targetId.equals(value.target().targetId()))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("SEMANTIC_V2_REGISTERED_TARGET_NOT_FOUND"));
    }

    private Map<String, Object> blocked(String operation, String reason) {
        Map<String, Object> result = Map.of(
                "artifact_type", "FAIL_CLOSED_RESULT",
                "decision", "BLOCKED",
                "reasons", List.of(reason),
                "final_claim_allowed", false);
        return envelope(operation, result, "TENANT_RBAC_SEMANTIC_OPERATION_TRANSACTION", "DEPLOYMENT_TARGET_BINDING_UNAVAILABLE");
    }

    private Map<String, Object> envelope(
            String operation, Map<String, Object> value, String authorization, String pathBinding) {
        return Map.of(
                "contract", CONTRACT,
                "route", "SEMANTIC_V2_CANDIDATE",
                "operation", operation,
                "result", value,
                "tenant_resource_authorization", authorization,
                "target_path_binding", pathBinding,
                "authorization_atomic_with_semantic_call", true,
                "assurance_class", "SELF_VALIDATION_NONFINAL",
                "active_authority", false,
                "final_claim_allowed", false);
    }

    private void requirePathWithinTarget(JsonNode request, String field, Path authorizedTargetRoot) {
        String text = request.path(field).asText("");
        if (text.isBlank()) throw new IllegalArgumentException("SEMANTIC_V2_FIELD_REQUIRED:" + field);
        Path candidate = workspaceRoot.resolve(text).normalize();
        if (!candidate.startsWith(authorizedTargetRoot)) {
            throw new SecurityException("SEMANTIC_V2_TARGET_PATH_ESCAPE:" + field);
        }
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
