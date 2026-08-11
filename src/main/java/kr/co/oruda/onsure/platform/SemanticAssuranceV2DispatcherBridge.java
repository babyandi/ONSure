package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;

/**
 * Dual-read dispatcher bridge. Existing v1 operations remain on LocalWorkflowDispatcher while
 * Semantic Assurance v2 candidate operations are routed to the isolated v2 service. This bridge
 * creates no Final authority and is intentionally not installed as the default dispatcher yet.
 */
public final class SemanticAssuranceV2DispatcherBridge {
    public static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_DISPATCHER_BRIDGE_V1";
    private final LocalWorkflowDispatcher legacy;
    private final SemanticAssuranceV2WorkflowService semantic;
    private final AuthenticatedWorkflowIdentity identity;

    public SemanticAssuranceV2DispatcherBridge(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        this.identity = identity;
        this.legacy = new LocalWorkflowDispatcher(workspaceRoot, identity);
        this.semantic = new SemanticAssuranceV2WorkflowService(workspaceRoot, identity);
    }

    public Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (!SemanticAssuranceV2WorkflowService.supports(operation)) {
            return legacy.dispatch(operation, request);
        }
        requireSemanticRole(operation);
        Map<String, Object> value = semantic.dispatch(operation, request);
        return Map.of(
                "contract", CONTRACT,
                "route", "SEMANTIC_V2_CANDIDATE",
                "operation", operation,
                "result", value,
                "assurance_class", "SELF_VALIDATION_NONFINAL",
                "active_authority", false,
                "final_claim_allowed", false);
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

        // Role permission is only an application boundary. It is not sufficient proof of
        // independent OTester/OAudit or Human Final Authority; those operations still require
        // principal/independence/qualification receipts inside their request.
    }
}
