package kr.co.oruda.onsure.sdk.v1;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import kr.co.oruda.onsure.platform.LocalWorkflowDispatcher;
import java.nio.file.Path;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Supported external Java boundary for ONSure v1 workflows.
 *
 * <p>The caller cannot select approval trust roots, replay ledgers or product-owned output
 * locations. Every operation crosses the same dispatcher used by the CLI, local API and VS Code.
 */
public final class ONSureSdkV1 {
    public static final String CONTRACT = "ONSURE_PUBLIC_SDK_V1";
    private static final Pattern ID = Pattern.compile("[A-Za-z0-9._:-]{1,160}");

    public enum TargetType { GENERAL_SOFTWARE, AI_APPLICATION }

    public record WorkspaceRegistration(String workspaceId, String workspaceName) {
        public WorkspaceRegistration {
            workspaceId = requireId(workspaceId, "workspaceId");
            workspaceName = requireText(workspaceName, "workspaceName");
        }
    }

    public record ProjectRegistration(String workspaceId, String projectId, String projectName) {
        public ProjectRegistration {
            workspaceId = requireId(workspaceId, "workspaceId");
            projectId = requireId(projectId, "projectId");
            projectName = requireText(projectName, "projectName");
        }
    }

    public record TargetRegistration(
            String projectId, String targetId, String targetName,
            TargetType targetType, Path sourceRoot) {
        public TargetRegistration {
            projectId = requireId(projectId, "projectId");
            targetId = requireId(targetId, "targetId");
            targetName = requireText(targetName, "targetName");
            targetType = Objects.requireNonNull(targetType, "targetType");
            sourceRoot = requirePath(sourceRoot, "sourceRoot");
        }
    }

    public record RegisteredTargetRef(String projectId, String targetId) {
        public RegisteredTargetRef {
            projectId = requireId(projectId, "projectId");
            targetId = requireId(targetId, "targetId");
        }
    }

    public record PlanGeneration(RegisteredTargetRef target, Path programProfileFile) {
        public PlanGeneration {
            target = Objects.requireNonNull(target, "target");
            programProfileFile = requirePath(programProfileFile, "programProfileFile");
        }
    }

    /** Approval inputs deliberately omit key-registry and replay-ledger paths. */
    public record PlanApproval(Path originalPlanFile, Path signedApprovalReceipt) {
        public PlanApproval {
            originalPlanFile = requirePath(originalPlanFile, "originalPlanFile");
            signedApprovalReceipt = requirePath(signedApprovalReceipt, "signedApprovalReceipt");
        }
    }

    /** A complete approved bundle; authority paths remain product-owned. */
    public record ApprovedPlanBundle(
            Path approvedPlanFile, Path originalPlanFile, Path signedApprovalReceipt) {
        public ApprovedPlanBundle {
            approvedPlanFile = requirePath(approvedPlanFile, "approvedPlanFile");
            originalPlanFile = requirePath(originalPlanFile, "originalPlanFile");
            signedApprovalReceipt = requirePath(signedApprovalReceipt, "signedApprovalReceipt");
        }
    }

    public record CatalogResult(long revision, JsonNode resource) {
        public CatalogResult {
            if (revision < 0) throw new IllegalArgumentException("SDK_CATALOG_REVISION_INVALID");
            resource = requireObject(resource, "resource");
        }
    }

    public record DocumentResult(Path productOwnedPath, JsonNode document) {
        public DocumentResult {
            productOwnedPath = requirePath(productOwnedPath, "productOwnedPath");
            document = requireObject(document, "document");
        }
    }

    public record ValidationResult(
            String decision, Path runRoot, JsonNode report, boolean approvedPlanConsumed) {
        public ValidationResult {
            decision = requireText(decision, "decision");
            runRoot = requirePath(runRoot, "runRoot");
            report = requireObject(report, "report");
        }
    }

    public record Response<T>(
            String contract, String operation, T result, String assuranceClass,
            String independentOTester, String independentOAudit, boolean finalClaimAllowed) {
        public Response {
            if (!CONTRACT.equals(contract)) throw new IllegalArgumentException("SDK_CONTRACT_INVALID");
            operation = requireText(operation, "operation");
            result = Objects.requireNonNull(result, "result");
            assuranceClass = requireText(assuranceClass, "assuranceClass");
            independentOTester = requireText(independentOTester, "independentOTester");
            independentOAudit = requireText(independentOAudit, "independentOAudit");
            if (finalClaimAllowed) throw new IllegalArgumentException("SDK_FINAL_CLAIM_PROHIBITED");
        }
    }

    private final Path workspaceRoot;
    private final LocalWorkflowDispatcher dispatcher;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public ONSureSdkV1(Path workspaceRoot) {
        this.workspaceRoot = requirePath(workspaceRoot, "workspaceRoot");
        this.dispatcher = new LocalWorkflowDispatcher(this.workspaceRoot);
    }

    public Response<CatalogResult> registerWorkspace(WorkspaceRegistration request) throws Exception {
        Objects.requireNonNull(request, "request");
        ObjectNode body = mapper.createObjectNode();
        body.put("workspace_id", request.workspaceId());
        body.put("workspace_name", request.workspaceName());
        JsonNode envelope = dispatch("project.register-workspace", body);
        return response(envelope, new CatalogResult(
                envelope.path("result").path("catalog_revision").asLong(-1),
                envelope.path("result").path("workspace")));
    }

