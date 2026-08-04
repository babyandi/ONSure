package io.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.onsure.platform.ValidationModel.TargetType;
import io.onsure.platform.ValidationModel.ValidationTarget;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared command boundary used by CLI, Local API and VS Code. */
public final class LocalWorkflowDispatcher {
    public static final String CONTRACT = "ONSURE_LOCAL_WORKFLOW_DISPATCHER_V8";
    private static final Set<String> PROHIBITED_REGISTERED_EXECUTION_PROFILES = Set.of(
            "LOCAL_E2E", "LOCAL_MVF_E2E", "LOCAL_FIXTURE", "TRUSTED_LOCAL_FIXTURE",
            "LOCAL_E2E_TRUSTED_FIXTURE", "LOCAL_DEVELOPMENT");
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private final Path workspaceRoot;
    private final ApprovalAuthorityPaths approvalAuthority;
    private final AuthenticatedWorkflowIdentity identity;

    public LocalWorkflowDispatcher(Path workspaceRoot) {
        this(workspaceRoot, null);
    }

    /** Internal API path whose tenant and actor derive from a server-authenticated identity. */
    LocalWorkflowDispatcher(
            Path workspaceRoot, AuthenticatedWorkflowIdentity authenticatedIdentity) {
        this.workspaceRoot = requireWorkspace(workspaceRoot);
        this.approvalAuthority = ApprovalAuthorityPaths.forWorkspace(this.workspaceRoot);
        this.identity = authenticatedIdentity;
    }

