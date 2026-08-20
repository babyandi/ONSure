package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Path;
import java.util.Map;

/** Composite workflow boundary for the post-final-target candidate denominator. */
public final class PostFinalTargetWorkflowDispatcher {
    public static final String CONTRACT = "ONSURE_POST_FINAL_TARGET_WORKFLOW_DISPATCHER_V2";
    private final Path workspaceRoot;
    private final AuthenticatedWorkflowIdentity identity;
    private final SemanticAssuranceV2DispatcherBridge existing;
    private final DdAssuranceOperationRuntime dd;

    public PostFinalTargetWorkflowDispatcher(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        if (workspaceRoot == null || identity == null) throw new IllegalArgumentException("POST_FINAL_TARGET_DISPATCH_CONTEXT_REQUIRED");
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.identity = identity;
        this.existing = new SemanticAssuranceV2DispatcherBridge(this.workspaceRoot, identity);
        this.dd = DdQualifiedRuntimeFactory.loadOrUnqualified(this.workspaceRoot);
    }

    public Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (!dd.supports(operation)) return existing.dispatch(operation, request);
        if (request == null || !request.isObject()) throw new IllegalArgumentException("DD_REQUEST_OBJECT_REQUIRED");
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
        String decision = String.valueOf(result.getOrDefault("decision", "HOLD"));
        String completion = "PASS_NONFINAL".equals(decision) ? "PASS_NONFINAL_SELF_VALIDATION_ONLY" : "NONPOSITIVE";
        return Map.ofEntries(
                Map.entry("contract", CONTRACT),
                Map.entry("route", "POST_FINAL_TARGET_DD_QUALIFICATION_AWARE_FAIL_CLOSED"),
                Map.entry("operation", operation),
                Map.entry("result", result),
                Map.entry("authenticated_actor", identity.actorId()),
                Map.entry("authenticated_tenant", identity.tenantId()),
                Map.entry("assurance_class", "SELF_VALIDATION_NONFINAL"),
                Map.entry("semantic_completion", completion),
                Map.entry("independent_otester", "NOT_RUN"),
                Map.entry("independent_oaudit", "NOT_RUN"),
                Map.entry("final_claim_allowed", false));
    }
}