    public Response<CatalogResult> registerProject(ProjectRegistration request) throws Exception {
        Objects.requireNonNull(request, "request");
        ObjectNode body = mapper.createObjectNode();
        body.put("workspace_id", request.workspaceId());
        body.put("project_id", request.projectId());
        body.put("project_name", request.projectName());
        JsonNode envelope = dispatch("project.register", body);
        return response(envelope, new CatalogResult(
                envelope.path("result").path("catalog_revision").asLong(-1),
                envelope.path("result").path("project")));
    }

    public Response<CatalogResult> registerTarget(TargetRegistration request) throws Exception {
        Objects.requireNonNull(request, "request");
        ObjectNode body = mapper.createObjectNode();
        body.put("project_id", request.projectId());
        body.put("target_id", request.targetId());
        body.put("target_name", request.targetName());
        body.put("target_type", request.targetType().name());
        body.put("source_root", request.sourceRoot().toString());
        JsonNode envelope = dispatch("project.register-target", body);
        return response(envelope, new CatalogResult(
                envelope.path("result").path("catalog_revision").asLong(-1),
                envelope.path("result").path("registered_target")));
    }

    public Response<DocumentResult> learnProgram(RegisteredTargetRef request) throws Exception {
        Objects.requireNonNull(request, "request");
        ObjectNode body = targetBody(request);
        body.put("program_id", request.targetId());
        JsonNode envelope = dispatch("program.learn", body);
        Path output = workspaceRoot.resolve(".onsure/profiles")
                .resolve(request.targetId()).resolve("program-profile.json");
        return response(envelope, new DocumentResult(output, envelope.path("result")));
    }

    public Response<DocumentResult> generatePlan(PlanGeneration request) throws Exception {
        Objects.requireNonNull(request, "request");
        ObjectNode body = targetBody(request.target());
        body.put("program_profile_file", request.programProfileFile().toString());
        JsonNode envelope = dispatch("plan.generate", body);
        Path output = workspaceRoot.resolve(".onsure/plans")
                .resolve(request.target().targetId() + "-execution-plan.json");
        return response(envelope, new DocumentResult(output, envelope.path("result")));
    }

    public Response<DocumentResult> approvePlan(PlanApproval request) throws Exception {
        Objects.requireNonNull(request, "request");
        ObjectNode body = mapper.createObjectNode();
        body.put("plan_file", request.originalPlanFile().toString());
        body.put("signed_approval_receipt", request.signedApprovalReceipt().toString());
        JsonNode envelope = dispatch("plan.approve", body);
        Path output = workspaceRoot.resolve(".onsure/plans/approved-execution-plan.json");
        return response(envelope, new DocumentResult(output, envelope.path("result")));
    }

    public Response<ValidationResult> runValidation(RegisteredTargetRef request) throws Exception {
        return runValidation(request, null);
    }

    public Response<ValidationResult> runValidation(
            RegisteredTargetRef request, ApprovedPlanBundle approvedPlan) throws Exception {
        Objects.requireNonNull(request, "request");
        ObjectNode body = targetBody(request);
        if (approvedPlan != null) {
            body.put("approved_execution_plan_file", approvedPlan.approvedPlanFile().toString());
            body.put("original_execution_plan_file", approvedPlan.originalPlanFile().toString());
            body.put("signed_approval_receipt", approvedPlan.signedApprovalReceipt().toString());
        }
        JsonNode envelope = dispatch("validation.run", body);
        JsonNode result = envelope.path("result");
        return response(envelope, new ValidationResult(
                requiredNodeText(result, "decision"), Path.of(requiredNodeText(result, "run_root")),
                result.path("report"), result.path("approved_execution_plan_consumed").asBoolean(false)));
    }

    private ObjectNode targetBody(RegisteredTargetRef request) {
        ObjectNode body = mapper.createObjectNode();
        body.put("project_id", request.projectId());
        body.put("target_id", request.targetId());
        return body;
    }

    private JsonNode dispatch(String operation, ObjectNode request) throws Exception {
        JsonNode envelope = mapper.valueToTree(dispatcher.dispatch(operation, request));
        if (!LocalWorkflowDispatcher.CONTRACT.equals(envelope.path("contract").asText())) {
            throw new IllegalStateException("SDK_DISPATCHER_CONTRACT_MISMATCH");
        }
        if (!operation.equals(envelope.path("operation").asText())) {
            throw new IllegalStateException("SDK_OPERATION_BINDING_MISMATCH");
        }
        if (envelope.path("final_claim_allowed").asBoolean(true)) {
            throw new IllegalStateException("SDK_DISPATCHER_FINAL_CLAIM_INVALID");
        }
        return envelope;
    }

    private <T> Response<T> response(JsonNode envelope, T result) {
        return new Response<>(CONTRACT, requiredNodeText(envelope, "operation"), result,
                requiredNodeText(envelope, "assurance_class"),
                requiredNodeText(envelope, "independent_otester"),
                requiredNodeText(envelope, "independent_oaudit"), false);
    }

    private static String requiredNodeText(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalStateException("SDK_RESPONSE_FIELD_MISSING:" + field);
        return value;
    }

    private static String requireId(String value, String field) {
        if (value == null || !ID.matcher(value).matches()) {
            throw new IllegalArgumentException("SDK_ID_INVALID:" + field);
        }
        return value;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException("SDK_TEXT_REQUIRED:" + field);
        return value;
    }

    private static Path requirePath(Path value, String field) {
        if (value == null) throw new IllegalArgumentException("SDK_PATH_REQUIRED:" + field);
        return value.toAbsolutePath().normalize();
    }

    private static JsonNode requireObject(JsonNode value, String field) {
        if (value == null || !value.isObject()) {
            throw new IllegalArgumentException("SDK_OBJECT_REQUIRED:" + field);
        }
        return value.deepCopy();
    }
}