    public Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (operation == null || !operation.matches("[a-z][a-z0-9.-]{2,80}")) {
            throw new IllegalArgumentException("WORKFLOW_OPERATION_INVALID");
        }
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("WORKFLOW_REQUEST_OBJECT_REQUIRED");
        }
        if (identity != null) {
            return new TenantRbacService(workspaceRoot).execute(
                    identity, operation, request, () -> dispatchAuthorized(operation, request));
        }
        return dispatchAuthorized(operation, request);
    }

    private Map<String, Object> dispatchAuthorized(String operation, JsonNode request) throws Exception {
        approvalAuthority.rejectRequestOverrides(operation, request);
        Map<String, Object> result = switch (operation) {
            case "project.register-workspace" -> projectRegisterWorkspace(request);
            case "project.register" -> projectRegister(request);
            case "project.register-target" -> projectRegisterTarget(request);
            case "project.read-target" -> projectReadTarget(request);
            case "project.list-targets" -> projectListTargets(request);
            case "program.learn" -> programLearn(request);
            case "plan.generate" -> planGenerate(request);
            case "plan.approve" -> planApprove(request);
            case "validation.run" -> validationRun(request);
            case "validation.resume" -> validationResume(request);
            case "knowledge.anonymize" -> knowledgeAnonymize(request);
            case "patch.verify-approval" -> patchVerifyApproval(request);
            case "patch.apply" -> patchApply(request);
            case "patch.rollback" -> patchRollback(request);
            case "improvement.prove" -> improvementProve(request);
            case "git.commit" -> gitCommit(request);
            case "git.draft-pr" -> gitDraftPr(request);
            case "license.issue" -> licenseIssue(request);
            case "license.activate" -> licenseActivate(request);
            case "license.validate" -> licenseValidate(request);
            case "license.authorize" -> licenseAuthorize(request);
            case "license.reserve" -> licenseReserve(request);
            case "license.commit-reservation" -> licenseCommitReservation(request);
            case "license.release-reservation" -> licenseReleaseReservation(request);
            case "license.suspend" -> licenseSuspend(request);
            case "license.revoke" -> licenseRevoke(request);
            case "license.read" -> licenseRead(request);
            case "case.open" -> caseOpen(request);
            case "case.preflight" -> casePreflight(request);
            case "case.quote" -> caseQuote(request);
            case "case.accept-order" -> caseAcceptOrder(request);
            case "case.record-payment" -> caseRecordPayment(request);
            case "case.verify-payment" -> caseVerifyPayment(request);
            case "case.start-work" -> caseStartWork(request);
            case "case.deliver" -> caseDeliver(request);
            case "case.accept-delivery" -> caseAcceptDelivery(request);
            case "case.request-refund" -> caseRequestRefund(request);
            case "case.record-refund" -> caseRecordRefund(request);
            case "case.verify-refund" -> caseVerifyRefund(request);
            case "case.legal-hold" -> caseLegalHold(request);
            case "case.delete" -> caseDelete(request);
            case "case.cancel" -> caseCancel(request);
            case "case.read" -> caseRead(request);
            default -> throw new IllegalArgumentException("WORKFLOW_OPERATION_UNSUPPORTED:" + operation);
        };
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contract", CONTRACT);
        envelope.put("operation", operation);
        envelope.put("result", result);
        if (identity != null) {
            envelope.put("authenticated_actor", identity.actorId());
            envelope.put("authenticated_tenant", identity.tenantId());
        }
        envelope.put("approval_authority", approvalAuthority.authorityRoot().toString());
        envelope.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        envelope.put("independent_otester", "NOT_RUN");
        envelope.put("independent_oaudit", "NOT_RUN");
        envelope.put("final_claim_allowed", false);
        return Map.copyOf(envelope);
    }

    private ProductCatalog catalog() {
        return new ProductCatalog(workspaceRoot.resolve(".onsure/product-catalog"));
    }

    private Map<String, Object> projectRegisterWorkspace(JsonNode request) throws Exception {
        ProductCatalog catalog = catalog();
        ProductCatalog.Workspace workspace = new ProductCatalog.Workspace(
                requiredId(request, "workspace_id"), requiredText(request, "workspace_name"), Instant.now());
        catalog.registerWorkspace(workspace);
        return Map.of("workspace", workspace, "catalog_revision", catalog.revision(),
                "final_claim_allowed", false);
    }

    private Map<String, Object> projectRegister(JsonNode request) throws Exception {
        ProductCatalog catalog = catalog();
        ProductCatalog.Project project = new ProductCatalog.Project(
                requiredId(request, "project_id"), requiredId(request, "workspace_id"),
                requiredText(request, "project_name"), Instant.now());
        catalog.registerProject(project);
        return Map.of("project", project, "catalog_revision", catalog.revision(),
                "final_claim_allowed", false);
    }

    private Map<String, Object> projectRegisterTarget(JsonNode request) throws Exception {
        ProductCatalog catalog = catalog();
        Path source = inputPath(request, "source_root", true);
        String adapterId = request.path("adapter_id").asText(GenericManifestTargetAdapter.ID);
        String executionProfile = request.path("execution_profile").asText("REGISTERED_REVIEWED");
        if (PROHIBITED_REGISTERED_EXECUTION_PROFILES.contains(executionProfile)) {
            throw new IllegalArgumentException("REGISTERED_TARGET_TRUSTED_FIXTURE_PROFILE_PROHIBITED");
        }
        ValidationTarget target = new ValidationTarget(
                requiredId(request, "target_id"),
                request.path("target_name").asText(requiredId(request, "target_id")),
                TargetType.valueOf(requiredText(request, "target_type")),
                source,
                request.path("immutable_source_reference").asText(SourceReferenceBinding.treeReference(source)),
                adapterId,
                request.path("policy_profile").asText("ONSURE_DEFAULT_POLICY_V1"),
                executionProfile);
        ProductCatalog.RegisteredTarget registered = new ProductCatalog.RegisteredTarget(
                requiredId(request, "project_id"), target, Instant.now());
        catalog.registerTarget(registered);
        return Map.of("registered_target", registered, "catalog_revision", catalog.revision(),
                "final_claim_allowed", false);
    }

    private Map<String, Object> projectReadTarget(JsonNode request) throws Exception {
        ProductCatalog.RegisteredTarget registered = requireRegisteredTarget(
                requiredId(request, "project_id"), requiredId(request, "target_id"));
        return Map.of("registered_target", registered, "catalog_revision", catalog().revision(),
                "final_claim_allowed", false);
    }

    private Map<String, Object> projectListTargets(JsonNode request) throws Exception {
        ProductCatalog catalog = catalog();
        return Map.of("targets", catalog.targets(requiredId(request, "project_id")),
                "catalog_revision", catalog.revision(), "final_claim_allowed", false);
    }

    private Map<String, Object> programLearn(JsonNode request) throws Exception {
        rejectRegisteredTargetOverrides(request);
        String projectId = requiredId(request, "project_id");
        String targetId = requiredId(request, "target_id");
        String programId = requiredId(request, "program_id");
        if (!programId.equals(targetId)) {
            throw new IllegalArgumentException("PROGRAM_ID_TARGET_ID_MISMATCH");
        }
        ValidationTarget target = requireRegisteredTarget(projectId, targetId).target();
        Path output = outputPath(request, "output_file",
                ".onsure/profiles/" + targetId + "/program-profile.json");
        return new ProgramLearningService().learn(
                target.sourceRoot(), projectId, target.targetId(), output);
    }

    private Map<String, Object> planGenerate(JsonNode request) throws Exception {
        rejectRegisteredTargetOverrides(request);
        String projectId = requiredId(request, "project_id");
        String targetId = requiredId(request, "target_id");
        ValidationTarget target = requireRegisteredTarget(projectId, targetId).target();
        if (!GenericManifestTargetAdapter.ID.equals(target.adapterId())) {
            throw new IllegalArgumentException("LOCAL_CORE_WORKFLOW_SUPPORTS_GENERIC_ADAPTER_ONLY");
        }
        Path profile = inputPath(request, "program_profile_file", true);
        int fixtureCount = new GenericManifestTargetAdapter().loadFixtures(target).size();
        Path output = outputPath(request, "output_file",
                ".onsure/plans/" + targetId + "-execution-plan.json");
        return new RegisteredExecutionPlanGenerationService().generate(
                projectId, target, profile, fixtureCount, output);
    }

    private Map<String, Object> planApprove(JsonNode request) throws Exception {
        return new ExecutionPlanApprovalService().approve(
                inputPath(request, "plan_file", true),
                inputPath(request, "signed_approval_receipt", true),
                approvalAuthority.requireTrustedKeyRegistry(),
                approvalAuthority.replayLedgerForConsumption(),
                outputPath(request, "approved_plan_file", ".onsure/plans/approved-execution-plan.json"));
    }

    private Map<String, Object> validationRun(JsonNode request) throws Exception {
        rejectRegisteredTargetOverrides(request);
        String projectId = requiredId(request, "project_id");
        String targetId = requiredId(request, "target_id");
        ValidationTarget target = requireRegisteredTarget(projectId, targetId).target();
        if (!GenericManifestTargetAdapter.ID.equals(target.adapterId())) {
            throw new IllegalArgumentException("LOCAL_CORE_WORKFLOW_SUPPORTS_GENERIC_ADAPTER_ONLY");
        }
        Path store = outputPath(request, "store_root", ".onsure/validation-data");
        Path approvedPlan = optionalInputPath(request, "approved_execution_plan_file");
        if (approvedPlan == null
                && !ExecutionPlanService.trustedFixtureAutoApproval(target.executionProfile())) {
            throw new IllegalArgumentException("APPROVED_EXECUTION_PLAN_BUNDLE_REQUIRED");
        }
        ValidationEngine engine = ValidationEngine.defaultEngine(store);
        ValidationEngine.RunResult run;
        if (approvedPlan == null) {
            for (String field : List.of("original_execution_plan_file", "signed_approval_receipt")) {
                if (!request.path(field).asText("").isBlank()) {
                    throw new IllegalArgumentException("INCOMPLETE_EXECUTION_PLAN_APPROVAL_BUNDLE:" + field);
                }
            }
            run = engine.run(target);
        } else {
            ValidationEngine.ApprovedExecutionPlanBundle bundle =
                    new ValidationEngine.ApprovedExecutionPlanBundle(
                            approvedPlan,
                            inputPath(request, "original_execution_plan_file", true),
                            inputPath(request, "signed_approval_receipt", true),
                            approvalAuthority.requireTrustedKeyRegistry(),
                            approvalAuthority.requireReplayLedger());
            run = engine.run(target, bundle);
        }
        return Map.of(
                "decision", run.report().decision().name(),
                "run_root", run.runRoot().toString(),
                "report", run.report(),
                "registered_project_id", projectId,
                "registered_target_id", targetId,
                "approved_execution_plan_consumed", approvedPlan != null,
                "approval_bundle_required", approvedPlan != null,
                "final_claim_allowed", false);
    }

    private Map<String, Object> validationResume(JsonNode request) throws Exception {
        rejectRegisteredTargetOverrides(request);
        String projectId = requiredId(request, "project_id");
        String targetId = requiredId(request, "target_id");
        ValidationTarget target = requireRegisteredTarget(projectId, targetId).target();
        if (!GenericManifestTargetAdapter.ID.equals(target.adapterId())) {
            throw new IllegalArgumentException("LOCAL_CORE_WORKFLOW_SUPPORTS_GENERIC_ADAPTER_ONLY");
        }
        Path store = outputPath(request, "store_root", ".onsure/validation-data");
        Path runRoot = inputPath(request, "run_root", true);
        if (!runRoot.startsWith(store.toAbsolutePath().normalize())) {
            throw new IllegalArgumentException("VALIDATION_RESUME_RUN_OUTSIDE_STORE");
        }
        ValidationEngine.RunResult run = ValidationEngine.defaultEngine(store).resumeInternal(target, runRoot);
        return Map.of(
                "decision", run.report().decision().name(),
                "run_root", run.runRoot().toString(),
                "report", run.report(),
                "resume_performed", true,
                "final_claim_allowed", false);
    }

    private Map<String, Object> knowledgeAnonymize(JsonNode request) throws Exception {
        Path saltFile = inputPath(request, "workspace_salt_file", true);
        byte[] salt = Files.readAllBytes(saltFile);
        if (salt.length < 32 || salt.length > 4096) {
            throw new IllegalArgumentException("WORKSPACE_SALT_LENGTH_INVALID");
        }
        JsonNode knowledgeNode = request.path("knowledge");
        if (!knowledgeNode.isObject()) throw new IllegalArgumentException("KNOWLEDGE_OBJECT_REQUIRED");
        Map<String, String> knowledge = new java.util.LinkedHashMap<>();
        knowledgeNode.fields().forEachRemaining(entry -> {
            if (!entry.getValue().isTextual()) throw new IllegalArgumentException("KNOWLEDGE_VALUE_TEXT_REQUIRED");
            knowledge.put(entry.getKey(), entry.getValue().asText());
        });
        ProjectKnowledgeSeparationService.Result result = new ProjectKnowledgeSeparationService()
                .separate(requiredId(request, "project_id"), knowledge, salt);
        return mapper.convertValue(result, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private Map<String, Object> patchVerifyApproval(JsonNode request) throws Exception {
        return new PatchApprovalExchangeVerifier().verify(
                inputPath(request, "approval_request_file", true),
                inputPath(request, "approval_receipt_file", true),
                inputPath(request, "patch_plan_file", true));
    }

    private ProductCatalog.RegisteredTarget requireRegisteredTarget(
            String projectId, String targetId) throws Exception {
        return catalog().targets(projectId).stream()
                .filter(value -> value.target().targetId().equals(targetId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "REGISTERED_TARGET_NOT_FOUND_IN_PROJECT:" + projectId + ":" + targetId));
    }

    private static void rejectRegisteredTargetOverrides(JsonNode request) {
        for (String field : List.of(
                "source_root", "target_name", "target_type", "adapter_id",
                "immutable_source_reference", "policy_profile", "execution_profile")) {
            if (!request.path(field).asText("").isBlank()) {
                throw new IllegalArgumentException("REGISTERED_TARGET_FIELD_OVERRIDE_PROHIBITED:" + field);
            }
        }
    }

    private Map<String, Object> patchApply(JsonNode request) throws Exception {
        Map<String, Object> exchange = null;
        if (!request.path("approval_request_file").asText("").isBlank()) {
            exchange = new PatchApprovalExchangeVerifier().verify(
                    inputPath(request, "approval_request_file", true),
                    inputPath(request, "approval_receipt_file", true),
                    inputPath(request, "patch_plan_file", true));
        }
        Map<String, Object> applied = new ImprovementWorkflowService().applyApprovedPlan(
                inputPath(request, "repository_root", true),
                inputPath(request, "patch_plan_file", true),
                inputPath(request, "approval_receipt_file", true),
                approvalAuthority.requireTrustedKeyRegistry(),
                approvalAuthority.replayLedgerForConsumption(),
                outputPath(request, "worktree_root", ".onsure/worktrees/approved-patch"),
                outputPath(request, "evidence_root", ".onsure/improvement-evidence"));
        if (exchange == null) return applied;
        Map<String, Object> bound = new java.util.LinkedHashMap<>(applied);
        bound.put("approval_exchange_verification", exchange);
        return Map.copyOf(bound);
    }

    private Map<String, Object> patchRollback(JsonNode request) throws Exception {
        return new ImprovementWorkflowService().rollback(
                inputPath(request, "worktree_root", true),
                inputPath(request, "patch_apply_receipt_file", true),
                outputPath(request, "rollback_receipt_file", ".onsure/improvement-evidence/rollback-receipt.json"));
    }

    private Map<String, Object> improvementProve(JsonNode request) throws Exception {
        return new ImprovementProofService().prove(
                inputPath(request, "baseline_report_file", true),
                inputPath(request, "current_report_file", true),
                inputPath(request, "patch_apply_receipt_file", true),
                outputPath(request, "output_file", ".onsure/improvement-evidence/improvement-proof.json"));
    }

    private Map<String, Object> gitCommit(JsonNode request) throws Exception {
        return new GitWorkflowService().commitApprovedWorktree(
                inputPath(request, "worktree_root", true),
                inputPath(request, "patch_apply_receipt_file", true),
                inputPath(request, "improvement_proof_file", true),
                inputPath(request, "delivery_approval_file", true),
                approvalAuthority.requireTrustedKeyRegistry(),
                approvalAuthority.replayLedgerForConsumption(),
                requiredText(request, "commit_message"),
                outputPath(request, "output_file", ".onsure/git/change-set.json"));
    }

    private Map<String, Object> gitDraftPr(JsonNode request) throws Exception {
        return new GitWorkflowService().pushAndOpenDraftPr(
                inputPath(request, "worktree_root", true),
                inputPath(request, "change_set_file", true),
                inputPath(request, "delivery_approval_file", true),
                requiredText(request, "base_branch"),
                requiredText(request, "title"),
                inputPath(request, "body_file", true),
                outputPath(request, "output_file", ".onsure/git/draft-pr-receipt.json"));
    }

    private LicenseLifecycleService licenses(JsonNode request) {
        return new LicenseLifecycleService(
                outputPathUnchecked(request, "license_store_root", ".onsure/license-store"));
    }

    private Map<String, Object> licenseIssue(JsonNode request) throws Exception {
        return licenses(request).issue(tenant(request), requiredId(request, "license_id"),
                requiredId(request, "product_id"), requiredId(request, "edition"),
                Instant.parse(requiredText(request, "valid_from")),
                Instant.parse(requiredText(request, "valid_until")),
                request.path("total_credits").asLong(-1),
                request.path("offline_grace_hours").asLong(0),
                request.path("clock_tolerance_seconds").asLong(0),
                stringList(request.path("entitlements")), actor(request));
    }

    private Map<String, Object> licenseActivate(JsonNode request) throws Exception {
        return licenses(request).activate(requiredId(request, "license_id"), observedAt(request), actor(request));
    }
    private Map<String, Object> licenseValidate(JsonNode request) throws Exception {
        return licenses(request).validate(requiredId(request, "license_id"), observedAt(request),
                request.path("online").asBoolean(false), actor(request));
    }
    private Map<String, Object> licenseAuthorize(JsonNode request) throws Exception {
        return licenses(request).authorize(requiredId(request, "license_id"),
                requiredId(request, "entitlement"), observedAt(request),
                request.path("online").asBoolean(false), actor(request));
    }
    private Map<String, Object> licenseReserve(JsonNode request) throws Exception {
        return licenses(request).reserve(requiredId(request, "license_id"),
                requiredId(request, "reservation_id"), request.path("credits").asLong(-1),
                Instant.parse(requiredText(request, "expires_at")), observedAt(request), actor(request));
    }
    private Map<String, Object> licenseCommitReservation(JsonNode request) throws Exception {
        return licenses(request).commitReservation(requiredId(request, "license_id"),
                requiredId(request, "reservation_id"), observedAt(request), actor(request));
    }
    private Map<String, Object> licenseReleaseReservation(JsonNode request) throws Exception {
        return licenses(request).releaseReservation(requiredId(request, "license_id"),
                requiredId(request, "reservation_id"), observedAt(request), actor(request));
    }
    private Map<String, Object> licenseSuspend(JsonNode request) throws Exception {
        return licenses(request).suspend(requiredId(request, "license_id"),
                requiredText(request, "reason"), observedAt(request), actor(request));
    }
    private Map<String, Object> licenseRevoke(JsonNode request) throws Exception {
        return licenses(request).revoke(requiredId(request, "license_id"),
                requiredText(request, "reason"), observedAt(request), actor(request));
    }
    private Map<String, Object> licenseRead(JsonNode request) throws Exception {
        return licenses(request).read(requiredId(request, "license_id"));
    }

    private ServiceCaseLifecycleService cases(JsonNode request) {
        return new ServiceCaseLifecycleService(
                outputPathUnchecked(request, "case_store_root", ".onsure/service-cases"));
    }

    private Map<String, Object> caseOpen(JsonNode request) throws Exception {
        return cases(request).open(tenant(request), requiredId(request, "case_id"),
                requiredId(request, "service_type"), requiredText(request, "target_reference"), actor(request));
    }
    private Map<String, Object> casePreflight(JsonNode request) throws Exception {
        return cases(request).recordPreflight(requiredId(request, "case_id"),
                requiredText(request, "decision"), requiredDigest(request, "source_digest"),
                stringList(request.path("findings")), actor(request));
    }
    private Map<String, Object> caseQuote(JsonNode request) throws Exception {
        return cases(request).createQuote(requiredId(request, "case_id"), requiredId(request, "quote_id"),
                requiredText(request, "currency"), request.path("amount_minor").asLong(-1),
                Instant.parse(requiredText(request, "expires_at")),
                stringList(request.path("scope")), actor(request));
    }
    private Map<String, Object> caseAcceptOrder(JsonNode request) throws Exception {
        return cases(request).acceptOrder(requiredId(request, "case_id"),
                requiredId(request, "quote_id"), actor(request));
    }
    private Map<String, Object> caseRecordPayment(JsonNode request) throws Exception {
        return cases(request).recordPaymentReceipt(requiredId(request, "case_id"),
                requiredId(request, "provider"), requiredText(request, "provider_receipt_id"),
                requiredText(request, "currency"), request.path("amount_minor").asLong(-1),
                requiredDigest(request, "receipt_digest"), actor(request));
    }
    private Map<String, Object> caseVerifyPayment(JsonNode request) throws Exception {
        return cases(request).verifyPayment(requiredId(request, "case_id"),
                inputPath(request, "signed_verification_receipt", true),
                approvalAuthority.requireTrustedKeyRegistry(),
                approvalAuthority.replayLedgerForConsumption(), actor(request));
    }
    private Map<String, Object> caseStartWork(JsonNode request) throws Exception {
        return cases(request).startWork(requiredId(request, "case_id"), actor(request));
    }
    private Map<String, Object> caseDeliver(JsonNode request) throws Exception {
        return cases(request).deliver(requiredId(request, "case_id"),
                requiredDigest(request, "evidence_bundle_digest"),
                requiredText(request, "delivery_pointer"), actor(request));
    }
    private Map<String, Object> caseAcceptDelivery(JsonNode request) throws Exception {
        return cases(request).acceptDelivery(requiredId(request, "case_id"),
                requiredDigest(request, "acceptance_receipt_digest"), actor(request));
    }
    private Map<String, Object> caseRequestRefund(JsonNode request) throws Exception {
        return cases(request).requestRefund(requiredId(request, "case_id"),
                requiredText(request, "reason"), request.path("amount_minor").asLong(-1), actor(request));
    }
    private Map<String, Object> caseRecordRefund(JsonNode request) throws Exception {
        return cases(request).completeRefund(requiredId(request, "case_id"),
                requiredText(request, "provider_receipt_id"),
                requiredDigest(request, "receipt_digest"), actor(request));
    }
    private Map<String, Object> caseVerifyRefund(JsonNode request) throws Exception {
        return cases(request).verifyRefund(requiredId(request, "case_id"),
                inputPath(request, "signed_verification_receipt", true),
                approvalAuthority.requireTrustedKeyRegistry(),
                approvalAuthority.replayLedgerForConsumption(), actor(request));
    }
    private Map<String, Object> caseLegalHold(JsonNode request) throws Exception {
        return cases(request).setLegalHold(requiredId(request, "case_id"),
                request.path("active").asBoolean(false),
                request.path("reason").asText(""), actor(request));
    }
    private Map<String, Object> caseDelete(JsonNode request) throws Exception {
        return cases(request).recordDeletion(requiredId(request, "case_id"),
                inputPath(request, "signed_verification_receipt", true),
                approvalAuthority.requireTrustedKeyRegistry(),
                approvalAuthority.replayLedgerForConsumption(), actor(request));
    }
    private Map<String, Object> caseCancel(JsonNode request) throws Exception {
        return cases(request).cancel(requiredId(request, "case_id"),
                requiredText(request, "reason"), actor(request));
    }
    private Map<String, Object> caseRead(JsonNode request) throws Exception {
        return cases(request).read(requiredId(request, "case_id"));
    }

    private Map<String, Object> tenant(JsonNode request) {
        if (identity != null) {
            return Map.of(
                    "contract", "ONSURE_TENANT_CONTEXT_V1",
                    "organization_id", identity.organizationId(),
                    "tenant_id", identity.tenantId(),
                    "workspace_id", identity.workspaceId(),
                    "actor_id", identity.actorId(),
                    "roles", identity.roles().stream().map(Enum::name).sorted().toList(),
                    "data_region", identity.dataRegion(),
                    "created_at", Instant.now().toString());
        }
        JsonNode value = request.path("tenant_context");
        return mapper.convertValue(value, new com.fasterxml.jackson.core.type.TypeReference<>() {});
    }

    private String actor(JsonNode request) {
        return identity == null ? requiredId(request, "actor") : identity.actorId();
    }
    private Instant observedAt(JsonNode request) { return Instant.parse(requiredText(request, "observed_at")); }

    private Path inputPath(JsonNode request, String field, boolean mustExist) {
        String value = requiredText(request, field);
        Path path = Path.of(value).toAbsolutePath().normalize();
        requireInsideWorkspace(path, field);
        if (mustExist && (!Files.exists(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path))) {
            throw new IllegalArgumentException("WORKFLOW_INPUT_PATH_INVALID:" + field);
        }
        return path;
    }

    private Path optionalInputPath(JsonNode request, String field) {
        String value = request.path(field).asText();
        if (value == null || value.isBlank()) return null;
        Path path = Path.of(value).toAbsolutePath().normalize();
        requireInsideWorkspace(path, field);
        if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("WORKFLOW_INPUT_PATH_INVALID:" + field);
        }
        return path;
    }

    private Path outputPath(JsonNode request, String field, String fallback) {
        String value = request.path(field).asText();
        Path path = value.isBlank() ? workspaceRoot.resolve(fallback).normalize()
                : Path.of(value).toAbsolutePath().normalize();
        requireInsideWorkspace(path, field);
        if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("WORKFLOW_OUTPUT_SYMLINK_PROHIBITED:" + field);
        }
        return path;
    }

    private Path outputPathUnchecked(JsonNode request, String field, String fallback) {
        return outputPath(request, field, fallback);
    }

    private void requireInsideWorkspace(Path path, String field) {
        if (!path.startsWith(workspaceRoot)) {
            throw new IllegalArgumentException("WORKFLOW_PATH_OUTSIDE_WORKSPACE:" + field);
        }
        Path current = path;
        while (current != null && current.startsWith(workspaceRoot)) {
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(current)) {
                throw new IllegalArgumentException("WORKFLOW_PATH_SYMLINK_PROHIBITED:" + field);
            }
            if (current.equals(workspaceRoot)) break;
            current = current.getParent();
        }
    }

    private static Path requireWorkspace(Path value) {
        if (value == null) throw new IllegalArgumentException("WORKSPACE_ROOT_REQUIRED");
        Path path = value.toAbsolutePath().normalize();
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)) {
            throw new IllegalArgumentException("WORKSPACE_ROOT_INVALID");
        }
        return path;
    }

    private static String requiredId(JsonNode request, String field) {
        String value = requiredText(request, field);
        if (!value.matches("[A-Za-z0-9._:-]{1,160}")) {
            throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        }
        return value;
    }

    private static String requiredText(JsonNode request, String field) {
        String value = request.path(field).asText();
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field.toUpperCase() + "_REQUIRED");
        }
        return value;
    }

    private static String requiredDigest(JsonNode request, String field) {
        String value = requiredText(request, field);
        if (!value.matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException(field.toUpperCase() + "_INVALID");
        }
        return value;
    }

    private static List<String> stringList(JsonNode value) {
        if (!value.isArray()) return List.of();
        java.util.ArrayList<String> result = new java.util.ArrayList<>();
        value.forEach(item -> {
            String text = item.asText();
            if (text != null && !text.isBlank()) result.add(text);
        });
        return List.copyOf(result);
    }
}
