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

        ObjectNode routed = ((ObjectNode) request).deepCopy();
        routed.put("_authorized_target_root", authorizedTargetRoot.toString());
        routed.put("_authorized_target_id", registered.target().targetId());
        routed.put("_authorized_project_id", registered.projectId());

        String pathBinding = "SERVER_RESOLVED_REGISTERED_TARGET_ROOT";
        if ("deployment.verify-installed".equals(operation)) {
            Path activeInstallationPath = resolveActiveDeploymentPath(operation, request, registered);
            if (activeInstallationPath == null) {
                return blocked(operation, "TARGET_BOUND_DEPLOYMENT_IDENTITY_NOT_AVAILABLE");
            }
            routed.put("_authorized_deployment_root", activeInstallationPath.toString());
            pathBinding = "SERVER_RESOLVED_DEPLOYMENT_IDENTITY";
        }

        Map<String, Object> value = semantic.dispatch(operation, routed);
        return envelope(operation, value, "TENANT_RBAC_SEMANTIC_OPERATION_TRANSACTION", pathBinding);
    }

    /**
     * Resolves the currently active installed version's directory for a registered deployment
     * binding (deployment.register-target), the real target-bound deployment identity that
     * verifyInstalled() digest-compares deployed_artifact_path against. Returns null (never
     * throws) whenever the identity genuinely is not available yet -- unregistered deployment
     * binding or zero installed versions -- so the caller fails closed with BLOCKED instead of a
     * 500, matching every other "identity not yet available" path in this bridge.
     */
    private Path resolveActiveDeploymentPath(
            String operation, JsonNode request, ProductCatalog.RegisteredTarget registered) throws Exception {
        String deploymentTargetId = request.path("deployment_target_id").asText("");
        if (deploymentTargetId.isBlank()) return null;
        ProductCatalog.RegisteredDeployment deployment;
        try {
            deployment = new ProductCatalog(workspaceRoot.resolve(".onsure/product-catalog"))
                    .requireDeployment(registered.projectId(), registered.target().targetId(), deploymentTargetId);
        } catch (IllegalArgumentException notFound) {
            return null;
        }
        try {
            return new DeploymentInstallationService(deployment.deploymentRoot()).activeInstallationPath();
        } catch (IllegalStateException noVersionInstalled) {
            return null;
        }
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
                    "semantic.reperformance.run", "assurance.evidence-graph.validate",
                    "assurance.composition.compute", "assurance.sod.record-stage",
                    "assurance.sod.check", "assurance.four-eyes.record-approval",
                    "assurance.four-eyes.check", "assurance.plugin.qualify",
                    "assurance.external-integration.reconcile",
                    "assurance.learning.candidate.register", "assurance.learning.validation.request",
                    "assurance.learning.completion-status.check",
                    "assurance.oracle.qualification-check", "assurance.oracle.multi-evaluate",
                    "assurance.corpus.integrity-check", "assurance.validator.regression-qualify",
                    "assurance.learning.stop-decision.compute",
                    "assurance.learning.decision-currentness.evaluate",
                    "assurance.learning.evidence-observation.record", "assurance.release.qualify",
                    "assurance.validation.snapshot-verify", "assurance.validation.experiment-evaluate",
                    "assurance.learning.effectiveness.evaluate", "assurance.strength-ceiling.compute",
                    "assurance.learning.data-residency.check", "assurance.learning.revocation-propagation.check",
                    "assurance.decision.propagation-check",
                    "assurance.learning.federated-aggregation-governance.check",
                    "assurance.learning.cross-tenant-transfer.validate",
                    "assurance.learning.activation-stage.transition",
                    "assurance.learning.statistical-qualification.check",
                    "assurance.learning.explanation-fidelity.check",
                    "assurance.learning.selective-prediction-risk-coverage.check",
                    "assurance.learning.history-migration.check",
                    "assurance.learning.ip-license-provenance.check",
                    "assurance.learning.catastrophic-forgetting.check",
                    "assurance.learning.sampling-bias.check",
                    "assurance.learning.confidence-calibration.check",
                    "assurance.learning.adversarial-benchmark-governance.check",
                    "assurance.learning.knowledge-fork-merge-governance.check",
                    "assurance.learning.external-llm-provenance-boundary.check",
                    "assurance.final-lock.approval-cross-contract.check",
                    "assurance.ai-product.currentness-compose",
                    "assurance.judge.independence-check", "assurance.reviewer-pool.independence-check",
                    "assurance.requalification.trigger-evaluate",
                    "assurance.agent-memory.conflict-resolve",
                    "assurance.tool-call.authorization-check",
                    "assurance.prompt.provenance-check",
                    "assurance.ai-safety.claim-independence-check",
                    "assurance.delegation.chain-check",
                    "assurance.rag.retrieval-assurance-check",
                    "assurance.provider.drift-check",
                    "assurance.multi-agent.corroboration-check",
                    "assurance.hazard.create", "assurance.appeal.file",
                    "assurance.appeal.submit-evidence", "assurance.engagement.check-scope",
                    "assurance.accessibility.validate-render", "assurance.migration.reconcile",
                    "assurance.session.create", "assurance.session.check-valid",
                    "assurance.learning.human-override.trend-report",
                    "assurance.learning.revalidation.complete",
                    "assurance.learning.revalidation.backlog-status" -> auditor || operator || admin;
            case "assurance.hazard.advance", "assurance.appeal.assign-reviewer",
                    "assurance.appeal.transition", "assurance.appeal.decide",
                    "assurance.offboarding.request", "assurance.offboarding.advance",
                    "assurance.migration.cutover", "assurance.migration.rollback" -> auditor || admin;
            case "semantic.authority.revalidate", "semantic.independence.assess",
                    "semantic.freshness.invalidate", "semantic.freshness.reconstruct",
                    "semantic.validator.requalify", "assurance.final-candidate.reconstruct",
                    "deployment.verify-installed", "assurance.certificate.issue",
                    "assurance.revocation.issue", "assurance.revocation.check",
                    "assurance.offline-trust-bundle.evaluate", "assurance.delegation.grant",
                    "assurance.delegation.check", "assurance.break-glass.invoke",
                    "assurance.break-glass.review", "assurance.learning.validation.pack.issue",
                    "assurance.learning.validation.receipt.record", "assurance.learning.promotion.approve",
                    "assurance.learning.applied-lock.record", "assurance.learning.scope-promotion.decide",
                    "assurance.learning.derived-lineage.dispose",
                    "assurance.learning.human-override.decide",
                    "assurance.learning.counterevidence.dispose",
                    "assurance.learning.challenge-set-access.decide",
                    "assurance.learning.ground-truth.declare-epoch" -> auditor || admin;
            case "assurance.human-accept" -> approver || admin;
            case "assurance.otester.accept", "assurance.oaudit.accept" -> auditor;
            case "git.push" -> operator || admin;
            default -> false;
        };
        if (!allowed) throw new SecurityException("SEMANTIC_V2_OPERATION_ROLE_DENIED:" + operation);
    }
}
