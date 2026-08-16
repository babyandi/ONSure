package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/** Authenticated RBAC and durable resource-to-tenant ownership boundary. */
public final class TenantRbacService {
    public static final String STATE_CONTRACT = "ONSURE_TENANT_RESOURCE_BINDINGS_V1";
    public static final String EVENT_CONTRACT = "ONSURE_TENANT_ACCESS_EVENT_V1";
    private static final String REGISTRY_ID = "WORKSPACE_TENANT_RESOURCE_REGISTRY";
    private static final Set<String> APPROVAL_OPERATIONS = Set.of(
            "plan.approve", "patch.apply", "patch.rollback", "git.commit", "git.draft-pr",
            "license.issue", "license.suspend", "license.revoke",
            "case.record-payment", "case.verify-payment", "case.record-refund",
            "case.verify-refund", "case.legal-hold", "case.delete");
    private static final Set<String> READ_OPERATIONS = Set.of(
            "project.read-target", "project.list-targets", "job.read", "license.read", "case.read",
            "artifact.read", "deployment.read-target");
    private static final Set<String> SEMANTIC_OPERATOR_OR_AUDITOR_OPERATIONS = Set.of(
            "semantic.applicability.evaluate",
            "semantic.denominator.discover",
            "semantic.denominator.challenge",
            "semantic.denominator.lock",
            "semantic.reperformance.run",
            "assurance.evidence-graph.validate",
            "assurance.composition.compute",
            "assurance.sod.record-stage",
            "assurance.sod.check",
            "assurance.four-eyes.record-approval",
            "assurance.four-eyes.check",
            "assurance.plugin.qualify",
            "assurance.external-integration.reconcile",
            "assurance.learning.candidate.register",
            "assurance.learning.validation.request",
            "assurance.learning.completion-status.check",
            "assurance.oracle.multi-evaluate",
            "assurance.corpus.integrity-check",
            "assurance.validator.regression-qualify",
            "assurance.learning.stop-decision.compute",
            "assurance.learning.decision-currentness.evaluate",
            "assurance.learning.evidence-observation.record",
            "assurance.release.qualify",
            "assurance.validation.snapshot-verify",
            "assurance.validation.experiment-evaluate",
            "assurance.learning.effectiveness.evaluate",
            "assurance.strength-ceiling.compute",
            "assurance.learning.data-residency.check",
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
            "assurance.judge.independence-check",
            "assurance.requalification.trigger-evaluate",
            "assurance.agent-memory.conflict-resolve",
            "assurance.tool-call.authorization-check",
            "assurance.prompt.provenance-check",
            "assurance.ai-safety.claim-independence-check",
            "assurance.delegation.chain-check",
            "assurance.provider.drift-check",
            "assurance.multi-agent.corroboration-check",
            "assurance.hazard.create",
            "assurance.appeal.file",
            "assurance.appeal.submit-evidence",
            "assurance.engagement.check-scope",
            "assurance.accessibility.validate-render",
            "assurance.migration.reconcile",
            "assurance.session.create",
            "assurance.session.check-valid",
            "assurance.learning.human-override.trend-report",
            "assurance.learning.revalidation.complete",
            "assurance.learning.revalidation.backlog-status");
    private static final Set<String> SEMANTIC_AUDITOR_OPERATIONS = Set.of(
            "semantic.authority.revalidate",
            "semantic.independence.assess",
            "semantic.freshness.invalidate",
            "semantic.freshness.reconstruct",
            "semantic.validator.requalify",
            "assurance.final-candidate.reconstruct",
            "assurance.otester.accept",
            "assurance.oaudit.accept",
            "deployment.verify-installed",
            "assurance.certificate.issue",
            "assurance.revocation.issue",
            "assurance.revocation.check",
            "assurance.offline-trust-bundle.evaluate",
            "assurance.delegation.grant",
            "assurance.delegation.check",
            "assurance.break-glass.invoke",
            "assurance.break-glass.review",
            "assurance.learning.validation.pack.issue",
            "assurance.learning.validation.receipt.record",
            "assurance.learning.promotion.approve",
            "assurance.learning.applied-lock.record",
            "assurance.learning.scope-promotion.decide",
            "assurance.learning.derived-lineage.dispose",
            "assurance.learning.human-override.decide",
            "assurance.learning.counterevidence.dispose",
            "assurance.learning.challenge-set-access.decide",
            "assurance.learning.ground-truth.declare-epoch",
            "assurance.hazard.advance",
            "assurance.appeal.assign-reviewer",
            "assurance.appeal.transition",
            "assurance.appeal.decide",
            "assurance.offboarding.request",
            "assurance.offboarding.advance",
            "assurance.migration.cutover",
            "assurance.migration.rollback");
    private static final Set<String> SEMANTIC_APPROVAL_OPERATIONS = Set.of(
            "assurance.human-accept");
    private static final Set<String> SEMANTIC_OPERATOR_OPERATIONS = Set.of(
            "git.push");

