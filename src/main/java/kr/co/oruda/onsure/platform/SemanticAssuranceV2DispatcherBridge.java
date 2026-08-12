package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.util.Map;

/**
 * Dual-read dispatcher bridge. Existing v1 operations remain on LocalWorkflowDispatcher while
 * Semantic Assurance v2 candidate operations are routed to the isolated v2 service.
 *
 * <p>Every semantic target operation first performs the existing authenticated
 * project.read-target path, so tenant/resource ownership and authenticated context substitution
 * checks are not bypassed. This is a preflight, not an atomic authorization transaction; therefore
 * this bridge is still SELF_VALIDATION_NONFINAL and must not become an active effect authority
 * before a canonical semantic operation is added to TenantRbacService itself.</p>
 */
public final class SemanticAssuranceV2DispatcherBridge {
    public static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_DISPATCHER_BRIDGE_V2";
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

        // Reuse the existing authenticated tenant/resource boundary before entering v2 candidate code.
        JsonNode readTargetRequest = mapper.createObjectNode()
                .put("project_id", request.path("project_id").asText())
                .put("target_id", request.path("target_id").asText());
        legacy.dispatch("project.read-target", readTargetRequest);

        Map<String, Object> value = semantic.dispatch(operation, request);
        return Map.of(
                "contract", CONTRACT,
                "route", "SEMANTIC_V2_CANDIDATE",
                "operation", operation,
                "result", value,
                "tenant_resource_preflight", "PROJECT_READ_TARGET_AUTHORIZED",
                "authorization_atomic_with_effect", false,
                "assurance_class", "SELF_VALIDATION_NONFINAL",
                "active_authority", false,
                "final_claim_allowed", false);
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
                    "semantic.reperformance.run", "deployment.verify-installed" -> auditor || operator || admin;
            case "semantic.authority.revalidate", "semantic.independence.assess",
                    "semantic.freshness.invalidate", "semantic.freshness.reconstruct",
                    "semantic.validator.requalify", "assurance.final-candidate.reconstruct" -> auditor || admin;
            case "assurance.human-accept" -> approver || admin;
            case "assurance.otester.accept", "assurance.oaudit.accept" -> auditor;
            case "git.push" -> operator || admin;
            default -> false;
        };
        if (!allowed) throw new SecurityException("SEMANTIC_V2_OPERATION_ROLE_DENIED:" + operation);
    }
}
