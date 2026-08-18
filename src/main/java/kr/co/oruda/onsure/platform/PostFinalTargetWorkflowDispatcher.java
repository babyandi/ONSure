package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;

/**
 * Composite workflow boundary for the post-final-target candidate denominator.
 *
 * <p>DD operations are routed through the real TenantRbacService and a fail-closed DD runtime.
 * Existing semantic-v2 and legacy operations remain delegated to the established bridge.</p>
 */
public final class PostFinalTargetWorkflowDispatcher {
    public static final String CONTRACT = "ONSURE_POST_FINAL_TARGET_WORKFLOW_DISPATCHER_V1";
    private final Path workspaceRoot;
    private final AuthenticatedWorkflowIdentity identity;
    private final SemanticAssuranceV2DispatcherBridge existing;
    private final DdAssuranceOperationRuntime dd = new DdAssuranceOperationRuntime();

    public PostFinalTargetWorkflowDispatcher(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        if (workspaceRoot == null || identity == null) {
            throw new IllegalArgumentException("POST_FINAL_TARGET_DISPATCH_CONTEXT_REQUIRED");
        }
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.identity = identity;
        this.existing = new SemanticAssuranceV2DispatcherBridge(this.workspaceRoot, identity);
    }

    public Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (!dd.supports(operation)) return existing.dispatch(operation, request);
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("DD_REQUEST_OBJECT_REQUIRED");
        }
        requireAuditorAuthority();
        return new TenantRbacService(workspaceRoot).execute(
                identity, operation, request, () -> envelope(operation, dd.execute(operation, request)));
    }

    private void requireAuditorAuthority() {
        boolean allowed = identity.roles().contains(AuthenticatedWorkflowIdentity.Role.AUDITOR)
                || identity.roles().contains(AuthenticatedWorkflowIdentity.Role.ADMIN);
        if (!allowed) throw new SecurityException("DD_OPERATION_AUDITOR_AUTHORITY_REQUIRED");
    }

    private Map<String, Object> envelope(String operation, Map<String, Object> result) {
        return Map.of(
                "contract", CONTRACT,
                "route", "POST_FINAL_TARGET_DD_FAIL_CLOSED",
                "operation", operation,
                "result", result,
                "authenticated_actor", identity.actorId(),
                "authenticated_tenant", identity.tenantId(),
                "assurance_class", "SELF_VALIDATION_NONFINAL",
                "semantic_completion", "NOT_QUALIFIED",
                "independent_otester", "NOT_RUN",
                "independent_oaudit", "NOT_RUN",
                "final_claim_allowed", false);
    }
}