    @FunctionalInterface
    public interface AuthorizedCall<T> { T call() throws Exception; }

    private final DurableStateLedger ledger;
    private final Path workspaceRoot;

    public TenantRbacService(Path workspaceRoot) throws Exception {
        Path workspace = workspaceRoot.toAbsolutePath().normalize();
        if (!Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(workspace)) {
            throw new IllegalArgumentException("TENANT_RBAC_WORKSPACE_INVALID");
        }
        this.workspaceRoot = workspace;
        Path root = workspace.resolve(".onsure/identity/resource-bindings");
        rejectStateSymlink(root, workspace);
        this.ledger = new DurableStateLedger(
                root, STATE_CONTRACT, EVENT_CONTRACT, "registry_id", REGISTRY_ID);
        Path state = root.resolve("state.json");
        if (!Files.exists(state, LinkOption.NOFOLLOW_LINKS)) {
            try {
                ledger.initialize(new LinkedHashMap<>(Map.of("bindings", new LinkedHashMap<>())),
                        "REGISTRY_INITIALIZED", "ONSURE_IDENTITY_BOOTSTRAP",
                        Map.of("default_access", "DENY"));
            } catch (IllegalStateException race) {
                if (!"STATE_LEDGER_ALREADY_EXISTS".equals(race.getMessage())) throw race;
            }
        }
    }

    public <T> T execute(
            AuthenticatedWorkflowIdentity identity,
            String operation,
            JsonNode request,
            AuthorizedCall<T> call) throws Exception {
        if (identity == null) throw new SecurityException("AUTHENTICATED_IDENTITY_REQUIRED");
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("WORKFLOW_REQUEST_OBJECT_REQUIRED");
        }
        requireRole(identity, operation);
        rejectContextSubstitution(identity, request);
        AtomicReference<T> result = new AtomicReference<>();
        ledger.mutate("AUTHORIZED_WORKFLOW", identity.actorId(), state -> {
            Map<String, String> bindings = bindings(state);
            AccessPlan plan = accessPlan(operation, request);
            for (String resource : plan.requires()) requireOwned(bindings, resource, identity.tenantId());
            for (String resource : plan.claims()) requireClaimable(bindings, resource, identity.tenantId());
            result.set(call.call());
            List<String> claims = new ArrayList<>(plan.claims());
            claims.addAll(resultClaims(operation, result.get()));
            for (String resource : claims) requireClaimable(bindings, resource, identity.tenantId());
            for (String resource : claims) bindings.put(resource, identity.tenantId());
            state.put("bindings", bindings);
            return Map.of(
                    "operation", operation,
                    "tenant_id", identity.tenantId(),
                    "workspace_id", identity.workspaceId(),
                    "authentication_method", identity.authenticationMethod().name(),
                    "required_resources", plan.requires(),
                    "claimed_resources", List.copyOf(claims),
                    "decision", "ALLOW_NONFINAL");
        });
        return result.get();
    }

    public DurableStateLedger.Verification verify() throws Exception { return ledger.verify(); }

    private static void rejectStateSymlink(Path root, Path workspace) {
        Path current = root;
        while (current != null && current.startsWith(workspace)) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("TENANT_RBAC_STATE_SYMLINK");
            }
            if (current.equals(workspace)) break;
            current = current.getParent();
        }
    }

    private static void requireRole(AuthenticatedWorkflowIdentity identity, String operation) {
        Set<AuthenticatedWorkflowIdentity.Role> roles = identity.roles();
        if (roles.contains(AuthenticatedWorkflowIdentity.Role.ADMIN)) return;
        AuthenticatedWorkflowIdentity.Role required;
        boolean allowed;
        if (SEMANTIC_AUDITOR_OPERATIONS.contains(operation)) {
            required = AuthenticatedWorkflowIdentity.Role.AUDITOR;
            allowed = roles.contains(required);
        } else if (SEMANTIC_APPROVAL_OPERATIONS.contains(operation)) {
            required = AuthenticatedWorkflowIdentity.Role.APPROVER;
            allowed = roles.contains(required);
        } else if (SEMANTIC_OPERATOR_OR_AUDITOR_OPERATIONS.contains(operation)) {
            required = AuthenticatedWorkflowIdentity.Role.OPERATOR;
            allowed = roles.contains(AuthenticatedWorkflowIdentity.Role.OPERATOR)
                    || roles.contains(AuthenticatedWorkflowIdentity.Role.AUDITOR);
        } else if (SEMANTIC_OPERATOR_OPERATIONS.contains(operation)) {
            required = AuthenticatedWorkflowIdentity.Role.OPERATOR;
            allowed = roles.contains(required);
        } else if (APPROVAL_OPERATIONS.contains(operation)) {
            required = AuthenticatedWorkflowIdentity.Role.APPROVER;
            allowed = roles.contains(required);
        } else if (READ_OPERATIONS.contains(operation)) {
            required = AuthenticatedWorkflowIdentity.Role.VIEWER;
            allowed = roles.stream().anyMatch(Set.of(
                    AuthenticatedWorkflowIdentity.Role.VIEWER,
                    AuthenticatedWorkflowIdentity.Role.OPERATOR,
                    AuthenticatedWorkflowIdentity.Role.APPROVER,
                    AuthenticatedWorkflowIdentity.Role.AUDITOR)::contains);
        } else {
            required = AuthenticatedWorkflowIdentity.Role.OPERATOR;
            allowed = roles.contains(required);
        }
        if (!allowed) {
            throw new SecurityException("RBAC_OPERATION_DENIED:" + operation + ":" + required);
        }
    }

    private static void rejectContextSubstitution(
            AuthenticatedWorkflowIdentity identity, JsonNode request) {
        JsonNode tenant = request.path("tenant_context");
        JsonNode actor = request.path("actor");
        if (!actor.isMissingNode() && !actor.isNull()
                && !identity.actorId().equals(actor.asText())) {
            throw new SecurityException("AUTHENTICATED_ACTOR_SUBSTITUTION");
        }
        if (tenant.isMissingNode() || tenant.isNull()) return;
        if (!tenant.isObject()
                || !identity.organizationId().equals(tenant.path("organization_id").asText())
                || !identity.tenantId().equals(tenant.path("tenant_id").asText())
                || !identity.workspaceId().equals(tenant.path("workspace_id").asText())
                || !identity.actorId().equals(tenant.path("actor_id").asText())
                || !identity.dataRegion().equals(tenant.path("data_region").asText())
                || !rolesMatch(identity, tenant.path("roles"))) {
            throw new SecurityException("AUTHENTICATED_TENANT_CONTEXT_SUBSTITUTION");
        }
    }

    private static boolean rolesMatch(AuthenticatedWorkflowIdentity identity, JsonNode roles) {
        if (!roles.isArray()) return false;
        java.util.HashSet<String> supplied = new java.util.HashSet<>();
        roles.forEach(role -> supplied.add(role.asText()));
        return supplied.size() == roles.size()
                && supplied.equals(identity.roles().stream()
                        .map(Enum::name)
                        .collect(java.util.stream.Collectors.toSet()));
    }

    private AccessPlan accessPlan(String operation, JsonNode request) {
        List<String> requires = new ArrayList<>();
        List<String> claims = new ArrayList<>();
        String workspace = text(request, "workspace_id");
        String project = text(request, "project_id");
        String target = text(request, "target_id");
        if ("project.register-workspace".equals(operation) && workspace != null) {
            claims.add("workspace:" + workspace);
        } else if (workspace != null) {
            requires.add("workspace:" + workspace);
        }
        if ("project.register".equals(operation) && project != null) {
            claims.add("project:" + project);
        } else if (project != null) {
            requires.add("project:" + project);
        }
        if ("project.register-target".equals(operation) && project != null && target != null) {
            claims.add("target:" + project + ":" + target);
        } else if (project != null && target != null) {
            requires.add("target:" + project + ":" + target);
        }
        addEntity(operation, request, "deployment_target_id", "deployment", "deployment.register-target", requires, claims);
        addEntity(operation, request, "job_id", "job", "job.create", requires, claims);
        addEntity(operation, request, "license_id", "license", "license.issue", requires, claims);
        addEntity(operation, request, "case_id", "case", "case.open", requires, claims);
        if ("artifact.read".equals(operation)) {
            String runRoot = text(request, "run_root");
            if (runRoot == null) throw new IllegalArgumentException("RUN_ROOT_MISSING");
            requires.add(runResource(runRoot));
        }
        return new AccessPlan(List.copyOf(requires), List.copyOf(claims));
    }

    private List<String> resultClaims(String operation, Object result) {
        if (!"validation.run".equals(operation) || !(result instanceof Map<?, ?> envelope)) {
            return List.of();
        }
        Object payload = envelope.get("result");
        if (!(payload instanceof Map<?, ?> values)) return List.of();
        Object runRoot = values.get("run_root");
        if (!(runRoot instanceof String path) || path.isBlank()) return List.of();
        return List.of(runResource(path));
    }

    private String runResource(String value) {
        Path path;
        try { path = Path.of(value).toAbsolutePath().normalize(); }
        catch (Exception invalid) { throw new IllegalArgumentException("RUN_ROOT_INVALID", invalid); }
        if (!path.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("RUN_ROOT_OUTSIDE_WORKSPACE");
        }
        return "run:" + workspaceRoot.relativize(path).toString().replace('\\', '/');
    }

    private static void addEntity(
            String operation, JsonNode request, String field, String type, String createOperation,
            List<String> requires, List<String> claims) {
        String id = text(request, field);
        if (id == null) return;
        if (createOperation.equals(operation)) claims.add(type + ":" + id);
        else requires.add(type + ":" + id);
    }

    private static void requireOwned(Map<String, String> bindings, String resource, String tenant) {
        String owner = bindings.get(resource);
        if (owner == null) throw new SecurityException("TENANT_RESOURCE_BINDING_MISSING:" + resource);
        if (!tenant.equals(owner)) throw new SecurityException("CROSS_TENANT_RESOURCE_ACCESS_DENIED:" + resource);
    }

    private static void requireClaimable(Map<String, String> bindings, String resource, String tenant) {
        String owner = bindings.get(resource);
        if (owner != null && !tenant.equals(owner)) {
            throw new SecurityException("CROSS_TENANT_RESOURCE_WRITE_DENIED:" + resource);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> bindings(Map<String, Object> state) {
        Object value = state.get("bindings");
        if (!(value instanceof Map<?, ?> source)) throw new IllegalStateException("TENANT_BINDINGS_INVALID");
        Map<String, String> result = new LinkedHashMap<>();
        source.forEach((key, owner) -> result.put(String.valueOf(key), String.valueOf(owner)));
        return result;
    }

    private static String text(JsonNode request, String field) {
        String value = request.path(field).asText("");
        return value.isBlank() ? null : value;
    }

    private record AccessPlan(List<String> requires, List<String> claims) {}
}
