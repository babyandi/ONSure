package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.learning.OfficialLearningLedger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Package-local candidate runtime boundary for Semantic Assurance v2 operations.
 *
 * <p>The service is intentionally not a public product surface. Calls must arrive through a
 * server-bound bridge that injects target authority context after durable tenant/resource
 * authorization. Strong independence, human acceptance and qualification claims fail closed until
 * their cryptographic/runtime verifiers are wired.</p>
 */
final class SemanticAssuranceV2WorkflowService {
    static final String CONTRACT = "ONSURE_SEMANTIC_ASSURANCE_V2_WORKFLOW_SERVICE_V2";
    private static final Set<String> OPERATIONS = Set.of(
            "semantic.applicability.evaluate",
            "semantic.denominator.discover",
            "semantic.denominator.challenge",
            "semantic.denominator.lock",
            "semantic.reperformance.run",
            "semantic.authority.revalidate",
            "semantic.independence.assess",
            "semantic.freshness.invalidate",
            "semantic.freshness.reconstruct",
            "semantic.validator.requalify",
            "assurance.otester.accept",
            "assurance.oaudit.accept",
            "assurance.human-accept",
            "assurance.final-candidate.reconstruct",
            "git.push",
            "deployment.verify-installed",
            "assurance.evidence-graph.validate",
            "assurance.composition.compute",
            "assurance.certificate.issue",
            "assurance.revocation.issue",
            "assurance.revocation.check",
            "assurance.offline-trust-bundle.evaluate",
            "assurance.sod.record-stage",
            "assurance.sod.check",
            "assurance.four-eyes.record-approval",
            "assurance.four-eyes.check",
            "assurance.delegation.grant",
            "assurance.delegation.check",
            "assurance.break-glass.invoke",
            "assurance.break-glass.review",
            "assurance.plugin.qualify",
            "assurance.external-integration.reconcile",
            "assurance.learning.candidate.register",
            "assurance.learning.validation.request",
            "assurance.learning.validation.pack.issue",
            "assurance.learning.validation.receipt.record",
            "assurance.learning.promotion.approve",
            "assurance.learning.applied-lock.record",
            "assurance.learning.completion-status.check",
            "assurance.oracle.multi-evaluate",
            "assurance.corpus.integrity-check",
            "assurance.validator.regression-qualify",
            "assurance.learning.stop-decision.compute",
            "assurance.learning.scope-promotion.decide",
            "assurance.learning.derived-lineage.dispose",
            "assurance.learning.decision-currentness.evaluate",
            "assurance.learning.human-override.decide",
            "assurance.learning.human-override.trend-report",
            "assurance.learning.counterevidence.dispose",
            "assurance.learning.challenge-set-access.decide",
            "assurance.learning.evidence-observation.record",
            "assurance.release.qualify",
            "assurance.validation.snapshot-verify",
            "assurance.validation.experiment-evaluate",
            "assurance.learning.effectiveness.evaluate",
            "assurance.strength-ceiling.compute",
            "assurance.learning.data-residency.check",
            "assurance.provider.drift-check",
            "assurance.multi-agent.corroboration-check",
            "assurance.hazard.create",
            "assurance.hazard.advance",
            "assurance.appeal.file",
            "assurance.appeal.assign-reviewer",
            "assurance.appeal.submit-evidence",
            "assurance.appeal.transition",
            "assurance.appeal.decide",
            "assurance.offboarding.request",
            "assurance.offboarding.advance",
            "assurance.engagement.check-scope",
            "assurance.accessibility.validate-render",
            "assurance.migration.reconcile",
            "assurance.migration.cutover",
            "assurance.migration.rollback",
            "assurance.session.create",
            "assurance.session.check-valid",
            "assurance.learning.ground-truth.declare-epoch",
            "assurance.learning.revalidation.complete",
            "assurance.learning.revalidation.backlog-status");
    private static final Set<String> CAPABILITIES = Set.of(
            "SA-01","SA-02","SA-03","SA-04","SA-05","SA-06","SA-07",
            "SA-08","SA-09","SA-10","SA-11","SA-12","SA-13","SA-14");

    private final Path workspaceRoot;
    private final AuthenticatedWorkflowIdentity identity;
    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
            .enable(SerializationFeature.INDENT_OUTPUT);
    private final SemanticAssuranceV2Reconstructor reconstructor = new SemanticAssuranceV2Reconstructor();

    SemanticAssuranceV2WorkflowService(Path workspaceRoot, AuthenticatedWorkflowIdentity identity) {
        if (workspaceRoot == null || identity == null) throw new IllegalArgumentException("V2_WORKFLOW_CONTEXT_REQUIRED");
        this.workspaceRoot = workspaceRoot.toAbsolutePath().normalize();
        this.identity = identity;
    }

    static boolean supports(String operation) {
        return OPERATIONS.contains(operation);
    }

    Map<String, Object> dispatch(String operation, JsonNode request) throws Exception {
        if (!supports(operation)) throw new IllegalArgumentException("SEMANTIC_V2_OPERATION_UNSUPPORTED:" + operation);
        if (request == null || !request.isObject()) throw new IllegalArgumentException("SEMANTIC_V2_REQUEST_OBJECT_REQUIRED");
        requireServerBoundContext(request);
        Map<String, Object> result = switch (operation) {
            case "semantic.applicability.evaluate" -> applicability(request);
            case "semantic.denominator.discover" -> denominator(request, "DISCOVERED");
            case "semantic.denominator.challenge" -> denominator(request, "CHALLENGED");
            case "semantic.denominator.lock" -> denominatorLock(request);
            case "semantic.reperformance.run" -> reperformance(request);
            case "semantic.authority.revalidate" -> authorityRevalidate(request);
            case "semantic.independence.assess" -> independenceAssess(request);
            case "semantic.freshness.invalidate" -> freshness(request, "INVALIDATED");
            case "semantic.freshness.reconstruct" -> freshness(request, "REASSESSMENT_REQUIRED");
            case "semantic.validator.requalify" -> requalify(request);
            case "assurance.otester.accept" -> independentAccept(request, "OTESTER");
            case "assurance.oaudit.accept" -> independentAccept(request, "OAUDIT");
            case "assurance.human-accept" -> humanAccept(request);
            case "assurance.final-candidate.reconstruct" -> finalCandidate(request);
            case "git.push" -> externalEffectNotImplemented(operation);
            case "deployment.verify-installed" -> verifyInstalled(request);
            case "assurance.evidence-graph.validate" -> evidenceGraphValidate(request);
            case "assurance.composition.compute" -> compositionCompute(request);
            case "assurance.certificate.issue" -> certificateIssue(request);
            case "assurance.revocation.issue" -> revocationIssue(request);
            case "assurance.revocation.check" -> revocationCheck(request);
            case "assurance.offline-trust-bundle.evaluate" -> offlineTrustBundleEvaluate(request);
            case "assurance.sod.record-stage" -> sodRecordStage(request);
            case "assurance.sod.check" -> sodCheck(request);
            case "assurance.four-eyes.record-approval" -> fourEyesRecordApproval(request);
            case "assurance.four-eyes.check" -> fourEyesCheck(request);
            case "assurance.delegation.grant" -> delegationGrant(request);
            case "assurance.delegation.check" -> delegationCheck(request);
            case "assurance.break-glass.invoke" -> breakGlassInvoke(request);
            case "assurance.break-glass.review" -> breakGlassReview(request);
            case "assurance.plugin.qualify" -> pluginQualify(request);
            case "assurance.external-integration.reconcile" -> externalIntegrationReconcile(request);
            case "assurance.learning.candidate.register" -> learningCandidateRegister(request);
            case "assurance.learning.validation.request" -> learningValidationRequest(request);
            case "assurance.learning.validation.pack.issue" -> learningValidationPackIssue(request);
            case "assurance.learning.validation.receipt.record" -> learningValidationReceiptRecord(request);
            case "assurance.learning.promotion.approve" -> learningPromotionApprove(request);
            case "assurance.learning.applied-lock.record" -> learningAppliedLockRecord(request);
            case "assurance.learning.completion-status.check" -> learningCompletionStatusCheck(request);
            case "assurance.oracle.multi-evaluate" -> oracleMultiEvaluate(request);
            case "assurance.corpus.integrity-check" -> corpusIntegrityCheck(request);
            case "assurance.validator.regression-qualify" -> validatorRegressionQualify(request);
            case "assurance.learning.stop-decision.compute" -> learningStopDecisionCompute(request);
            case "assurance.learning.scope-promotion.decide" -> learningScopePromotionDecide(request);
            case "assurance.learning.derived-lineage.dispose" -> learningDerivedLineageDispose(request);
            case "assurance.learning.decision-currentness.evaluate" -> learningDecisionCurrentnessEvaluate(request);
            case "assurance.learning.human-override.decide" -> learningHumanOverrideDecide(request);
            case "assurance.learning.human-override.trend-report" -> learningHumanOverrideTrendReport(request);
            case "assurance.learning.counterevidence.dispose" -> learningCounterevidenceDispose(request);
            case "assurance.learning.challenge-set-access.decide" -> learningChallengeSetAccessDecide(request);
            case "assurance.learning.evidence-observation.record" -> learningEvidenceObservationRecord(request);
            case "assurance.release.qualify" -> releaseQualify(request);
            case "assurance.validation.snapshot-verify" -> validationSnapshotVerify(request);
            case "assurance.validation.experiment-evaluate" -> validationExperimentEvaluate(request);
            case "assurance.learning.effectiveness.evaluate" -> learningEffectivenessEvaluate(request);
            case "assurance.strength-ceiling.compute" -> assuranceStrengthCeilingCompute(request);
            case "assurance.learning.data-residency.check" -> dataResidencyCheck(request);
            case "assurance.provider.drift-check" -> providerDriftCheck(request);
            case "assurance.multi-agent.corroboration-check" -> multiAgentCorroborationCheck(request);
            case "assurance.hazard.create" -> hazardCreate(request);
            case "assurance.hazard.advance" -> hazardAdvance(request);
            case "assurance.appeal.file" -> appealFile(request);
            case "assurance.appeal.assign-reviewer" -> appealAssignReviewer(request);
            case "assurance.appeal.submit-evidence" -> appealSubmitEvidence(request);
            case "assurance.appeal.transition" -> appealTransition(request);
            case "assurance.appeal.decide" -> appealDecide(request);
            case "assurance.offboarding.request" -> offboardingRequest(request);
            case "assurance.offboarding.advance" -> offboardingAdvance(request);
            case "assurance.engagement.check-scope" -> engagementCheckScope(request);
            case "assurance.accessibility.validate-render" -> accessibilityValidateRender(request);
            case "assurance.migration.reconcile" -> migrationReconcile(request);
            case "assurance.migration.cutover" -> migrationCutover(request);
            case "assurance.migration.rollback" -> migrationRollback(request);
            case "assurance.session.create" -> sessionCreate(request);
            case "assurance.session.check-valid" -> sessionCheckValid(request);
            case "assurance.learning.ground-truth.declare-epoch" -> groundTruthEpochDeclare(request);
            case "assurance.learning.revalidation.complete" -> learningRevalidationComplete(request);
            case "assurance.learning.revalidation.backlog-status" -> learningRevalidationBacklogStatus(request);
            default -> throw new IllegalStateException("SEMANTIC_V2_OPERATION_SWITCH_GAP:" + operation);
        };
        return envelope(operation, result);
    }

    private Map<String, Object> applicability(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        JsonNode capabilities = request.path("capabilities");
        if (!capabilities.isArray()) return failClosed("INPUT_REQUIRED", List.of("CAPABILITY_SET_REQUIRED"));
        List<Map<String, Object>> rows = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (JsonNode item : capabilities) {
            String id = requiredText(item, "capability_id");
            if (!CAPABILITIES.contains(id)) return failClosed("HOLD", List.of("UNKNOWN_CAPABILITY:" + id));
            if (!ids.add(id)) return failClosed("HOLD", List.of("DUPLICATE_CAPABILITY:" + id));
            String disposition = item.path("disposition").asText("INPUT_REQUIRED");
            String rationale = item.path("rationale").asText("");
            if ("NOT_APPLICABLE_JUSTIFIED".equals(disposition) && rationale.isBlank()) disposition = "INPUT_REQUIRED";
            if (!Set.of("APPLICABLE", "NOT_APPLICABLE_JUSTIFIED", "INPUT_REQUIRED", "HOLD").contains(disposition)) {
                return failClosed("HOLD", List.of("CAPABILITY_DISPOSITION_INVALID:" + id));
            }
            rows.add(Map.of("capability_id", id, "disposition", disposition, "rationale", rationale));
        }
        if (!ids.equals(CAPABILITIES)) {
            java.util.HashSet<String> missing = new java.util.HashSet<>(CAPABILITIES);
            missing.removeAll(ids);
            return failClosed("HOLD", List.of("CAPABILITY_DENOMINATOR_INCOMPLETE:" + String.join(",", missing)));
        }
        Map<String, Object> out = base("SEMANTIC_APPLICABILITY_SET", targetId);
        out.put("items", List.copyOf(rows));
        out.put("population_digest", digest(rows));
        out.put("decision", rows.stream().anyMatch(row -> Set.of("INPUT_REQUIRED", "HOLD").contains(row.get("disposition")))
                ? "HOLD" : "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> denominator(JsonNode request, String mode) {
        String targetId = requiredText(request, "target_id");
        JsonNode items = request.path("items");
        if (!items.isArray() || items.isEmpty()) return failClosed("INPUT_REQUIRED", List.of("DENOMINATOR_ITEMS_REQUIRED"));
        List<Map<String, Object>> normalized = new ArrayList<>();
        java.util.HashSet<String> ids = new java.util.HashSet<>();
        for (JsonNode item : items) {
            String id = requiredText(item, "item_id");
            if (!ids.add(id)) return failClosed("HOLD", List.of("DUPLICATE_DENOMINATOR_ID:" + id));
            String sha = requiredDigest(item, "item_sha256");
            String disposition = item.path("disposition").asText("INCLUDED");
            if (!Set.of("INCLUDED", "NOT_APPLICABLE_JUSTIFIED", "EXCLUDED_WITH_AUTHORITY", "SUPERSEDED_LEGACY").contains(disposition)) {
                return failClosed("HOLD", List.of("DENOMINATOR_DISPOSITION_INVALID:" + id));
            }
            if (("NOT_APPLICABLE_JUSTIFIED".equals(disposition) || "EXCLUDED_WITH_AUTHORITY".equals(disposition))
                    && !item.path("disposition_receipt_sha256").asText("").matches("[0-9a-f]{64}")) {
                return failClosed("HOLD", List.of("DENOMINATOR_DISPOSITION_EVIDENCE_REQUIRED:" + id));
            }
            normalized.add(Map.of("item_id", id, "item_sha256", sha, "disposition", disposition));
        }
        Map<String, Object> out = base("DENOMINATOR_" + mode, targetId);
        out.put("mode", mode);
        out.put("item_count", normalized.size());
        out.put("items", List.copyOf(normalized));
        out.put("population_digest", digest(normalized));
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> denominatorLock(JsonNode request) {
        Map<String, Object> out = denominator(request, "LOCK");
        if (!"NON_FINAL".equals(out.get("decision"))) return out;
        String epoch = request.path("epoch").asText("");
        if (epoch.isBlank()) return failClosed("INPUT_REQUIRED", List.of("DENOMINATOR_EPOCH_REQUIRED"));
        Map<String, Object> mutable = new LinkedHashMap<>(out);
        mutable.put("epoch", epoch);
        mutable.put("locked_at", Instant.now().toString());
        mutable.put("lock_is_final_authority", false);
        return immutable(mutable);
    }

    private Map<String, Object> reperformance(JsonNode request) throws Exception {
        Path subject = requiredPathWithin(request, "subject_path", "_authorized_target_root");
        String expected = requiredDigest(request, "subject_sha256");
        String actual = Hashing.file(subject);
        boolean same = expected.equals(actual);
        Map<String, Object> out = base("REPERFORMANCE_RESULT", requiredText(request, "target_id"));
        out.put("subject_path", Hashing.relative(workspaceRoot, subject));
        out.put("expected_sha256", expected);
        out.put("actual_sha256", actual);
        out.put("readback_equal", same);
        out.put("oracle_state", request.path("oracle_state").asText("NOT_RUN"));
        out.put("decision", same && "PASS".equals(request.path("oracle_state").asText()) ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    private Map<String, Object> authorityRevalidate(JsonNode request) {
        List<String> missing = new ArrayList<>();
        for (String field : List.of("principal_profile_sha256", "authority_epoch", "purpose", "effect_at")) {
            if (request.path(field).asText("").isBlank()) missing.add(field);
        }
        if (!request.path("authority_readback_receipt_sha256").asText("").matches("[0-9a-f]{64}")) {
            missing.add("AUTHORITY_READBACK_RECEIPT_REQUIRED");
        }
        Map<String, Object> out = base("AUTHORITY_REVALIDATION", requiredText(request, "target_id"));
        out.put("principal_profile_sha256", request.path("principal_profile_sha256").asText(""));
        out.put("authority_epoch", request.path("authority_epoch").asText(""));
        out.put("valid_at_effect", false);
        out.put("reasons", List.copyOf(missing));
        out.put("decision", "HOLD");
        out.put("limitation", "AUTHORITY_EFFECT_TIME_VERIFIER_NOT_WIRED");
        return immutable(out);
    }

    private Map<String, Object> independenceAssess(JsonNode request) {
        Map<String, Object> out = base("INDEPENDENCE_ASSESSMENT", requiredText(request, "target_id"));
        out.put("independent", false);
        out.put("decision", "HOLD");
        out.put("limitation", "INDEPENDENCE_PROFILE_CRYPTOGRAPHIC_VERIFIER_NOT_WIRED");
        out.put("self_attested_fields_ignored", true);
        return immutable(out);
    }

    private Map<String, Object> freshness(JsonNode request, String state) {
        Map<String, Object> out = base("FRESHNESS_EVENT", requiredText(request, "target_id"));
        out.put("state", state);
        out.put("trigger", request.path("trigger").asText("UNSPECIFIED"));
        out.put("affected_receipts", stringList(request.path("affected_receipts")));
        out.put("freshness_epoch", request.path("freshness_epoch").asText("UNASSIGNED"));
        out.put("decision", "HOLD");
        out.put("persistent_invalidation_applied", false);
        return immutable(out);
    }

    private Map<String, Object> requalify(JsonNode request) {
        Map<String, Object> out = base("VALIDATOR_REQUALIFICATION", requiredText(request, "target_id"));
        out.put("qualification_state", "NOT_QUALIFIED");
        out.put("decision", "HOLD");
        out.put("limitation", "QUALIFICATION_EXECUTION_AND_INDEPENDENT_REPERFORMANCE_NOT_WIRED");
        out.put("self_attested_metrics_ignored", true);
        return immutable(out);
    }

    private Map<String, Object> independentAccept(JsonNode request, String lane) {
        Map<String, Object> out = base("INDEPENDENT_" + lane + "_ACCEPTANCE", requiredText(request, "target_id"));
        out.put("lane", lane);
        out.put("decision", "HOLD");
        out.put("accepted", false);
        out.put("limitation", "INDEPENDENT_RECEIPT_SIGNATURE_PROFILE_AND_QUALIFICATION_VERIFIER_NOT_WIRED");
        out.put("caller_declared_independent_ignored", true);
        return immutable(out);
    }

    private Map<String, Object> humanAccept(JsonNode request) {
        Map<String, Object> out = base("HUMAN_ACCEPTANCE", requiredText(request, "target_id"));
        out.put("decision", "HOLD");
        out.put("accepted", false);
        out.put("limitation", "SIGNED_HUMAN_ACCEPTANCE_AUTHORITY_VERIFIER_NOT_WIRED");
        out.put("caller_declared_acceptance_ignored", true);
        return immutable(out);
    }

    private Map<String, Object> finalCandidate(JsonNode request) {
        List<Map<String, Object>> evidence = new ArrayList<>();
        if (request.path("evidence").isArray()) {
            for (JsonNode row : request.path("evidence")) evidence.add(mapper.convertValue(row, Map.class));
        }
        Map<String, String> epochs = new LinkedHashMap<>();
        for (String key : List.of("scope", "requirement", "denominator", "policy", "oracle", "validator_qualification", "authority")) {
            epochs.put(key, request.path("epochs").path(key).asText(""));
        }
        return reconstructor.reconstructFinalCandidate(
                requiredText(request, "target_id"),
                requiredDigest(request, "source_tree_sha256"),
                requiredDigest(request, "artifact_digest"),
                evidence,
                epochs,
                request.path("otester_receipt_sha256").asText(null),
                request.path("oaudit_receipt_sha256").asText(null),
                request.path("human_acceptance_receipt_sha256").asText(null),
                request.path("open_p0").asInt(0),
                request.path("open_p1").asInt(0));
    }

    private Map<String, Object> verifyInstalled(JsonNode request) throws Exception {
        if (request.path("_authorized_deployment_root").asText("").isBlank()) {
            return failClosed("BLOCKED", List.of("TARGET_BOUND_DEPLOYMENT_IDENTITY_NOT_AVAILABLE"));
        }
        Path artifact = requiredPathWithin(request, "verified_artifact_path", "_authorized_target_root");
        Path deployed = requiredPathWithin(request, "deployed_artifact_path", "_authorized_deployment_root");
        String verified = Hashing.file(artifact);
        String installed = Hashing.file(deployed);
        boolean same = verified.equals(installed);
        Map<String, Object> out = base("VERIFIED_TO_DEPLOYED", requiredText(request, "target_id"));
        out.put("verified_artifact_sha256", verified);
        out.put("deployed_artifact_sha256", installed);
        out.put("identity_equal", same);
        out.put("decision", same ? "NON_FINAL" : "FAIL");
        return immutable(out);
    }

    /**
     * evidence-graph-snapshot.v1.schema.json real structural validation: referential integrity
     * (every edge references a node that exists), edge digest binding (an edge's declared
     * source/target digest must match the current content_digest of the node it references, so an
     * edge can't outlive the node version it was computed against), DERIVED/AGGREGATED nodes must
     * carry an actual derivation edge, superseded_by_node_id must be backed by a real SUPERSEDES
     * edge from the successor, and the SUPERSEDES/DERIVES_FROM/AGGREGATES subgraph must be acyclic
     * (a supersession/derivation chain with a cycle has no well-defined "current" node). All
     * digests in the result are computed here, never trusted from the caller.
     */
    private Map<String, Object> evidenceGraphValidate(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String evidenceGraphId = requiredText(request, "evidence_graph_id");
        JsonNode nodesNode = request.path("nodes");
        JsonNode edgesNode = request.path("edges");
        if (!nodesNode.isArray() || nodesNode.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("EVIDENCE_GRAPH_NODES_REQUIRED"));
        }
        if (!edgesNode.isArray()) {
            return failClosed("INPUT_REQUIRED", List.of("EVIDENCE_GRAPH_EDGES_REQUIRED"));
        }

        List<String> violations = new ArrayList<>();
        java.util.LinkedHashSet<String> nodeIds = new java.util.LinkedHashSet<>();
        Map<String, String> nodeDigestById = new LinkedHashMap<>();
        Map<String, String> nodeOriginById = new LinkedHashMap<>();
        Map<String, String> nodeSupersededBy = new LinkedHashMap<>();
        List<Map<String, Object>> nodeDigestRows = new ArrayList<>();

        for (JsonNode node : nodesNode) {
            String nodeId = requiredText(node, "node_id");
            if (!nodeIds.add(nodeId)) { violations.add("DUPLICATE_NODE_ID:" + nodeId); continue; }
            String contentDigest = requiredDigest(node, "content_digest");
            String origin = node.path("origin_class").asText("");
            if (!Set.of("PRIMARY", "DERIVED", "AGGREGATED").contains(origin)) {
                violations.add("NODE_ORIGIN_CLASS_INVALID:" + nodeId);
            }
            if (!identity.tenantId().equals(node.path("tenant_id").asText(""))) {
                violations.add("NODE_TENANT_MISMATCH:" + nodeId);
            }
            String supersededBy = node.path("superseded_by_node_id").asText(null);
            if (supersededBy != null && !supersededBy.isBlank()) nodeSupersededBy.put(nodeId, supersededBy);
            nodeDigestById.put(nodeId, contentDigest);
            nodeOriginById.put(nodeId, origin);
            nodeDigestRows.add(Map.of("node_id", nodeId, "content_digest", contentDigest));
        }
        for (Map.Entry<String, String> entry : nodeSupersededBy.entrySet()) {
            if (!nodeIds.contains(entry.getValue())) {
                violations.add("SUPERSEDED_BY_UNKNOWN_NODE:" + entry.getKey() + "->" + entry.getValue());
            }
        }

        java.util.LinkedHashSet<String> edgeIds = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> edgeDigestRows = new ArrayList<>();
        List<String[]> dagEdges = new ArrayList<>();
        Map<String, java.util.Set<String>> derivationTargetsBySource = new LinkedHashMap<>();
        Map<String, java.util.Set<String>> supersedesTargetsBySource = new LinkedHashMap<>();

        for (JsonNode edge : edgesNode) {
            String edgeId = requiredText(edge, "edge_id");
            if (!edgeIds.add(edgeId)) { violations.add("DUPLICATE_EDGE_ID:" + edgeId); continue; }
            String edgeType = edge.path("edge_type").asText("");
            if (!Set.of("SUPERSEDES", "INVALIDATES", "REVOKES", "DERIVES_FROM", "AGGREGATES").contains(edgeType)) {
                violations.add("EDGE_TYPE_INVALID:" + edgeId);
                continue;
            }
            String source = requiredText(edge, "source_node_id");
            String target = requiredText(edge, "target_node_id");
            if (!nodeIds.contains(source)) { violations.add("EDGE_SOURCE_UNKNOWN:" + edgeId + ":" + source); continue; }
            if (!nodeIds.contains(target)) { violations.add("EDGE_TARGET_UNKNOWN:" + edgeId + ":" + target); continue; }
            String sourceDigest = requiredDigest(edge, "source_digest");
            String targetDigest = requiredDigest(edge, "target_digest");
            if (!sourceDigest.equals(nodeDigestById.get(source))) violations.add("EDGE_SOURCE_DIGEST_MISMATCH:" + edgeId);
            if (!targetDigest.equals(nodeDigestById.get(target))) violations.add("EDGE_TARGET_DIGEST_MISMATCH:" + edgeId);

            if (Set.of("SUPERSEDES", "DERIVES_FROM", "AGGREGATES").contains(edgeType)) {
                dagEdges.add(new String[] {source, target});
            }
            if ("DERIVES_FROM".equals(edgeType) || "AGGREGATES".equals(edgeType)) {
                derivationTargetsBySource.computeIfAbsent(source, key -> new java.util.LinkedHashSet<>()).add(target);
            }
            if ("SUPERSEDES".equals(edgeType)) {
                supersedesTargetsBySource.computeIfAbsent(source, key -> new java.util.LinkedHashSet<>()).add(target);
            }
            edgeDigestRows.add(Map.of("edge_id", edgeId, "source_node_id", source, "target_node_id", target, "edge_type", edgeType));
        }

        for (Map.Entry<String, String> entry : nodeSupersededBy.entrySet()) {
            java.util.Set<String> supersededByThatNode = supersedesTargetsBySource.get(entry.getValue());
            if (supersededByThatNode == null || !supersededByThatNode.contains(entry.getKey())) {
                violations.add("SUPERSEDED_BY_WITHOUT_MATCHING_SUPERSEDES_EDGE:" + entry.getKey());
            }
        }
        for (String nodeId : nodeIds) {
            String origin = nodeOriginById.get(nodeId);
            java.util.Set<String> derivesFrom = derivationTargetsBySource.get(nodeId);
            if (("DERIVED".equals(origin) || "AGGREGATED".equals(origin)) && (derivesFrom == null || derivesFrom.isEmpty())) {
                violations.add("DERIVED_NODE_WITHOUT_DERIVATION_EDGE:" + nodeId);
            }
        }
        if (hasCycle(nodeIds, dagEdges)) {
            violations.add("EVIDENCE_GRAPH_CYCLE_DETECTED");
        }

        nodeDigestRows.sort(java.util.Comparator.comparing(row -> (String) row.get("node_id")));
        edgeDigestRows.sort(java.util.Comparator.comparing(row -> (String) row.get("edge_id")));
        String nodePopulationDigest = digest(nodeDigestRows);
        String edgePopulationDigest = digest(edgeDigestRows);

        Map<String, Object> out = base("EVIDENCE_GRAPH_VALIDATION", targetId);
        out.put("evidence_graph_id", evidenceGraphId);
        out.put("node_count", nodeIds.size());
        out.put("edge_count", edgeIds.size());
        out.put("node_population_digest", nodePopulationDigest);
        out.put("edge_population_digest", edgePopulationDigest);
        out.put("graph_head_digest", digest(nodePopulationDigest + edgePopulationDigest));
        out.put("violations", List.copyOf(violations));
        out.put("decision", violations.isEmpty() ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    private boolean hasCycle(java.util.Set<String> nodeIds, List<String[]> edges) {
        Map<String, List<String>> adjacency = new LinkedHashMap<>();
        for (String[] edge : edges) adjacency.computeIfAbsent(edge[0], key -> new ArrayList<>()).add(edge[1]);
        java.util.Set<String> visited = new java.util.HashSet<>();
        java.util.Set<String> onStack = new java.util.HashSet<>();
        for (String nodeId : nodeIds) {
            if (!visited.contains(nodeId) && hasCycleFrom(nodeId, adjacency, visited, onStack)) return true;
        }
        return false;
    }

    private boolean hasCycleFrom(
            String nodeId, Map<String, List<String>> adjacency,
            java.util.Set<String> visited, java.util.Set<String> onStack) {
        visited.add(nodeId);
        onStack.add(nodeId);
        for (String next : adjacency.getOrDefault(nodeId, List.of())) {
            if (onStack.contains(next)) return true;
            if (!visited.contains(next) && hasCycleFrom(next, adjacency, visited, onStack)) return true;
        }
        onStack.remove(nodeId);
        return false;
    }

    /**
     * assurance-composition-snapshot.v1.schema.json real rollup: computes the parent decision
     * from real child inputs instead of validating a caller-declared one. A HARD-edge child that
     * FAILED/INVALIDATED/REVOKED forbids parent PASS (mapped to parent FAIL, since that state
     * needs remediation, not just a pending precondition); a HARD-edge child BLOCKED forbids
     * parent PASS but is less severe (mapped to parent BLOCKED); any child (any edge class) still
     * HOLD/NOT_RUN/INCONCLUSIVE also forbids a positive parent decision. Only when every HARD
     * child is PASS/NOT_APPLICABLE_JUSTIFIED and nothing is still outstanding does the parent
     * reach PASS. This mirrors, at the runtime level, the invariant Wave 6 already expressed
     * structurally in the schema's own allOf conditional.
     */
    private Map<String, Object> compositionCompute(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String compositionId = requiredText(request, "composition_id");
        JsonNode inputs = request.path("input_results");
        if (!inputs.isArray() || inputs.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("COMPOSITION_INPUT_RESULTS_REQUIRED"));
        }
        Set<String> validEdgeClasses = Set.of("HARD", "SOFT", "CONDITIONAL", "INFORMATIONAL");
        Set<String> validChildDecisions = Set.of(
                "PASS", "FAIL", "BLOCKED", "HOLD", "NOT_RUN", "INCONCLUSIVE",
                "INVALIDATED", "REVOKED", "NOT_APPLICABLE_JUSTIFIED");

        java.util.LinkedHashSet<String> seenSubjects = new java.util.LinkedHashSet<>();
        List<Map<String, Object>> normalizedRows = new ArrayList<>();
        List<String> ceilingReasons = new ArrayList<>();
        java.util.LinkedHashSet<String> hardBlockingDecisions = new java.util.LinkedHashSet<>();
        boolean anyOutstanding = false;

        for (JsonNode row : inputs) {
            String subjectId = requiredText(row, "subject_id");
            if (!seenSubjects.add(subjectId)) {
                return failClosed("HOLD", List.of("DUPLICATE_COMPOSITION_SUBJECT:" + subjectId));
            }
            String edgeClass = row.path("edge_propagation_class").asText("");
            if (!validEdgeClasses.contains(edgeClass)) {
                return failClosed("HOLD", List.of("COMPOSITION_EDGE_CLASS_INVALID:" + subjectId));
            }
            String childDecision = row.path("child_decision").asText("");
            if (!validChildDecisions.contains(childDecision)) {
                return failClosed("HOLD", List.of("COMPOSITION_CHILD_DECISION_INVALID:" + subjectId));
            }
            String resultDigest = requiredDigest(row, "result_digest");
            if ("NOT_APPLICABLE_JUSTIFIED".equals(childDecision)
                    && !row.path("applicability_proof_digest").asText("").matches("[0-9a-f]{64}")) {
                return failClosed("HOLD", List.of("COMPOSITION_APPLICABILITY_PROOF_REQUIRED:" + subjectId));
            }

            if ("HARD".equals(edgeClass) && Set.of("FAIL", "BLOCKED", "INVALIDATED", "REVOKED").contains(childDecision)) {
                hardBlockingDecisions.add(childDecision);
                ceilingReasons.add("HARD_EDGE_CHILD_" + childDecision + ":" + subjectId);
            }
            if (Set.of("HOLD", "NOT_RUN", "INCONCLUSIVE").contains(childDecision)) {
                anyOutstanding = true;
                ceilingReasons.add("CHILD_" + childDecision + ":" + subjectId);
            }
            normalizedRows.add(Map.of("subject_id", subjectId, "result_digest", resultDigest));
        }

        String decision;
        if (hardBlockingDecisions.contains("FAIL") || hardBlockingDecisions.contains("INVALIDATED")
                || hardBlockingDecisions.contains("REVOKED")) {
            decision = "FAIL";
        } else if (hardBlockingDecisions.contains("BLOCKED")) {
            decision = "BLOCKED";
        } else if (anyOutstanding) {
            decision = "HOLD";
        } else {
            decision = "PASS";
        }

        normalizedRows.sort(java.util.Comparator.comparing(row -> (String) row.get("subject_id")));
        Map<String, Object> out = base("ASSURANCE_COMPOSITION_SNAPSHOT", targetId);
        out.put("composition_id", compositionId);
        out.put("subject_population_digest", digest(normalizedRows));
        out.put("input_result_count", normalizedRows.size());
        out.put("decision", decision);
        out.put("assurance_strength", "SELF_VALIDATION");
        out.put("currentness_state", "UNKNOWN");
        out.put("qualification_state", "NOT_QUALIFIED");
        out.put("independence_state", "SELF_VALIDATION");
        out.put("uncertainty_state", "UNBOUNDED");
        out.put("ceiling_reasons", List.copyOf(ceilingReasons));
        out.put("limitation", "COMPOSITION_ROLLUP_ONLY_NO_INDEPENDENT_CURRENTNESS_VERIFIER_WIRED");
        return immutable(out);
    }

    /**
     * assurance-certificate.v1.schema.json real issuance path: real Ed25519 signature over the
     * real canonical certificate payload (LocalReceiptCrypto, the same primitive used for
     * approval receipts), gated by the composition decision passed to it and an honestly UNKNOWN
     * currentness_state_at_issue -- no currentness verifier is wired anywhere in this codebase
     * yet, so this can never claim CURRENT and, per the schema's own conditional, therefore can
     * never issue a positive PASS/PASS_WITH_LIMITATIONS certificate. A composition decision other
     * than PASS issues a BLOCKED certificate; a PASS composition still only reaches HOLD here
     * (the composition passed, but currentness itself is not yet certifiable). The signing key is
     * generated fresh per call (issuer_key_id EPHEMERAL_SELF_VALIDATION_KEY) since this is a
     * self-validation-nonfinal candidate path, not the real trust-rooted certificate authority.
     */
    private Map<String, Object> certificateIssue(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String certificateId = requiredText(request, "certificate_id");
        String subjectId = requiredText(request, "subject_id");
        String subjectDigest = requiredDigest(request, "subject_digest");
        String productVersion = requiredText(request, "product_version");
        String targetManifestDigest = requiredDigest(request, "target_manifest_digest");
        String requirementEpoch = requiredText(request, "requirement_epoch");
        String compositionSnapshotDigest = requiredDigest(request, "composition_snapshot_digest");
        String finalLockDigest = requiredDigest(request, "final_lock_digest");
        String assuranceTier = requiredText(request, "assurance_tier");
        if (!Set.of("TIER_1_BASIC", "TIER_2_STANDARD", "TIER_3_HIGH", "TIER_4_CRITICAL").contains(assuranceTier)) {
            return failClosed("HOLD", List.of("CERTIFICATE_ASSURANCE_TIER_INVALID"));
        }
        String compositionDecision = requiredText(request, "composition_decision");
        if (!Set.of("PASS", "FAIL", "BLOCKED", "HOLD", "NOT_RUN", "INCONCLUSIVE").contains(compositionDecision)) {
            return failClosed("HOLD", List.of("CERTIFICATE_COMPOSITION_DECISION_INVALID"));
        }
        String verifierIdentityRef = requiredText(request, "verifier_identity_ref");

        List<String> limitations = new ArrayList<>(List.of("CERTIFICATE_CURRENTNESS_VERIFIER_NOT_WIRED"));
        String decision;
        if ("PASS".equals(compositionDecision)) {
            decision = "HOLD";
            limitations.add("COMPOSITION_PASS_BUT_CURRENTNESS_UNKNOWN");
        } else {
            decision = "BLOCKED";
            limitations.add("COMPOSITION_DECISION_NOT_PASS:" + compositionDecision);
        }

        String issuedAt = Instant.now().toString();
        java.security.KeyPair keyPair = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.generate();
        Map<String, Object> unsigned = new LinkedHashMap<>();
        unsigned.put("contract", "ONSURE_ASSURANCE_CERTIFICATE_V1");
        unsigned.put("certificate_id", certificateId);
        unsigned.put("certificate_version", "1");
        unsigned.put("subject_id", subjectId);
        unsigned.put("subject_digest", subjectDigest);
        unsigned.put("product_version", productVersion);
        unsigned.put("target_manifest_digest", targetManifestDigest);
        unsigned.put("requirement_epoch", requirementEpoch);
        unsigned.put("composition_snapshot_digest", compositionSnapshotDigest);
        unsigned.put("final_lock_digest", finalLockDigest);
        unsigned.put("assurance_tier", assuranceTier);
        unsigned.put("decision", decision);
        unsigned.put("currentness_state_at_issue", "UNKNOWN");
        unsigned.put("issued_at", issuedAt);
        unsigned.put("not_before", issuedAt);
        unsigned.put("revalidation_due_at", null);
        unsigned.put("expires_at", null);
        unsigned.put("verifier_identity_ref", verifierIdentityRef);
        unsigned.put("revocation_reference", null);
        unsigned.put("issuer_key_id", "EPHEMERAL_SELF_VALIDATION_KEY");
        unsigned.put("issuer_public_key_der_base64", java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        unsigned.put("independent_verification_summary_digest", digest("NO_INDEPENDENT_VERIFICATION_PERFORMED"));
        unsigned.put("limitation_summary", List.copyOf(limitations));
        unsigned.put("exclusion_summary", List.of());
        unsigned.put("target_id", targetId);
        unsigned.put("tenant_id", identity.tenantId());
        unsigned.put("actor_id", identity.actorId());
        unsigned.put("self_validation_nonfinal", true);
        unsigned.put("final_claim_allowed", false);

        // Every field above this line is part of the signed payload -- signature is added last and
        // is, by construction (LocalReceiptCrypto.canonicalPayload strips only "signature"), the
        // only field a verifier excludes when recomputing the same canonical bytes.
        String signatureValue = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.sign(unsigned, keyPair.getPrivate());
        Map<String, Object> out = new LinkedHashMap<>(unsigned);
        out.put("signature", Map.of("algorithm", "Ed25519", "signature", signatureValue));
        return immutable(out);
    }

    /**
     * assurance-revocation-event.candidate.v2.schema.json real issuance: real Ed25519 signature
     * (ephemeral key, same self-validation-nonfinal boundary as certificateIssue -- this is not
     * the trust-rooted production revocation authority), real revocation_sha256, and durably
     * persisted via RevocationLedger so a later assurance.revocation.check can actually find it.
     * Persisting the fact of a revocation is not a positive assurance claim, so unlike composition/
     * certificate this can reach NON_FINAL on success -- there is nothing here to fail closed on
     * except malformed input or a colliding revocation_id.
     */
    private Map<String, Object> revocationIssue(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String revocationId = requiredText(request, "revocation_id");

        JsonNode subjectNode = request.path("subject");
        String subjectType = subjectNode.path("subject_type").asText("");
        if (!Set.of("RECEIPT", "CERTIFICATE", "QUALIFICATION", "SELECTOR", "DEPLOYMENT",
                "AUTHORITY_PROFILE", "VALIDATOR", "TARGET").contains(subjectType)) {
            return failClosed("HOLD", List.of("REVOCATION_SUBJECT_TYPE_INVALID"));
        }
        String subjectId = requiredText(subjectNode, "subject_id");
        String subjectSha256 = requiredDigest(subjectNode, "subject_sha256");

        String reason = requiredText(request, "reason");
        String severity = requiredText(request, "severity");
        if (!Set.of("CRITICAL", "HIGH", "MEDIUM", "LOW").contains(severity)) {
            return failClosed("HOLD", List.of("REVOCATION_SEVERITY_INVALID"));
        }
        JsonNode triggeringEvidenceNode = request.path("triggering_evidence");
        if (!triggeringEvidenceNode.isArray() || triggeringEvidenceNode.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("REVOCATION_TRIGGERING_EVIDENCE_REQUIRED"));
        }
        List<Map<String, Object>> triggeringEvidence = new ArrayList<>();
        for (JsonNode item : triggeringEvidenceNode) {
            triggeringEvidence.add(Map.of("id", requiredText(item, "id"), "sha256", requiredDigest(item, "sha256")));
        }

        JsonNode authorityNode = request.path("authority");
        String principalProfileSha256 = requiredDigest(authorityNode, "principal_profile_sha256");
        String authorityEpoch = requiredText(authorityNode, "authority_epoch");

        JsonNode scopeNode = request.path("propagation_scope");
        String scopeType = scopeNode.path("scope_type").asText("");
        if (!Set.of("GLOBAL", "TENANT", "TARGET", "REGION", "SUBJECT_GRAPH").contains(scopeType)) {
            return failClosed("HOLD", List.of("REVOCATION_PROPAGATION_SCOPE_INVALID"));
        }
        String scopeDigest = requiredDigest(scopeNode, "scope_digest");
        String revocationEpoch = requiredText(request, "revocation_epoch");
        String supersedes = request.path("supersedes_revocation_sha256").asText(null);
        if (supersedes != null && !supersedes.matches("[0-9a-f]{64}")) {
            return failClosed("HOLD", List.of("REVOCATION_SUPERSEDES_DIGEST_INVALID"));
        }

        String issuedAt = Instant.now().toString();
        String effectiveAt = request.path("effective_at").asText(issuedAt);

        Map<String, Object> unsigned = new LinkedHashMap<>();
        unsigned.put("contract", "ONSURE_ASSURANCE_REVOCATION_EVENT_V2_CANDIDATE");
        unsigned.put("revocation_id", revocationId);
        unsigned.put("subject", Map.of("subject_type", subjectType, "subject_id", subjectId, "subject_sha256", subjectSha256));
        unsigned.put("reason", reason);
        unsigned.put("severity", severity);
        unsigned.put("triggering_evidence", List.copyOf(triggeringEvidence));
        unsigned.put("authority", Map.of(
                "principal_profile_sha256", principalProfileSha256, "authority_epoch", authorityEpoch,
                "purpose", "ASSURANCE_REVOCATION"));
        unsigned.put("issued_at", issuedAt);
        unsigned.put("effective_at", effectiveAt);
        unsigned.put("propagation_scope", Map.of("scope_type", scopeType, "scope_digest", scopeDigest));
        unsigned.put("revocation_epoch", revocationEpoch);
        unsigned.put("supersedes_revocation_sha256", supersedes);
        unsigned.put("revocation_sha256", digest(unsigned));

        java.security.KeyPair keyPair = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.generate();
        String signatureValue = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.sign(unsigned, keyPair.getPrivate());
        Map<String, Object> event = new LinkedHashMap<>(unsigned);
        event.put("signature", Map.of(
                "key_id", "EPHEMERAL_SELF_VALIDATION_KEY", "algorithm", "Ed25519", "signature", signatureValue));

        try {
            new RevocationLedger(workspaceRoot.resolve(".onsure/assurance/revocations")).issue(event);
        } catch (IllegalArgumentException duplicate) {
            return failClosed("HOLD", List.of(duplicate.getMessage()));
        }

        Map<String, Object> out = base("ASSURANCE_REVOCATION_ISSUED", targetId);
        out.put("revocation_id", revocationId);
        out.put("revocation_sha256", unsigned.get("revocation_sha256"));
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * Looks up whether a subject currently has an active (not superseded by a later event)
     * revocation on record. Fail-closed in the sense that matters here: an unreadable/missing
     * ledger reads as CLEAR only because there is genuinely nothing recorded, never because a read
     * failure was swallowed -- forSubject()/all() propagate real I/O errors.
     */
    private Map<String, Object> revocationCheck(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        JsonNode subjectNode = request.path("subject");
        String subjectType = subjectNode.path("subject_type").asText("");
        String subjectId = subjectNode.path("subject_id").asText("");
        if (subjectType.isBlank() || subjectId.isBlank()) {
            return failClosed("INPUT_REQUIRED", List.of("REVOCATION_CHECK_SUBJECT_REQUIRED"));
        }

        RevocationLedger ledger = new RevocationLedger(workspaceRoot.resolve(".onsure/assurance/revocations"));
        List<Map<String, Object>> matches = ledger.forSubject(subjectType, subjectId);
        java.util.Set<String> supersededDigests = new java.util.HashSet<>();
        for (Map<String, Object> event : ledger.all()) {
            Object supersedes = event.get("supersedes_revocation_sha256");
            if (supersedes instanceof String value) supersededDigests.add(value);
        }
        List<Map<String, Object>> active = matches.stream()
                .filter(event -> !supersededDigests.contains(event.get("revocation_sha256")))
                .sorted(java.util.Comparator.comparing(event -> String.valueOf(event.get("issued_at"))))
                .toList();

        Map<String, Object> out = base("ASSURANCE_REVOCATION_CHECK", targetId);
        out.put("subject_type", subjectType);
        out.put("subject_id", subjectId);
        out.put("decision", "NON_FINAL");
        if (active.isEmpty()) {
            out.put("revocation_state", "CLEAR");
        } else {
            Map<String, Object> mostRecent = active.get(active.size() - 1);
            out.put("revocation_state", "REVOKED");
            out.put("revocation_id", mostRecent.get("revocation_id"));
            out.put("severity", mostRecent.get("severity"));
            out.put("reason", mostRecent.get("reason"));
        }
        return immutable(out);
    }

    /**
     * offline-trust-bundle.v1.schema.json real degradation computation (doc 31 SS8): offline_status
     * is computed from real elapsed time since last_online_sync_at against grace_period_seconds,
     * not accepted as a caller claim. A single local OS clock is never trusted enough to certify
     * freshness on its own (source LOCAL_OS_CLOCK_ONLY caps trust_level at LOW/UNTRUSTED per the
     * schema's own conditional); UNTRUSTED time forces OFFLINE_BLOCKED unconditionally regardless
     * of the elapsed-time arithmetic, since an untrusted clock can't even certify how much time has
     * actually elapsed.
     */
    private Map<String, Object> offlineTrustBundleEvaluate(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String bundleId = requiredText(request, "bundle_id");
        JsonNode rootKeyIdsNode = request.path("trusted_root_key_ids");
        if (!rootKeyIdsNode.isArray() || rootKeyIdsNode.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("OFFLINE_BUNDLE_TRUSTED_ROOT_KEY_IDS_REQUIRED"));
        }
        List<String> rootKeyIds = stringList(rootKeyIdsNode);
        String keyRegistrySnapshotDigest = requiredDigest(request, "key_registry_snapshot_digest");
        String policySnapshotDigest = requiredDigest(request, "policy_snapshot_digest");
        String validatorQualificationSnapshotDigest = requiredDigest(request, "validator_qualification_snapshot_digest");
        String revocationSnapshotDigest = requiredDigest(request, "revocation_snapshot_digest");

        JsonNode timeEvidenceNode = request.path("trusted_time_evidence");
        String source = timeEvidenceNode.path("source").asText("");
        if (!Set.of("TPM", "SECURE_CLOCK", "ENTERPRISE_TIME_AUTHORITY", "LOCAL_OS_CLOCK_ONLY").contains(source)) {
            return failClosed("HOLD", List.of("OFFLINE_BUNDLE_TIME_SOURCE_INVALID"));
        }
        String trustLevel = timeEvidenceNode.path("trust_level").asText("");
        if (!Set.of("HIGH", "MEDIUM", "LOW", "UNTRUSTED").contains(trustLevel)) {
            return failClosed("HOLD", List.of("OFFLINE_BUNDLE_TIME_TRUST_LEVEL_INVALID"));
        }
        if ("LOCAL_OS_CLOCK_ONLY".equals(source) && !Set.of("LOW", "UNTRUSTED").contains(trustLevel)) {
            return failClosed("HOLD", List.of("OFFLINE_BUNDLE_LOCAL_CLOCK_TRUST_LEVEL_TOO_HIGH"));
        }
        Instant observedAt = Instant.parse(requiredText(timeEvidenceNode, "observed_at"));

        int gracePeriodSeconds = request.path("grace_period_seconds").asInt(-1);
        if (gracePeriodSeconds < 0) return failClosed("HOLD", List.of("OFFLINE_BUNDLE_GRACE_PERIOD_INVALID"));
        String lastOnlineSyncAt = request.path("last_online_sync_at").asText(null);

        String offlineStatus;
        if ("UNTRUSTED".equals(trustLevel)) {
            offlineStatus = "OFFLINE_BLOCKED";
        } else if (lastOnlineSyncAt == null || lastOnlineSyncAt.isBlank()) {
            offlineStatus = "OFFLINE_BLOCKED";
        } else {
            long elapsedSeconds = java.time.Duration.between(Instant.parse(lastOnlineSyncAt), observedAt).getSeconds();
            if (elapsedSeconds < 0) {
                return failClosed("HOLD", List.of("OFFLINE_BUNDLE_LAST_SYNC_IN_FUTURE"));
            } else if (elapsedSeconds <= gracePeriodSeconds) {
                offlineStatus = "OFFLINE_CURRENT_WITHIN_GRACE";
            } else if (elapsedSeconds <= gracePeriodSeconds * 2L) {
                offlineStatus = "OFFLINE_REVALIDATION_DUE";
            } else if (elapsedSeconds <= gracePeriodSeconds * 4L) {
                offlineStatus = "OFFLINE_STATUS_UNCERTAIN";
            } else {
                offlineStatus = "OFFLINE_BLOCKED";
            }
        }

        String generatedAt = Instant.now().toString();
        Map<String, Object> unsigned = new LinkedHashMap<>();
        unsigned.put("contract", "ONSURE_OFFLINE_TRUST_BUNDLE_V1");
        unsigned.put("bundle_id", bundleId);
        unsigned.put("trusted_root_key_ids", rootKeyIds);
        unsigned.put("key_registry_snapshot_digest", keyRegistrySnapshotDigest);
        unsigned.put("policy_snapshot_digest", policySnapshotDigest);
        unsigned.put("validator_qualification_snapshot_digest", validatorQualificationSnapshotDigest);
        unsigned.put("revocation_snapshot_digest", revocationSnapshotDigest);
        unsigned.put("trusted_time_evidence", Map.of("source", source, "observed_at", observedAt.toString(), "trust_level", trustLevel));
        unsigned.put("generated_at", generatedAt);
        unsigned.put("expires_at", request.path("expires_at").asText(generatedAt));
        unsigned.put("grace_period_seconds", gracePeriodSeconds);
        unsigned.put("last_online_sync_at", lastOnlineSyncAt);
        unsigned.put("offline_status", offlineStatus);
        unsigned.put("target_id", targetId);
        unsigned.put("decision", "OFFLINE_CURRENT_WITHIN_GRACE".equals(offlineStatus) ? "NON_FINAL" : "HOLD");
        unsigned.put("self_validation_nonfinal", true);
        unsigned.put("final_claim_allowed", false);
        unsigned.put("bundle_sha256", digest(unsigned));

        // Every field above this line is part of the signed payload, matching certificateIssue's
        // fix: signature is added last so a verifier excludes only the signature field itself when
        // recomputing the same canonical bytes.
        java.security.KeyPair keyPair = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.generate();
        String signatureValue = kr.co.oruda.onsure.assurance.LocalReceiptCrypto.sign(unsigned, keyPair.getPrivate());
        Map<String, Object> out = new LinkedHashMap<>(unsigned);
        out.put("bundle_signature", Map.of(
                "key_id", "EPHEMERAL_SELF_VALIDATION_KEY", "algorithm", "Ed25519", "signature", signatureValue));
        out.put("issuer_public_key_der_base64", java.util.Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        return immutable(out);
    }

    /**
     * FR-COM-013 real enforcement: records that the caller performed {@code stage} (DEVELOP/
     * VERIFY/ACCEPT) for an ImprovementRequest. Under a REGULATED industry_class with
     * sod_enforcement ENFORCED, an actor who already recorded a different stage for the same
     * request is rejected outright (SecurityException, before anything is written) rather than
     * merely flagged after the fact -- "동일 사용자가... 모두 수행할 수 없다" means genuinely
     * cannot, not just logged. STANDARD/ADVISORY still records the conflict but does not block it.
     */
    private Map<String, Object> sodRecordStage(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String improvementRequestId = requiredText(request, "improvement_request_id");
        String stage = requiredText(request, "stage");
        if (!SeparationOfDutiesLedger.STAGES.contains(stage)) {
            return failClosed("HOLD", List.of("SOD_STAGE_INVALID"));
        }

        JsonNode policyNode = request.path("policy_profile");
        String industryClass = policyNode.path("industry_class").asText("");
        if (!Set.of("STANDARD", "REGULATED_FINANCIAL", "REGULATED_HEALTHCARE",
                "REGULATED_GOVERNMENT", "REGULATED_OTHER").contains(industryClass)) {
            return failClosed("HOLD", List.of("SOD_POLICY_INDUSTRY_CLASS_INVALID"));
        }
        String sodEnforcement = policyNode.path("sod_enforcement").asText("");
        if (!Set.of("ADVISORY", "ENFORCED").contains(sodEnforcement)) {
            return failClosed("HOLD", List.of("SOD_POLICY_ENFORCEMENT_INVALID"));
        }
        if ("STANDARD".equals(industryClass) && "ENFORCED".equals(sodEnforcement)) {
            return failClosed("HOLD", List.of("SOD_STANDARD_INDUSTRY_CANNOT_ENFORCE"));
        }
        boolean enforced = "ENFORCED".equals(sodEnforcement);

        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(workspaceRoot.resolve(".onsure/assurance/sod"));
        SeparationOfDutiesLedger.Result result = ledger.recordStage(improvementRequestId, stage, identity.actorId(), enforced);

        Map<String, Object> out = base("SOD_STAGE_RECORD", targetId);
        out.put("improvement_request_id", improvementRequestId);
        out.put("stage", stage);
        out.put("industry_class", industryClass);
        out.put("sod_enforcement", sodEnforcement);
        out.put("advisory_violation", result.outcome() == SeparationOfDutiesLedger.Outcome.ADVISORY_VIOLATION);
        out.put("conflicting_stage", result.conflictingStage());
        out.put("stage_count", result.stages().size());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> sodCheck(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String improvementRequestId = requiredText(request, "improvement_request_id");
        SeparationOfDutiesLedger ledger = new SeparationOfDutiesLedger(workspaceRoot.resolve(".onsure/assurance/sod"));
        List<SeparationOfDutiesLedger.StageRecord> stages = ledger.stagesFor(improvementRequestId);

        Map<String, java.util.Set<String>> stagesByActor = new LinkedHashMap<>();
        for (SeparationOfDutiesLedger.StageRecord record : stages) {
            stagesByActor.computeIfAbsent(record.actorId(), key -> new java.util.LinkedHashSet<>()).add(record.stage());
        }
        List<String> actorsWithMultipleStages = stagesByActor.entrySet().stream()
                .filter(entry -> entry.getValue().size() > 1)
                .map(Map.Entry::getKey)
                .sorted()
                .toList();

        Map<String, Object> out = base("SOD_CHECK", targetId);
        out.put("improvement_request_id", improvementRequestId);
        out.put("recorded_stage_count", stages.size());
        out.put("actors_with_multiple_stages", actorsWithMultipleStages);
        out.put("clean", actorsWithMultipleStages.isEmpty());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * policy-profile.v1.schema.json four_eyes_required real enforcement: records that the caller
     * approved {@code approval_subject_id}, and reports whether the required number of genuinely
     * distinct approvers (FourEyesLedger.REQUIRED_DISTINCT_APPROVERS) has now been reached. The
     * same actor approving the same subject twice is rejected -- it would silently defeat the
     * control -- so an actor who wants to "recheck" gets FOUR_EYES_SAME_ACTOR_CANNOT_COUNT_TWICE,
     * not a quiet no-op success.
     */
    private Map<String, Object> fourEyesRecordApproval(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String approvalSubjectId = requiredText(request, "approval_subject_id");
        boolean fourEyesRequired = request.path("policy_profile").path("four_eyes_required").asBoolean(false);
        if (!fourEyesRequired) {
            return failClosed("HOLD", List.of("FOUR_EYES_NOT_REQUIRED_BY_POLICY"));
        }

        FourEyesLedger ledger = new FourEyesLedger(workspaceRoot.resolve(".onsure/assurance/four-eyes"));
        FourEyesLedger.Result result = ledger.recordApproval(approvalSubjectId, identity.actorId());

        Map<String, Object> out = base("FOUR_EYES_APPROVAL_RECORD", targetId);
        out.put("approval_subject_id", approvalSubjectId);
        out.put("distinct_approver_count", result.approvals().stream().map(FourEyesLedger.ApprovalRecord::actorId).distinct().count());
        out.put("required_distinct_approvers", FourEyesLedger.REQUIRED_DISTINCT_APPROVERS);
        out.put("satisfied", result.satisfied());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> fourEyesCheck(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String approvalSubjectId = requiredText(request, "approval_subject_id");
        FourEyesLedger ledger = new FourEyesLedger(workspaceRoot.resolve(".onsure/assurance/four-eyes"));
        FourEyesLedger.Result result = ledger.approvalsFor(approvalSubjectId);

        Map<String, Object> out = base("FOUR_EYES_CHECK", targetId);
        out.put("approval_subject_id", approvalSubjectId);
        out.put("approver_actor_ids", result.approvals().stream().map(FourEyesLedger.ApprovalRecord::actorId).distinct().toList());
        out.put("required_distinct_approvers", FourEyesLedger.REQUIRED_DISTINCT_APPROVERS);
        out.put("satisfied", result.satisfied());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * Real bounded-time role delegation: the delegator must currently hold the role being
     * delegated (identity.roles(), server-authenticated -- never a caller-declared claim), the
     * expiry must be strictly in the future, and self-delegation is rejected. Grants are checked
     * for real expiry at read time by DelegationLedger, never trusted as a caller-declared "still
     * active" flag.
     */
    private Map<String, Object> delegationGrant(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String delegationId = requiredText(request, "delegation_id");
        String delegateActorId = requiredText(request, "delegate_actor_id");
        String role = requiredText(request, "role");
        if (!Set.of("VIEWER", "OPERATOR", "APPROVER", "AUDITOR", "ADMIN").contains(role)) {
            return failClosed("HOLD", List.of("DELEGATION_ROLE_INVALID"));
        }
        if (identity.roles().stream().noneMatch(value -> value.name().equals(role))) {
            throw new SecurityException("DELEGATION_DELEGATOR_DOES_NOT_HOLD_ROLE:" + role);
        }
        String justification = requiredText(request, "justification");
        Instant expiresAt;
        try {
            expiresAt = Instant.parse(requiredText(request, "expires_at"));
        } catch (Exception malformed) {
            return failClosed("HOLD", List.of("DELEGATION_EXPIRY_MALFORMED"));
        }

        DelegationLedger ledger = new DelegationLedger(workspaceRoot.resolve(".onsure/assurance/delegations"));
        DelegationLedger.Grant grant;
        try {
            grant = ledger.grant(delegationId, identity.actorId(), delegateActorId, role, expiresAt, justification, Instant.now());
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }

        Map<String, Object> out = base("DELEGATION_GRANT", targetId);
        out.put("delegation_id", grant.delegationId());
        out.put("delegate_actor_id", grant.delegateActorId());
        out.put("role", grant.role());
        out.put("expires_at", grant.expiresAt());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> delegationCheck(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String delegateActorId = requiredText(request, "delegate_actor_id");
        String role = requiredText(request, "role");
        DelegationLedger ledger = new DelegationLedger(workspaceRoot.resolve(".onsure/assurance/delegations"));
        List<DelegationLedger.Grant> active = ledger.activeGrantsFor(delegateActorId, role, Instant.now());

        Map<String, Object> out = base("DELEGATION_CHECK", targetId);
        out.put("delegate_actor_id", delegateActorId);
        out.put("role", role);
        out.put("active", !active.isEmpty());
        out.put("active_grant_count", active.size());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * Emergency override, always created with review_required permanently true -- there is no
     * path here that creates an already-reviewed event -- so every invocation is guaranteed
     * discoverable as outstanding until a genuinely distinct reviewer closes it.
     */
    private Map<String, Object> breakGlassInvoke(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String eventId = requiredText(request, "event_id");
        String justification = requiredText(request, "justification");

        BreakGlassLedger ledger = new BreakGlassLedger(workspaceRoot.resolve(".onsure/assurance/break-glass"));
        BreakGlassLedger.Event event;
        try {
            event = ledger.invoke(eventId, identity.actorId(), justification, Instant.now());
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }

        Map<String, Object> out = base("BREAK_GLASS_EVENT", targetId);
        out.put("event_id", event.eventId());
        out.put("invoker_actor_id", event.invokerActorId());
        out.put("review_required", event.reviewRequired());
        out.put("review_completed", event.reviewCompleted());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> breakGlassReview(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String eventId = requiredText(request, "event_id");
        String reviewNotes = requiredText(request, "review_notes");

        BreakGlassLedger ledger = new BreakGlassLedger(workspaceRoot.resolve(".onsure/assurance/break-glass"));
        BreakGlassLedger.Event reviewed;
        try {
            reviewed = ledger.recordReview(eventId, identity.actorId(), reviewNotes, Instant.now());
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }

        Map<String, Object> out = base("BREAK_GLASS_REVIEW", targetId);
        out.put("event_id", reviewed.eventId());
        out.put("reviewer_actor_id", reviewed.reviewerActorId());
        out.put("review_completed", reviewed.reviewCompleted());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * plugin-manifest.v1.schema.json SS5 real qualification: an unsigned or revoked publisher
     * forbids qualification outright; an undeclared privilege (one of required_privileges with no
     * matching access_declarations entry) blocks qualification rather than silently passing
     * through; and, via PluginQualificationLedger, a plugin previously QUALIFIED whose
     * artifact_digest has since changed drops to QUALIFICATION_PENDING rather than silently
     * carrying the old QUALIFIED state forward onto different bytes.
     */
    private Map<String, Object> pluginQualify(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String pluginId = requiredText(request, "plugin_id");
        String pluginVersion = requiredText(request, "plugin_version");
        boolean publisherSignatureValid = request.path("publisher_signature_valid").asBoolean(false);
        boolean publisherRevoked = request.path("publisher_revoked").asBoolean(false);
        String artifactDigest = requiredDigest(request, "artifact_digest");

        JsonNode accessNode = request.path("access_declarations");
        String filesystem = accessNode.path("filesystem").asText("");
        String network = accessNode.path("network").asText("");
        if (!Set.of("NONE", "READ_ONLY_SANDBOX", "READ_WRITE_SANDBOX").contains(filesystem)) {
            return failClosed("HOLD", List.of("PLUGIN_ACCESS_FILESYSTEM_INVALID"));
        }
        if (!Set.of("NONE", "EGRESS_ALLOWLIST_ONLY", "UNRESTRICTED").contains(network)) {
            return failClosed("HOLD", List.of("PLUGIN_ACCESS_NETWORK_INVALID"));
        }
        List<String> toolInvocation = stringList(accessNode.path("tool_invocation"));
        List<String> requiredPrivileges = stringList(request.path("required_privileges"));

        List<String> reasons = new ArrayList<>();
        for (String privilege : requiredPrivileges) {
            boolean declared = switch (privilege) {
                case "FILESYSTEM_READ" -> Set.of("READ_ONLY_SANDBOX", "READ_WRITE_SANDBOX").contains(filesystem);
                case "FILESYSTEM_WRITE" -> "READ_WRITE_SANDBOX".equals(filesystem);
                case "NETWORK_EGRESS" -> Set.of("EGRESS_ALLOWLIST_ONLY", "UNRESTRICTED").contains(network);
                default -> privilege.startsWith("TOOL_INVOCATION:")
                        && toolInvocation.contains(privilege.substring("TOOL_INVOCATION:".length()));
            };
            if (!declared) reasons.add("UNDECLARED_PRIVILEGE:" + privilege);
        }

        String qualificationState;
        if (publisherRevoked) {
            qualificationState = "REVOKED";
            reasons.add(0, "PUBLISHER_REVOKED");
        } else if (!publisherSignatureValid) {
            qualificationState = "NOT_QUALIFIED";
            reasons.add(0, "PUBLISHER_SIGNATURE_INVALID");
        } else if (!reasons.isEmpty()) {
            qualificationState = "NOT_QUALIFIED";
        } else {
            PluginQualificationLedger ledger = new PluginQualificationLedger(
                    workspaceRoot.resolve(".onsure/assurance/plugin-qualifications"));
            PluginQualificationLedger.Record previous = ledger.last(pluginId);
            if (previous != null && "QUALIFIED".equals(previous.qualificationState())
                    && !previous.artifactDigest().equals(artifactDigest)) {
                qualificationState = "QUALIFICATION_PENDING";
                reasons.add("ARTIFACT_DIGEST_CHANGED_REQUALIFICATION_REQUIRED");
            } else {
                qualificationState = "QUALIFIED";
            }
            ledger.save(new PluginQualificationLedger.Record(pluginId, artifactDigest, qualificationState, Instant.now().toString()));
        }

        Map<String, Object> out = base("PLUGIN_QUALIFICATION", targetId);
        out.put("plugin_id", pluginId);
        out.put("plugin_version", pluginVersion);
        out.put("artifact_digest", artifactDigest);
        out.put("qualification_state", qualificationState);
        out.put("reasons", List.copyOf(reasons));
        out.put("decision", "QUALIFIED".equals(qualificationState) ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    /**
     * 52_EXTERNAL_INTEGRATION_AND_SUPPLY_CHAIN_TRUST.md SS6/SS10/SS11 real reconciliation: an
     * external provider's state and ONSure's local state can genuinely differ (a CI success
     * webhook for a different commit, a mutable container tag whose digest moved, a license cached
     * ACTIVE locally while the provider now says REVOKED), and doc 52 is explicit that a mismatch
     * must become EXTERNAL_STATE_CONFLICT_HOLD -- never an automatic pick of "whichever side looks
     * better." Just as important: a failed/timed-out provider lookup must never be treated as a
     * clean/zero result (SS7/SS10's "advisory lookup timeout을 0 vulnerability로 처리" negative
     * test) -- it fails closed to HOLD exactly like a genuine conflict, not to CONSISTENT.
     */
    private Map<String, Object> externalIntegrationReconcile(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String integrationType = requiredText(request, "integration_type");
        if (!Set.of("CI_STATUS", "CONTAINER_DIGEST", "LICENSE_STATUS", "DEPENDENCY_ADVISORY").contains(integrationType)) {
            return failClosed("HOLD", List.of("EXTERNAL_INTEGRATION_TYPE_INVALID"));
        }
        String expectedSubject = requiredText(request, "expected_subject");

        JsonNode localNode = request.path("local_state");
        String localSubject = requiredText(localNode, "subject");
        String localValue = requiredText(localNode, "value");

        JsonNode providerNode = request.path("provider_state");
        boolean lookupSucceeded = providerNode.path("lookup_succeeded").asBoolean(false);

        Map<String, Object> out = base("EXTERNAL_INTEGRATION_RECONCILIATION", targetId);
        out.put("integration_type", integrationType);
        out.put("expected_subject", expectedSubject);

        if (!lookupSucceeded) {
            out.put("reconciliation_state", "HOLD");
            out.put("reasons", List.of("EXTERNAL_LOOKUP_FAILED_NOT_TREATED_AS_CLEAN"));
            out.put("decision", "HOLD");
            return immutable(out);
        }

        String providerSubject = requiredText(providerNode, "subject");
        String providerValue = requiredText(providerNode, "value");

        List<String> reasons = new ArrayList<>();
        if (!expectedSubject.equals(providerSubject) || !localSubject.equals(providerSubject)) {
            reasons.add("EXTERNAL_STATE_CONFLICT_HOLD:SUBJECT_MISMATCH");
        }
        if (!localValue.equals(providerValue)) {
            reasons.add("EXTERNAL_STATE_CONFLICT_HOLD:VALUE_MISMATCH");
        }

        String reconciliationState = reasons.isEmpty() ? "CONSISTENT" : "CONFLICT";
        out.put("reconciliation_state", reconciliationState);
        out.put("reasons", List.copyOf(reasons));
        out.put("decision", reasons.isEmpty() ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    /**
     * Real wiring for kr.co.oruda.onsure.learning.OfficialLearningLedger (FR-LEARN): the ledger
     * class itself already hash-chains LEARNING_CANDIDATE through APPLIED_LOCK and enforces
     * self-approval/self-validation blocking, two-independent-run promotion, reviewer/approver
     * separation and rollback-pointer requirements, but until this wiring it was not reachable
     * from any dispatch operation -- a real, tested engine with no door into the product. actor
     * identity fields that describe *this call's own actor* (learner_identity, requested_by,
     * verifier_identity) are bound to the authenticated caller, never accepted as a caller claim,
     * closing an identity-spoofing gap the ledger's own internal checks assume is already closed
     * upstream.
     */
    private OfficialLearningLedger learningLedger() {
        return new OfficialLearningLedger(
                workspaceRoot.resolve(".onsure/assurance/official-learning-ledger.jsonl"));
    }

    private Map<String, Object> learningLedgerResult(String targetId, String artifactType, Runnable ledgerCall) {
        Map<String, Object> out = base(artifactType, targetId);
        try {
            ledgerCall.run();
            out.put("decision", "NON_FINAL");
        } catch (IllegalStateException violation) {
            out.put("decision", "HOLD");
            out.put("reasons", List.of(violation.getMessage()));
        }
        return immutable(out);
    }

    private Map<String, Object> learningCandidateRegister(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        var ledger = learningLedger();
        return learningLedgerResult(targetId, "LEARNING_CANDIDATE_REGISTERED", () -> ledger.registerCandidate(
                new OfficialLearningLedger.LearningCandidate(
                        requiredText(request, "candidate_id"), requiredText(request, "candidate_type"),
                        requiredDigest(request, "source_receipt_sha256"), requiredDigest(request, "learner_output_sha256"),
                        requiredText(request, "training_dataset_version"),
                        request.path("hidden_dataset_non_access_attestation").asBoolean(false),
                        identity.actorId())));
    }

    private Map<String, Object> learningValidationRequest(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        var ledger = learningLedger();
        return learningLedgerResult(targetId, "LEARNING_VALIDATION_REQUESTED", () -> ledger.requestValidation(
                new OfficialLearningLedger.ValidationRequest(
                        requiredText(request, "request_id"), requiredText(request, "candidate_id"),
                        requiredText(request, "queue_item_id"), requiredText(request, "policy_version"),
                        requiredDigest(request, "dataset_versions_digest"), requiredText(request, "validator_version"),
                        identity.actorId())));
    }

    private Map<String, Object> learningValidationPackIssue(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        var ledger = learningLedger();
        return learningLedgerResult(targetId, "LEARNING_VALIDATION_PACK_ISSUED", () -> ledger.issueValidationPack(
                new OfficialLearningLedger.ValidationPack(
                        requiredText(request, "pack_id"), requiredText(request, "request_id"),
                        requiredText(request, "candidate_id"), requiredDigest(request, "fixture_digest"),
                        requiredDigest(request, "harness_digest"), requiredDigest(request, "oracle_digest"),
                        requiredDigest(request, "expected_evidence_digest"))));
    }

    private Map<String, Object> learningValidationReceiptRecord(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        var ledger = learningLedger();
        return learningLedgerResult(targetId, "LEARNING_VALIDATION_RECEIPT_RECORDED", () -> ledger.recordValidationReceipt(
                new OfficialLearningLedger.ValidationReceipt(
                        requiredText(request, "receipt_id"), requiredText(request, "pack_id"),
                        requiredText(request, "candidate_id"), requiredText(request, "run_id"),
                        identity.actorId(), requiredText(request, "decision"),
                        requiredDigest(request, "projection_digest"), requiredDigest(request, "evidence_digest"),
                        request.path("independent_recalculation").asBoolean(false),
                        request.path("copied_learner_output").asBoolean(false))));
    }

    private Map<String, Object> learningPromotionApprove(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        var ledger = learningLedger();
        return learningLedgerResult(targetId, "LEARNING_PROMOTION_APPROVED", () -> ledger.approvePromotion(
                new OfficialLearningLedger.Promotion(
                        requiredText(request, "promotion_id"), requiredText(request, "candidate_id"),
                        requiredDigest(request, "artifact_digest"), requiredText(request, "application_class"),
                        requiredText(request, "reviewer_identity"), requiredText(request, "approver_identity"),
                        requiredText(request, "rollback_plan_id"))));
    }

    private Map<String, Object> learningAppliedLockRecord(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        var ledger = learningLedger();
        return learningLedgerResult(targetId, "LEARNING_APPLIED_LOCK_RECORDED", () -> ledger.lockApplied(
                new OfficialLearningLedger.AppliedLock(
                        requiredText(request, "lock_id"), requiredText(request, "candidate_id"),
                        requiredDigest(request, "artifact_digest"), requiredText(request, "active_selector"),
                        requiredDigest(request, "active_artifact_digest"), requiredText(request, "main_or_stable_ref_sha"),
                        requiredDigest(request, "immutable_evidence_bundle_digest"),
                        requiredText(request, "post_apply_verification_receipt_id"),
                        requiredText(request, "rollback_pointer"),
                        requiredDigest(request, "applied_count_increment_receipt_digest"),
                        request.path("read_only_reverification_pass").asBoolean(false))));
    }

    private Map<String, Object> learningCompletionStatusCheck(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String candidateId = requiredText(request, "candidate_id");
        var ledger = learningLedger();
        Map<String, Object> out = base("LEARNING_COMPLETION_STATUS", targetId);
        out.put("candidate_id", candidateId);
        try {
            var status = ledger.completionStatus(candidateId);
            out.put("completion_status", status.name());
            out.put("applied_locked", status == OfficialLearningLedger.CompletionStatus.APPLIED_LOCKED);
            out.put("decision", "NON_FINAL");
        } catch (IllegalStateException violation) {
            out.put("decision", "HOLD");
            out.put("reasons", List.of(violation.getMessage()));
        }
        return immutable(out);
    }

    /**
     * oracle-disagreement-case.v1.schema.json real computation (148 P0 invariant 5): given two or
     * more named oracle results for the same subject, computes whether they genuinely disagree --
     * never resolved by a simple majority vote (149 SS F's own named negative case) -- and, when
     * they do, forces the case OPEN with related_decision HOLD. Agreement requires every oracle to
     * report the exact same decision; anything else is a real disagreement, not a caller-declared
     * one.
     */
    private Map<String, Object> oracleMultiEvaluate(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        // Deliberately not "case_id": that field name is a distinct, already-RBAC-tracked
        // customer-service resource identifier elsewhere in the dispatcher (case.open et al.),
        // and reusing it here would make this call require ownership of an unrelated resource.
        String disagreementCaseId = requiredText(request, "disagreement_case_id");
        String subjectId = requiredText(request, "subject_id");
        JsonNode resultsNode = request.path("oracle_results");
        if (!resultsNode.isArray() || resultsNode.size() < 2) {
            return failClosed("INPUT_REQUIRED", List.of("ORACLE_MULTI_EVALUATE_REQUIRES_AT_LEAST_TWO_RESULTS"));
        }
        Set<String> validDecisions = Set.of("PASS", "FAIL", "BLOCKED", "HOLD", "NOT_RUN", "INCONCLUSIVE");
        List<Map<String, Object>> results = new ArrayList<>();
        java.util.LinkedHashSet<String> oracleIds = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> distinctDecisions = new java.util.LinkedHashSet<>();
        for (JsonNode row : resultsNode) {
            String oracleId = requiredText(row, "oracle_id");
            if (!oracleIds.add(oracleId)) return failClosed("HOLD", List.of("DUPLICATE_ORACLE_RESULT:" + oracleId));
            String decision = row.path("decision").asText("");
            if (!validDecisions.contains(decision)) return failClosed("HOLD", List.of("ORACLE_DECISION_INVALID:" + oracleId));
            distinctDecisions.add(decision);
            results.add(Map.of("oracle_id", oracleId, "decision", decision));
        }

        boolean disagreement = distinctDecisions.size() > 1;
        String status = disagreement ? "OPEN" : "RESOLVED";
        String relatedDecision = disagreement ? "HOLD" : distinctDecisions.iterator().next();

        Map<String, Object> out = base("ORACLE_DISAGREEMENT_CASE", targetId);
        out.put("disagreement_case_id", disagreementCaseId);
        out.put("subject_id", subjectId);
        out.put("oracle_results", List.copyOf(results));
        out.put("disagreement", disagreement);
        out.put("status", status);
        out.put("related_decision", relatedDecision);
        out.put("decision", disagreement ? "HOLD" : "NON_FINAL");
        return immutable(out);
    }

    /**
     * corpus-integrity-report.v1.schema.json real computation (148 P0 invariant 6): the decision
     * is derived from the three integrity axes, never trusted from the caller. Any CONFIRMED or
     * IMPACT_ASSESSED axis forces BLOCKED; a SUSPECTED axis with nothing worse forces HOLD; only
     * three genuinely CLEAR axes reach CLEAR.
     */
    private Map<String, Object> corpusIntegrityCheck(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String corpusId = requiredText(request, "corpus_id");
        Set<String> validStates = Set.of("CLEAR", "SUSPECTED", "CONFIRMED", "IMPACT_ASSESSED");
        String poisoningState = requiredText(request, "poisoning_state");
        String tenantLeakageState = requiredText(request, "tenant_leakage_state");
        String benchmarkContaminationState = requiredText(request, "benchmark_contamination_state");
        for (var entry : Map.of(
                "poisoning_state", poisoningState, "tenant_leakage_state", tenantLeakageState,
                "benchmark_contamination_state", benchmarkContaminationState).entrySet()) {
            if (!validStates.contains(entry.getValue())) {
                return failClosed("HOLD", List.of("CORPUS_INTEGRITY_STATE_INVALID:" + entry.getKey()));
            }
        }
        List<String> axes = List.of(poisoningState, tenantLeakageState, benchmarkContaminationState);
        String decision;
        if (axes.stream().anyMatch(value -> Set.of("CONFIRMED", "IMPACT_ASSESSED").contains(value))) {
            decision = "BLOCKED";
        } else if (axes.stream().anyMatch("SUSPECTED"::equals)) {
            decision = "HOLD";
        } else {
            decision = "CLEAR";
        }

        Map<String, Object> out = base("CORPUS_INTEGRITY_REPORT", targetId);
        out.put("corpus_id", corpusId);
        out.put("poisoning_state", poisoningState);
        out.put("tenant_leakage_state", tenantLeakageState);
        out.put("benchmark_contamination_state", benchmarkContaminationState);
        out.put("decision", decision);
        return immutable(out);
    }

    /**
     * validator-regression-qualification.v1.schema.json real computation (148 P0 invariant 9): the
     * numeric false_positive_drift/false_negative_drift vs drift_threshold comparison JSON Schema
     * alone cannot express (no $data support) happens here for real -- a validator whose drift
     * exceeds its own declared threshold is REGRESSED, never QUALIFIED, regardless of what the
     * caller might otherwise claim.
     */
    private Map<String, Object> validatorRegressionQualify(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String validatorId = requiredText(request, "validator_id");
        Set<String> validRunResult = Set.of("PASS", "FAIL", "NOT_RUN");
        String golden = requiredText(request, "golden_result");
        String blind = requiredText(request, "blind_result");
        String challenge = requiredText(request, "challenge_result");
        if (!validRunResult.contains(golden) || !validRunResult.contains(blind) || !validRunResult.contains(challenge)) {
            return failClosed("HOLD", List.of("VALIDATOR_REGRESSION_RUN_RESULT_INVALID"));
        }
        double falsePositiveDrift = request.path("false_positive_drift").asDouble(-1);
        double falseNegativeDrift = request.path("false_negative_drift").asDouble(-1);
        double driftThreshold = request.path("drift_threshold").asDouble(-1);
        if (falsePositiveDrift < 0 || falseNegativeDrift < 0 || driftThreshold < 0) {
            return failClosed("HOLD", List.of("VALIDATOR_REGRESSION_DRIFT_VALUES_INVALID"));
        }

        List<String> runResults = List.of(golden, blind, challenge);
        String decision;
        if (runResults.contains("FAIL")) {
            decision = "REGRESSED";
        } else if (falsePositiveDrift > driftThreshold || falseNegativeDrift > driftThreshold) {
            decision = "REGRESSED";
        } else if (runResults.contains("NOT_RUN")) {
            decision = "STALE";
        } else {
            decision = "QUALIFIED";
        }

        Map<String, Object> out = base("VALIDATOR_REGRESSION_QUALIFICATION", targetId);
        out.put("validator_id", validatorId);
        out.put("false_positive_drift", falsePositiveDrift);
        out.put("false_negative_drift", falseNegativeDrift);
        out.put("drift_threshold", driftThreshold);
        out.put("decision", decision);
        return immutable(out);
    }

    /**
     * learning-stop-decision.v1.schema.json real computation. Regression risk at coverage
     * saturation, or an exceeded budget, forces STOP/HOLD regardless of a positive marginal_gain
     * claim -- there is no path here that reaches CONTINUE without an actual basis for it.
     */
    private Map<String, Object> learningStopDecisionCompute(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String candidateId = requiredText(request, "candidate_id");
        Set<String> riskLevels = Set.of("LOW", "MEDIUM", "HIGH");
        String regressionRisk = requiredText(request, "regression_risk");
        String falsePositiveCost = requiredText(request, "false_positive_cost");
        if (!riskLevels.contains(regressionRisk) || !riskLevels.contains(falsePositiveCost)) {
            return failClosed("HOLD", List.of("LEARNING_STOP_RISK_LEVEL_INVALID"));
        }
        double coverageSaturation = request.path("coverage_saturation").asDouble(-1);
        if (coverageSaturation < 0 || coverageSaturation > 1) {
            return failClosed("HOLD", List.of("LEARNING_STOP_COVERAGE_SATURATION_INVALID"));
        }
        Set<String> budgetStates = Set.of("WITHIN_BUDGET", "NEAR_LIMIT", "EXCEEDED");
        String budgetState = requiredText(request, "budget_state");
        if (!budgetStates.contains(budgetState)) {
            return failClosed("HOLD", List.of("LEARNING_STOP_BUDGET_STATE_INVALID"));
        }
        double marginalGain = request.path("marginal_gain").asDouble(0);

        String decision;
        List<String> reasons = new ArrayList<>();
        if ("EXCEEDED".equals(budgetState)) {
            decision = "STOP";
            reasons.add("BUDGET_EXCEEDED");
        } else if (coverageSaturation >= 0.95 && "HIGH".equals(regressionRisk)) {
            decision = "STOP";
            reasons.add("COVERAGE_SATURATED_WITH_HIGH_REGRESSION_RISK");
        } else if ("NEAR_LIMIT".equals(budgetState) || "HIGH".equals(regressionRisk) || marginalGain <= 0) {
            decision = "HOLD";
            reasons.add("BUDGET_OR_RISK_OR_GAIN_REQUIRES_REVIEW");
        } else {
            decision = "CONTINUE";
        }

        Map<String, Object> out = base("LEARNING_STOP_DECISION", targetId);
        out.put("candidate_id", candidateId);
        out.put("regression_risk", regressionRisk);
        out.put("false_positive_cost", falsePositiveCost);
        out.put("coverage_saturation", coverageSaturation);
        out.put("budget_state", budgetState);
        out.put("marginal_gain", marginalGain);
        out.put("decision", decision);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    /**
     * learning-scope-promotion.v1.schema.json real computation (149 SS I, 148 P0 invariant 6,
     * doc 158 contradiction class 4 "Tenant Isolation vs Global Learning"). APPROVED is reachable
     * only when consent, a privacy/anonymization proof, and explicit policy approval are ALL
     * present and the corpus integrity report is CLEAR -- any missing proof or a non-CLEAR corpus
     * report forces HOLD, never a silent APPROVED with a gap. to_scope=GLOBAL is only reachable
     * from from_scope=INDUSTRY (no skipping straight from TENANT/ORGANIZATION to GLOBAL); a skip
     * attempt is DENIED outright regardless of how complete the proofs otherwise are.
     */
    private Map<String, Object> learningScopePromotionDecide(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String promotionId = requiredText(request, "promotion_id");
        String assetId = requiredText(request, "asset_id");
        Set<String> scopes = Set.of("TENANT", "ORGANIZATION", "INDUSTRY", "GLOBAL");
        String fromScope = requiredText(request, "from_scope");
        String toScope = requiredText(request, "to_scope");
        if (!scopes.contains(fromScope) || !scopes.contains(toScope)) {
            return failClosed("HOLD", List.of("LEARNING_SCOPE_PROMOTION_SCOPE_INVALID"));
        }
        String consentRef = request.path("consent_ref").asText(null);
        String privacyProofRef = request.path("privacy_proof_ref").asText(null);
        String policyApprovalRef = request.path("policy_approval_ref").asText(null);
        String corpusIntegrityDecision = requiredText(request, "corpus_integrity_report_decision");
        if (!Set.of("CLEAR", "HOLD", "BLOCKED").contains(corpusIntegrityDecision)) {
            return failClosed("HOLD", List.of("LEARNING_SCOPE_PROMOTION_CORPUS_DECISION_INVALID"));
        }

        String decision;
        List<String> reasons = new ArrayList<>();
        if ("GLOBAL".equals(toScope) && !"INDUSTRY".equals(fromScope)) {
            decision = "DENIED";
            reasons.add("GLOBAL_SCOPE_UNREACHABLE_WITHOUT_INDUSTRY_INTERMEDIATE");
        } else if (consentRef == null || privacyProofRef == null || policyApprovalRef == null) {
            decision = "HOLD";
            reasons.add("CONSENT_PRIVACY_OR_POLICY_PROOF_MISSING");
        } else if (!"CLEAR".equals(corpusIntegrityDecision)) {
            decision = "HOLD";
            reasons.add("CORPUS_INTEGRITY_NOT_CLEAR");
        } else {
            decision = "APPROVED";
        }

        Map<String, Object> out = base("LEARNING_SCOPE_PROMOTION", targetId);
        out.put("promotion_id", promotionId);
        out.put("asset_id", assetId);
        out.put("from_scope", fromScope);
        out.put("to_scope", toScope);
        out.put("corpus_integrity_report_decision", corpusIntegrityDecision);
        out.put("decision", decision);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    /**
     * derived-learning-lineage-disposition.v1.schema.json real computation (149 SS H, 148 P0
     * invariant 7, doc 158 contradiction class 5 "Deletion vs Derived Global Knowledge"). A
     * CONSENT_WITHDRAWAL trigger forces every derived asset onto a real disposition (REVOKE/
     * DELETE/REQUALIFY_REQUIRED) -- NO_ACTION_WITH_PROOF is structurally unreachable for that
     * trigger, closing the exact "unresolved derived asset stays implicitly ACTIVE" gap the schema
     * names. Other triggers (CORPUS_REVOCATION/TENANT_OFFBOARDING/POLICY_CHANGE) may legitimately
     * resolve to NO_ACTION_WITH_PROOF when the caller has real evidence nothing derived needs to
     * change, but that evidence (evidence_refs) is still mandatory in every case.
     */
    private Map<String, Object> learningDerivedLineageDispose(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String dispositionId = requiredText(request, "disposition_id");
        String sourceId = requiredText(request, "source_id");
        JsonNode derivedAssetIdsNode = request.path("derived_asset_ids");
        if (!derivedAssetIdsNode.isArray() || derivedAssetIdsNode.isEmpty()) {
            return failClosed("HOLD", List.of("LEARNING_DERIVED_LINEAGE_ASSET_IDS_REQUIRED"));
        }
        List<String> derivedAssetIds = stringList(derivedAssetIdsNode);
        Set<String> triggers = Set.of("CONSENT_WITHDRAWAL", "CORPUS_REVOCATION", "TENANT_OFFBOARDING", "POLICY_CHANGE");
        String trigger = requiredText(request, "trigger");
        if (!triggers.contains(trigger)) {
            return failClosed("HOLD", List.of("LEARNING_DERIVED_LINEAGE_TRIGGER_INVALID"));
        }
        String requestedDisposition = requiredText(request, "disposition");
        Set<String> dispositions = Set.of("REVOKE", "DELETE", "REQUALIFY_REQUIRED", "NO_ACTION_WITH_PROOF");
        if (!dispositions.contains(requestedDisposition)) {
            return failClosed("HOLD", List.of("LEARNING_DERIVED_LINEAGE_DISPOSITION_INVALID"));
        }
        JsonNode evidenceRefsNode = request.path("evidence_refs");
        if (!evidenceRefsNode.isArray() || evidenceRefsNode.isEmpty()) {
            return failClosed("HOLD", List.of("LEARNING_DERIVED_LINEAGE_EVIDENCE_REQUIRED"));
        }
        List<String> evidenceRefs = stringList(evidenceRefsNode);
        for (String ref : evidenceRefs) {
            if (!ref.matches("[0-9a-f]{64}")) {
                return failClosed("HOLD", List.of("LEARNING_DERIVED_LINEAGE_EVIDENCE_DIGEST_INVALID"));
            }
        }

        String disposition = requestedDisposition;
        List<String> reasons = new ArrayList<>();
        if ("CONSENT_WITHDRAWAL".equals(trigger) && "NO_ACTION_WITH_PROOF".equals(requestedDisposition)) {
            disposition = "REQUALIFY_REQUIRED";
            reasons.add("CONSENT_WITHDRAWAL_FORBIDS_NO_ACTION_DOWNGRADED_TO_REQUALIFY_REQUIRED");
        }

        Map<String, Object> out = base("DERIVED_LEARNING_LINEAGE_DISPOSITION", targetId);
        out.put("disposition_id", dispositionId);
        out.put("source_id", sourceId);
        out.put("derived_asset_ids", derivedAssetIds);
        out.put("trigger", trigger);
        out.put("disposition", disposition);
        out.put("evidence_refs", evidenceRefs);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    /**
     * decision-time-knowledge-snapshot.v1.schema.json real computation (doc 158 contradiction
     * classes 7 "Adaptive Learning vs Reproducibility" and 11 "Ground-truth Drift vs Historical
     * Immutability", LC-P0-007/LC-P0-011). The epoch comparison JSON Schema alone cannot express
     * (no $data support) happens here: knowledge_epoch equal to current_knowledge_epoch is the
     * ONLY way to reach CURRENT/replay_claim_allowed=true. Any drift forces STALE (or
     * REVIEW_REQUIRED when the caller flags it as a materially decision-relevant drift, not just a
     * cosmetic epoch bump) and REQUIRES a real reevaluation_ref -- there is no code path that lets
     * a stale decision keep claiming CURRENT, and decision_sha256 is echoed back unchanged, never
     * recomputed, so the original decision content this snapshot annotates is never itself touched.
     */
    private GroundTruthEpochLedger groundTruthEpochLedger() {
        return new GroundTruthEpochLedger(workspaceRoot.resolve(".onsure/assurance/ground-truth-epochs"));
    }

    private RevalidationBacklogLedger revalidationBacklogLedger() {
        return new RevalidationBacklogLedger(workspaceRoot.resolve(".onsure/assurance/revalidation-backlog"));
    }

    /**
     * LC-P0-011 cross-wire (doc 158 class 11, both remaining contract bindings). GroundTruthAuthority:
     * current_knowledge_epoch must be a REALLY-declared epoch (checked against GroundTruthEpochLedger,
     * which only AUDITOR/ADMIN can append to via {@link #groundTruthEpochDeclare}) -- an arbitrary
     * caller-supplied epoch string is rejected outright, closing the "who has authority to say what
     * counts as current" half of the class. RevalidationBacklog: a STALE/REVIEW_REQUIRED result does
     * not just return a bare reevaluation_ref string for the caller to track themselves -- it is
     * really enqueued into RevalidationBacklogLedger, so "every stale decision eventually gets
     * reevaluated" becomes a checkable property via {@link #learningRevalidationBacklogStatus}
     * instead of an unverifiable claim.
     */
    private Map<String, Object> learningDecisionCurrentnessEvaluate(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String snapshotId = requiredText(request, "snapshot_id");
        String decisionRef = requiredText(request, "decision_ref");
        String decisionSha256 = requiredDigest(request, "decision_sha256");
        String knowledgeEpoch = requiredText(request, "knowledge_epoch");
        String currentKnowledgeEpoch = requiredText(request, "current_knowledge_epoch");
        boolean materialDrift = request.path("material_drift").asBoolean(false);
        String reevaluationRef = request.path("reevaluation_ref").asText(null);

        if (!groundTruthEpochLedger().isDeclaredEpoch(currentKnowledgeEpoch)) {
            return failClosed("HOLD", List.of("LEARNING_DECISION_CURRENTNESS_EPOCH_NOT_DECLARED"));
        }

        String currentnessState;
        boolean replayClaimAllowed;
        if (knowledgeEpoch.equals(currentKnowledgeEpoch)) {
            currentnessState = "CURRENT";
            replayClaimAllowed = true;
            reevaluationRef = null;
        } else {
            currentnessState = materialDrift ? "REVIEW_REQUIRED" : "STALE";
            replayClaimAllowed = false;
            if (reevaluationRef == null || reevaluationRef.isBlank()) {
                return failClosed("HOLD", List.of("LEARNING_DECISION_CURRENTNESS_REEVALUATION_REF_REQUIRED"));
            }
            try {
                revalidationBacklogLedger().enqueue(reevaluationRef, decisionRef);
            } catch (IllegalArgumentException alreadyQueued) {
                return failClosed("HOLD", List.of(alreadyQueued.getMessage()));
            }
        }

        Map<String, Object> out = base("DECISION_TIME_KNOWLEDGE_SNAPSHOT", targetId);
        out.put("snapshot_id", snapshotId);
        out.put("decision_ref", decisionRef);
        out.put("decision_sha256", decisionSha256);
        out.put("knowledge_epoch", knowledgeEpoch);
        out.put("current_knowledge_epoch", currentKnowledgeEpoch);
        out.put("currentness_state", currentnessState);
        out.put("reevaluation_ref", reevaluationRef);
        out.put("replay_claim_allowed", replayClaimAllowed);
        return immutable(out);
    }

    /** GroundTruthAuthority: authorityConfirmed is computed from the caller's real roles, never trusted from the request. */
    private Map<String, Object> groundTruthEpochDeclare(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String epochId = requiredText(request, "epoch_id");
        boolean authorityConfirmed = identity.roles().contains(AuthenticatedWorkflowIdentity.Role.AUDITOR)
                || identity.roles().contains(AuthenticatedWorkflowIdentity.Role.ADMIN);
        GroundTruthEpochLedger.EpochDeclaration declaration;
        try {
            declaration = groundTruthEpochLedger().declare(epochId, identity.actorId(), authorityConfirmed);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("GROUND_TRUTH_EPOCH_DECLARED", targetId);
        out.put("epoch_id", declaration.epochId());
        out.put("declared_by", declaration.declaredBy());
        out.put("declared_at", declaration.declaredAt());
        return immutable(out);
    }

    private Map<String, Object> learningRevalidationComplete(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String reevaluationRef = requiredText(request, "reevaluation_ref");
        RevalidationBacklogLedger.Entry entry;
        try {
            entry = revalidationBacklogLedger().complete(reevaluationRef);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("REVALIDATION_COMPLETED", targetId);
        out.put("reevaluation_ref", entry.reevaluationRef());
        out.put("decision_ref", entry.decisionRef());
        out.put("status", entry.status());
        out.put("completed_at", entry.completedAt());
        return immutable(out);
    }

    /** Real enumeration of every still-open backlog item -- not a caller-supplied count. */
    private Map<String, Object> learningRevalidationBacklogStatus(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        List<RevalidationBacklogLedger.Entry> pending = revalidationBacklogLedger().pending();
        Map<String, Object> out = base("REVALIDATION_BACKLOG_STATUS", targetId);
        out.put("pending_count", pending.size());
        out.put("pending_reevaluation_refs", pending.stream().map(RevalidationBacklogLedger.Entry::reevaluationRef).sorted().toList());
        return immutable(out);
    }

    /**
     * human-override-disposition.v1.schema.json real computation (doc 158 contradiction class 9
     * "Human Override vs Self-confirmation", LC-P0-009). "override는 signal이며 truth가 아니다":
     * promoted_to_active_knowledge can only be true when reason, evidence_ref, AND a confirmer_id
     * genuinely distinct from overrider_id are all present -- self-confirmation (confirmer_id
     * equal to overrider_id) is rejected outright, the same "same actor cannot both act and
     * attest" shape as SeparationOfDutiesLedger/AppealLedger's reviewer-separation checks
     * elsewhere in this codebase, applied here to the learning-knowledge-promotion boundary.
     */
    private Map<String, Object> learningHumanOverrideDecide(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String overrideId = requiredText(request, "override_id");
        String candidateRef = requiredText(request, "candidate_ref");
        String overriderId = requiredText(request, "overrider_id");
        String reason = request.path("reason").asText(null);
        String evidenceRef = request.path("evidence_ref").asText(null);
        String confirmerId = request.path("confirmer_id").asText(null);

        boolean promoted;
        boolean selfConfirmationRejected = false;
        List<String> reasons = new ArrayList<>();
        if (reason == null || reason.isBlank() || evidenceRef == null || evidenceRef.isBlank()
                || confirmerId == null || confirmerId.isBlank()) {
            promoted = false;
            reasons.add("REASON_EVIDENCE_OR_CONFIRMER_MISSING");
        } else if (confirmerId.equals(overriderId)) {
            promoted = false;
            selfConfirmationRejected = true;
            reasons.add("SELF_CONFIRMATION_CANNOT_PROMOTE_OVERRIDE_TO_ACTIVE_KNOWLEDGE");
        } else {
            promoted = true;
        }

        new HumanOverrideTrendLedger(workspaceRoot.resolve(".onsure/assurance/override-trends"))
                .record(candidateRef, overrideId, promoted, selfConfirmationRejected);

        Map<String, Object> out = base("HUMAN_OVERRIDE_DISPOSITION", targetId);
        out.put("override_id", overrideId);
        out.put("candidate_ref", candidateRef);
        out.put("overrider_id", overriderId);
        out.put("reason", reason);
        out.put("evidence_ref", evidenceRef);
        out.put("confirmer_id", confirmerId);
        out.put("promoted_to_active_knowledge", promoted);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    /**
     * LC-P0-009 cross-wire (doc 158 class 9, second contract binding): HumanOverrideTrendReport,
     * computed from HumanOverrideTrendLedger's real recorded history for the candidate, never a
     * caller-supplied summary. Answers the question learningHumanOverrideDecide's per-event
     * disposition alone cannot: is self-confirmation being attempted repeatedly for this candidate
     * (a real trend, not a one-off), which promotion_rate and self_confirmation_rejected_count make
     * visible across the whole history rather than one event at a time.
     */
    private Map<String, Object> learningHumanOverrideTrendReport(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String candidateRef = requiredText(request, "candidate_ref");
        HumanOverrideTrendLedger.TrendReport report = new HumanOverrideTrendLedger(
                workspaceRoot.resolve(".onsure/assurance/override-trends")).report(candidateRef);

        Map<String, Object> out = base("HUMAN_OVERRIDE_TREND_REPORT", targetId);
        out.put("candidate_ref", report.candidateRef());
        out.put("total_overrides", report.totalOverrides());
        out.put("promoted_count", report.promotedCount());
        out.put("self_confirmation_rejected_count", report.selfConfirmationRejectedCount());
        out.put("promotion_rate", report.promotionRate());
        return immutable(out);
    }

    /**
     * counterevidence-disposition.v1.schema.json real computation (doc 158 contradiction class 8
     * "Counterevidence vs Privacy", LC-P0-008). "privacy를 이유로 불리한 증거만 제거하지 않는다":
     * a caller requesting DELETE for real counterevidence is downgraded to RETAINED_PSEUDONYMIZED
     * unless deletion_basis is a genuine external requirement (LEGAL_REQUIREMENT or
     * DATA_SUBJECT_ERASURE_RIGHT) -- PRIVACY_PREFERENCE_ONLY or no basis at all is exactly the
     * named negative case (unfavorable evidence quietly removed under a privacy pretext) and is
     * never honored as a deletion reason on its own.
     */
    private Map<String, Object> learningCounterevidenceDispose(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String dispositionId = requiredText(request, "disposition_id");
        String evidenceRef = requiredText(request, "evidence_ref");
        String decisionRef = requiredText(request, "decision_ref");
        boolean isCounterevidence = request.path("is_counterevidence").asBoolean(false);
        String requestedDisposition = requiredText(request, "requested_disposition");
        Set<String> dispositions = Set.of("RETAINED_MINIMIZED", "RETAINED_PSEUDONYMIZED", "RETAINED_FULL", "DELETED");
        if (!dispositions.contains(requestedDisposition)) {
            return failClosed("HOLD", List.of("COUNTEREVIDENCE_DISPOSITION_INVALID"));
        }
        String deletionBasis = request.path("deletion_basis").asText(null);
        Set<String> genuineDeletionBases = Set.of("LEGAL_REQUIREMENT", "DATA_SUBJECT_ERASURE_RIGHT");

        String disposition = requestedDisposition;
        List<String> reasons = new ArrayList<>();
        if (isCounterevidence && "DELETED".equals(requestedDisposition)
                && (deletionBasis == null || !genuineDeletionBases.contains(deletionBasis))) {
            disposition = "RETAINED_PSEUDONYMIZED";
            deletionBasis = null;
            reasons.add("PRIVACY_ONLY_DELETION_OF_COUNTEREVIDENCE_DOWNGRADED_TO_RETAINED_PSEUDONYMIZED");
        } else if (!"DELETED".equals(disposition)) {
            deletionBasis = null;
        }

        Map<String, Object> out = base("COUNTEREVIDENCE_DISPOSITION", targetId);
        out.put("disposition_id", dispositionId);
        out.put("evidence_ref", evidenceRef);
        out.put("decision_ref", decisionRef);
        out.put("is_counterevidence", isCounterevidence);
        out.put("disposition", disposition);
        out.put("deletion_basis", deletionBasis);
        out.put("reasons", List.copyOf(reasons));
        out.put("evidence_observation", evidenceObservationView(disposition));
        return immutable(out);
    }

    /**
     * LC-P0-008 cross-wire (doc 158 class 8, second contract binding): the counterevidence
     * disposition and its corresponding evidence-observation.v1.schema.json retention_form/
     * reproducibility_claimed must never be independently decided -- one determines the other, so
     * they cannot drift apart the way two separately-called operations could. DELETED can only
     * ever be reached here via a genuine legal basis (learningCounterevidenceDispose's own
     * downgrade logic already guarantees that), so a DELETED disposition maps to TOMBSTONE, never
     * RAW_SENSITIVE -- evidence-observation's own rule (RAW_SENSITIVE forbids a reproducibility
     * claim) is therefore automatically respected rather than left to a second, separately-called
     * evidence-observation.record invocation to enforce.
     */
    private Map<String, Object> evidenceObservationView(String disposition) {
        String retentionForm = switch (disposition) {
            case "RETAINED_MINIMIZED" -> "MINIMIZED";
            case "RETAINED_PSEUDONYMIZED" -> "PSEUDONYMIZED";
            case "RETAINED_FULL" -> "RAW_SENSITIVE";
            case "DELETED" -> "TOMBSTONE";
            default -> throw new IllegalStateException("COUNTEREVIDENCE_DISPOSITION_UNREACHABLE:" + disposition);
        };
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("retention_form", retentionForm);
        view.put("reproducibility_claimed", !"RAW_SENSITIVE".equals(retentionForm));
        return view;
    }

    /**
     * challenge-set-access-disposition.v1.schema.json real computation (doc 158 contradiction
     * class 3 "Transparency vs Challenge Secrecy", LC-P0-003). Methodology/scope/results/authority
     * are always disclosable; SEALED_FIXTURE/SEALED_ANSWER access is evaluator-only -- a
     * non-evaluator requesting either is denied outright. Exposure is one-way: once
     * prior_exposure_state is EXPOSED (from any earlier grant, including a legitimate evaluator's),
     * it stays EXPOSED and blind_authority_retained stays false forever, regardless of what's
     * requested now -- matching real-world blind-test contamination, which cannot be undone by a
     * later "actually let's keep it sealed" decision.
     */
    private Map<String, Object> learningChallengeSetAccessDecide(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String accessId = requiredText(request, "access_id");
        String challengeSetId = requiredText(request, "challenge_set_id");
        Set<String> roles = Set.of("EVALUATOR", "PUBLIC", "LEARNER", "OTHER");
        String requesterRole = requiredText(request, "requester_role");
        if (!roles.contains(requesterRole)) {
            return failClosed("HOLD", List.of("CHALLENGE_SET_ACCESS_ROLE_INVALID"));
        }
        Set<String> fields = Set.of("METHODOLOGY", "SCOPE", "RESULTS", "AUTHORITY", "SEALED_FIXTURE", "SEALED_ANSWER");
        String fieldRequested = requiredText(request, "field_requested");
        if (!fields.contains(fieldRequested)) {
            return failClosed("HOLD", List.of("CHALLENGE_SET_ACCESS_FIELD_INVALID"));
        }
        Set<String> exposureStates = Set.of("SEALED", "EXPOSED");
        String priorExposureState = requiredText(request, "prior_exposure_state");
        if (!exposureStates.contains(priorExposureState)) {
            return failClosed("HOLD", List.of("CHALLENGE_SET_ACCESS_PRIOR_STATE_INVALID"));
        }
        boolean sealedField = "SEALED_FIXTURE".equals(fieldRequested) || "SEALED_ANSWER".equals(fieldRequested);

        boolean accessGranted;
        String exposureState;
        List<String> reasons = new ArrayList<>();
        if ("EXPOSED".equals(priorExposureState)) {
            exposureState = "EXPOSED";
            accessGranted = !sealedField || "EVALUATOR".equals(requesterRole);
            reasons.add("CHALLENGE_SET_ALREADY_EXPOSED_BLIND_AUTHORITY_UNRECOVERABLE");
        } else if (sealedField && !"EVALUATOR".equals(requesterRole)) {
            accessGranted = false;
            exposureState = "SEALED";
            reasons.add("SEALED_FIELD_IS_EVALUATOR_ONLY");
        } else if (sealedField) {
            accessGranted = true;
            exposureState = "EXPOSED";
            reasons.add("EVALUATOR_ACCESS_TO_SEALED_FIELD_EXPOSES_THE_CHALLENGE_SET");
        } else {
            accessGranted = true;
            exposureState = "SEALED";
        }
        boolean blindAuthorityRetained = "SEALED".equals(exposureState);

        Map<String, Object> out = base("CHALLENGE_SET_ACCESS_DISPOSITION", targetId);
        out.put("access_id", accessId);
        out.put("challenge_set_id", challengeSetId);
        out.put("requester_role", requesterRole);
        out.put("field_requested", fieldRequested);
        out.put("prior_exposure_state", priorExposureState);
        out.put("access_granted", accessGranted);
        out.put("exposure_state", exposureState);
        out.put("blind_authority_retained", blindAuthorityRetained);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    /**
     * evidence-observation.v1.schema.json real computation (doc 158 contradiction class 2
     * "Privacy vs Reproducibility", LC-P0-002). "원문 민감정보의 영구보존을 재현성 전제조건으로
     * 두지 않는다": a caller cannot claim reproducibility while retaining raw sensitive content --
     * RAW_SENSITIVE retention forces reproducibility_claimed to false regardless of what the
     * caller requested, closing the named negative case (permanent raw retention used to justify
     * reproducibility) rather than merely discouraging it.
     */
    private Map<String, Object> learningEvidenceObservationRecord(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String observationId = requiredText(request, "observation_id");
        String decisionRef = requiredText(request, "decision_ref");
        Set<String> retentionForms = Set.of("RAW_SENSITIVE", "MINIMIZED", "PSEUDONYMIZED", "DIGEST_ONLY", "TOMBSTONE");
        String retentionForm = requiredText(request, "retention_form");
        if (!retentionForms.contains(retentionForm)) {
            return failClosed("HOLD", List.of("EVIDENCE_OBSERVATION_RETENTION_FORM_INVALID"));
        }
        String contentDigest = requiredDigest(request, "content_digest");
        boolean requestedReproducibilityClaimed = request.path("reproducibility_claimed").asBoolean(false);

        boolean reproducibilityClaimed = requestedReproducibilityClaimed;
        List<String> reasons = new ArrayList<>();
        if ("RAW_SENSITIVE".equals(retentionForm) && requestedReproducibilityClaimed) {
            reproducibilityClaimed = false;
            reasons.add("RAW_SENSITIVE_RETENTION_CANNOT_JUSTIFY_A_REPRODUCIBILITY_CLAIM");
        }

        Map<String, Object> out = base("EVIDENCE_OBSERVATION", targetId);
        out.put("observation_id", observationId);
        out.put("decision_ref", decisionRef);
        out.put("retention_form", retentionForm);
        out.put("content_digest", contentDigest);
        out.put("reproducibility_claimed", reproducibilityClaimed);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    /**
     * onsure-release-qualification.v1.schema.json real computation (71 SS11, Wave 7 schema --
     * existed since Batch 1 with zero runtime consumer until this operation). "Self-validation
     * receipts alone can never produce QUALIFIED": an empty independent_verifier_receipts forces
     * NOT_QUALIFIED regardless of anything else. A release whose valid_until has already passed is
     * STALE even if every other input looks clean. Any archetype in the caller-supplied
     * archetype_qualification_map that is not itself QUALIFIED caps the overall release state --
     * a release is never QUALIFIED as a whole while one of its target archetypes individually
     * is not, closing the "critical blind spot leaves the archetype silently QUALIFIED" case the
     * schema description itself names.
     */
    private Map<String, Object> releaseQualify(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String releaseQualificationId = requiredText(request, "release_qualification_id");
        String onsureReleaseDigest = requiredDigest(request, "onsure_release_digest");

        JsonNode archetypeMapNode = request.path("archetype_qualification_map");
        if (!archetypeMapNode.isArray() || archetypeMapNode.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("RELEASE_ARCHETYPE_QUALIFICATION_MAP_REQUIRED"));
        }
        Set<String> validScopeStates = Set.of("QUALIFIED", "NOT_QUALIFIED", "STALE", "REASSESSMENT_REQUIRED");
        java.util.LinkedHashSet<String> archetypes = new java.util.LinkedHashSet<>();
        List<String> nonQualifiedArchetypes = new ArrayList<>();
        for (JsonNode row : archetypeMapNode) {
            String archetype = requiredText(row, "target_archetype");
            if (!archetypes.add(archetype)) return failClosed("HOLD", List.of("DUPLICATE_ARCHETYPE:" + archetype));
            String scopeState = row.path("scope_state").asText("");
            if (!validScopeStates.contains(scopeState)) {
                return failClosed("HOLD", List.of("RELEASE_ARCHETYPE_SCOPE_STATE_INVALID:" + archetype));
            }
            if (!"QUALIFIED".equals(scopeState)) nonQualifiedArchetypes.add(archetype + ":" + scopeState);
        }

        JsonNode receiptsNode = request.path("independent_verifier_receipts");
        int receiptCount = receiptsNode.isArray() ? receiptsNode.size() : 0;

        Instant now = Instant.now();
        Instant validUntil;
        try {
            validUntil = Instant.parse(requiredText(request, "valid_until"));
        } catch (Exception malformed) {
            return failClosed("HOLD", List.of("RELEASE_VALID_UNTIL_MALFORMED"));
        }

        String state;
        List<String> reasons = new ArrayList<>();
        if (receiptCount == 0) {
            state = "NOT_QUALIFIED";
            reasons.add("SELF_VALIDATION_RECEIPTS_ALONE_CANNOT_QUALIFY");
        } else if (!now.isBefore(validUntil)) {
            state = "STALE";
            reasons.add("RELEASE_QUALIFICATION_EXPIRED");
        } else if (!nonQualifiedArchetypes.isEmpty()) {
            state = "REASSESSMENT_REQUIRED";
            reasons.add("ARCHETYPE_NOT_QUALIFIED:" + String.join(",", nonQualifiedArchetypes));
        } else {
            state = "QUALIFIED";
        }

        Map<String, Object> out = base("ONSURE_RELEASE_QUALIFICATION", targetId);
        out.put("release_qualification_id", releaseQualificationId);
        out.put("onsure_release_digest", onsureReleaseDigest);
        out.put("independent_verifier_receipt_count", receiptCount);
        out.put("archetype_count", archetypes.size());
        out.put("non_qualified_archetypes", List.copyOf(nonQualifiedArchetypes));
        out.put("state", state);
        out.put("reasons", List.copyOf(reasons));
        out.put("decision", "QUALIFIED".equals(state) ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    private static final List<String> ATOMIC_SNAPSHOT_EPOCH_FIELDS = List.of(
            "scope", "requirement", "denominator", "policy", "oracle", "validator_qualification", "authority");
    private static final Set<String> ATOMIC_SNAPSHOT_READ_METHODS =
            Set.of("SINGLE_TRANSACTION_SNAPSHOT_READ", "LEDGER_SEQUENCE_PINNED_READ");

    /**
     * atomic-validation-snapshot.v2.schema.json real computation (FR-META-010 Atomic Validation
     * Snapshot: "Final은 동일 Target/Scope/Requirement/Policy generation에서 필수 Lane이 동시에
     * 성립한 Snapshot이어야 한다. 서로 다른 Run의 좋은 결과를 선택적으로 조립하지 않는다."). JSON
     * Schema alone can validate shape but cannot: (a) verify read_completed_at is not before
     * read_started_at, (b) verify test_execution_summary's own counts arithmetically reconcile
     * with applicable_count, or (c) force HOLD when any required Lane (open P0 findings, failed/
     * blocked/not_run tests) is non-clean -- exactly the "selectively assemble good results from
     * different runs" failure mode this requirement forbids: a snapshot may not report itself
     * atomic-clean while quietly carrying a failed/blocked/not_run test or an open P0. snapshot_
     * sha256 is computed here from the sealed payload, never trusted from the caller, so a
     * downstream consumer can detect any post-capture tamper.
     */
    private Map<String, Object> validationSnapshotVerify(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String snapshotId = requiredText(request, "snapshot_id");
        String targetArtifactSha256 = requiredDigest(request, "target_artifact_sha256");

        JsonNode epochsNode = request.path("epochs");
        Map<String, String> epochs = new LinkedHashMap<>();
        for (String field : ATOMIC_SNAPSHOT_EPOCH_FIELDS) {
            epochs.put(field, requiredText(epochsNode, field));
        }

        JsonNode tokenNode = request.path("read_consistency_token");
        String tokenMethod = requiredText(tokenNode, "method");
        if (!ATOMIC_SNAPSHOT_READ_METHODS.contains(tokenMethod)) {
            return failClosed("HOLD", List.of("SNAPSHOT_READ_CONSISTENCY_METHOD_INVALID:" + tokenMethod));
        }
        String tokenValue = requiredText(tokenNode, "token");
        Instant readStarted;
        Instant readCompleted;
        try {
            readStarted = Instant.parse(requiredText(tokenNode, "read_started_at"));
            readCompleted = Instant.parse(requiredText(tokenNode, "read_completed_at"));
        } catch (Exception malformed) {
            return failClosed("HOLD", List.of("SNAPSHOT_READ_CONSISTENCY_TIMESTAMP_MALFORMED"));
        }
        if (readCompleted.isBefore(readStarted)) {
            return failClosed("HOLD", List.of("SNAPSHOT_READ_COMPLETED_BEFORE_STARTED"));
        }

        JsonNode summaryNode = request.path("test_execution_summary");
        long applicable = requiredNonNegativeLong(summaryNode, "applicable_count");
        long passed = requiredNonNegativeLong(summaryNode, "passed_count");
        long failed = requiredNonNegativeLong(summaryNode, "failed_count");
        long blocked = requiredNonNegativeLong(summaryNode, "blocked_count");
        long hold = requiredNonNegativeLong(summaryNode, "hold_count");
        long notRun = requiredNonNegativeLong(summaryNode, "not_run_count");
        if (passed + failed + blocked + hold + notRun != applicable) {
            return failClosed("HOLD", List.of("SNAPSHOT_TEST_SUMMARY_COUNTS_DO_NOT_RECONCILE"));
        }
        String exactResultDigest = requiredDigest(summaryNode, "exact_result_digest");

        JsonNode findingsNode = request.path("open_findings");
        long p0Count = requiredNonNegativeLong(findingsNode, "p0_count");
        long p1Count = requiredNonNegativeLong(findingsNode, "p1_count");
        String blockingSetDigest = requiredDigest(findingsNode, "blocking_set_digest");

        List<String> nonAtomicLanes = new ArrayList<>();
        if (p0Count > 0) nonAtomicLanes.add("OPEN_P0_FINDINGS:" + p0Count);
        if (failed > 0) nonAtomicLanes.add("FAILED_TESTS:" + failed);
        if (blocked > 0) nonAtomicLanes.add("BLOCKED_TESTS:" + blocked);
        if (notRun > 0) nonAtomicLanes.add("NOT_RUN_TESTS:" + notRun);
        String decision = nonAtomicLanes.isEmpty() ? "ALL_LANES_ATOMIC_CLEAN" : "HOLD";

        Map<String, Object> out = base("ONSURE_ATOMIC_VALIDATION_SNAPSHOT", targetId);
        out.put("snapshot_id", snapshotId);
        out.put("target_artifact_sha256", targetArtifactSha256);
        out.put("epochs", Map.copyOf(epochs));
        out.put("read_consistency_token", Map.of(
                "method", tokenMethod, "token", tokenValue,
                "read_started_at", readStarted.toString(), "read_completed_at", readCompleted.toString()));
        out.put("test_execution_summary", Map.of(
                "applicable_count", applicable, "passed_count", passed, "failed_count", failed,
                "blocked_count", blocked, "hold_count", hold, "not_run_count", notRun,
                "exact_result_digest", exactResultDigest));
        out.put("open_findings", Map.of(
                "p0_count", p0Count, "p1_count", p1Count, "blocking_set_digest", blockingSetDigest));
        out.put("non_atomic_lanes", List.copyOf(nonAtomicLanes));
        out.put("decision", decision);
        Map<String, Object> sealed = immutable(out);
        Map<String, Object> withDigest = new LinkedHashMap<>(sealed);
        withDigest.put("snapshot_sha256", digest(sealed));
        return immutable(withDigest);
    }

    private long requiredNonNegativeLong(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (!value.isIntegralNumber() || value.asLong() < 0) {
            throw new IllegalArgumentException("SEMANTIC_V2_FIELD_REQUIRED:" + field);
        }
        return value.asLong();
    }

    private static final Set<String> VALIDATION_EXPERIMENT_MODES =
            Set.of("STOCHASTIC", "METAMORPHIC", "DIFFERENTIAL", "ENVIRONMENT_MATRIX");
    private static final Set<String> VALIDATION_RUN_OUTCOMES =
            Set.of("PASS", "FAIL", "NOT_RUN", "INCONCLUSIVE");

    /**
     * validation-experiment.v1.schema.json real computation (Batch 5 object J; 149 SS J / 148 P0
     * invariant 12: "a single stochastic run must never claim stability"). JSON Schema's allOf
     * can enforce STOCHASTIC mode's run_count>=2 and a caller-CLAIMED STABLE result's run_count>=2
     * plus all-PASS runs, but it cannot: (a) verify run_count actually equals runs.length (a
     * caller could declare run_count=5 while supplying only 3 real run entries), or (b) COMPUTE
     * result from the runs[] themselves rather than trust a caller-declared value. This operation
     * computes result for real: any FAIL makes the experiment UNSTABLE; any NOT_RUN (with no FAIL)
     * forces NOT_RUN -- generalizing the schema's stated 'a NOT_RUN cell must not be silently
     * treated as PASS' beyond ENVIRONMENT_MATRIX to every mode; any remaining INCONCLUSIVE (with
     * no FAIL/NOT_RUN) forces INCONCLUSIVE; only run_count>=2 with every run PASS reaches STABLE
     * -- a single passing run, in ANY mode, can never claim STABLE.
     */
    private Map<String, Object> validationExperimentEvaluate(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String experimentId = requiredText(request, "experiment_id");
        String subjectId = requiredText(request, "subject_id");
        String environment = requiredText(request, "environment");

        String mode = requiredText(request, "mode");
        if (!VALIDATION_EXPERIMENT_MODES.contains(mode)) {
            return failClosed("HOLD", List.of("VALIDATION_EXPERIMENT_MODE_INVALID:" + mode));
        }

        JsonNode runsNode = request.path("runs");
        if (!runsNode.isArray() || runsNode.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("VALIDATION_EXPERIMENT_RUNS_REQUIRED"));
        }
        long declaredRunCount = requiredNonNegativeLong(request, "run_count");

        List<Map<String, Object>> runs = new ArrayList<>();
        java.util.LinkedHashSet<String> runIds = new java.util.LinkedHashSet<>();
        boolean anyFail = false;
        boolean anyNotRun = false;
        boolean anyInconclusive = false;
        for (JsonNode runNode : runsNode) {
            String runId = requiredText(runNode, "run_id");
            if (!runIds.add(runId)) return failClosed("HOLD", List.of("DUPLICATE_RUN_ID:" + runId));
            String outcome = requiredText(runNode, "outcome");
            if (!VALIDATION_RUN_OUTCOMES.contains(outcome)) {
                return failClosed("HOLD", List.of("VALIDATION_RUN_OUTCOME_INVALID:" + outcome));
            }
            if ("FAIL".equals(outcome)) anyFail = true;
            else if ("NOT_RUN".equals(outcome)) anyNotRun = true;
            else if ("INCONCLUSIVE".equals(outcome)) anyInconclusive = true;
            runs.add(Map.of("run_id", runId, "outcome", outcome));
        }

        if (declaredRunCount != runs.size()) {
            return failClosed("HOLD", List.of(
                    "VALIDATION_EXPERIMENT_RUN_COUNT_MISMATCH:declared=" + declaredRunCount + ":actual=" + runs.size()));
        }
        if ("STOCHASTIC".equals(mode) && runs.size() < 2) {
            return failClosed("HOLD", List.of("STOCHASTIC_REQUIRES_AT_LEAST_TWO_RUNS"));
        }

        String result;
        if (anyFail) {
            result = "UNSTABLE";
        } else if (anyNotRun) {
            result = "NOT_RUN";
        } else if (anyInconclusive) {
            result = "INCONCLUSIVE";
        } else if (runs.size() >= 2) {
            result = "STABLE";
        } else {
            result = "INCONCLUSIVE";
        }

        Map<String, Object> out = base("ONSURE_VALIDATION_EXPERIMENT", targetId);
        out.put("experiment_id", experimentId);
        out.put("mode", mode);
        out.put("subject_id", subjectId);
        out.put("run_count", runs.size());
        out.put("runs", List.copyOf(runs));
        out.put("environment", environment);
        out.put("result", result);
        out.put("decision", "STABLE".equals(result) ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    private static final List<String> EFFECTIVENESS_METRIC_FIELDS = List.of(
            "precision", "recall", "false_positive_rate", "false_negative_rate", "coverage", "latency_ms");
    private static final double EFFECTIVENESS_TOLERANCE = 0.01;
    private static final double EFFECTIVENESS_MIN_CONFIDENCE = 0.8;

    /**
     * learning-effectiveness-report.v1.schema.json real computation (Batch 5 object D; 149 SS D:
     * "before/after metrics on the SAME benchmark_id are the only basis for an IMPROVED/EQUIVALENT
     * decision... a real regression (worse false_positive/false_negative/recall/precision) claiming
     * IMPROVED is the named negative case"). JSON Schema alone cannot numerically compare before vs
     * after. This operation computes decision for real: any regression beyond tolerance on false_
     * positive_rate, false_negative_rate, recall, or precision forces REGRESSION regardless of what
     * improved elsewhere -- a caller cannot claim IMPROVED by cherry-picking one better metric while
     * another got worse. Below-threshold confidence forces INCONCLUSIVE before any directional claim
     * is made at all.
     */
    private Map<String, Object> learningEffectivenessEvaluate(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String reportId = requiredText(request, "report_id");
        String candidateId = requiredText(request, "candidate_id");
        String learningEpoch = requiredText(request, "learning_epoch");
        String benchmarkId = requiredText(request, "benchmark_id");

        Map<String, Double> before = requiredMetricSet(request.path("before"));
        Map<String, Double> after = requiredMetricSet(request.path("after"));
        if (before == null || after == null) {
            return failClosed("HOLD", List.of("EFFECTIVENESS_METRIC_SET_INVALID"));
        }

        double variance = request.path("variance").asDouble(-1);
        double confidence = request.path("confidence").asDouble(-1);
        if (variance < 0 || confidence < 0 || confidence > 1) {
            return failClosed("HOLD", List.of("EFFECTIVENESS_VARIANCE_OR_CONFIDENCE_INVALID"));
        }

        String decision;
        List<String> reasons = new ArrayList<>();
        if (confidence < EFFECTIVENESS_MIN_CONFIDENCE) {
            decision = "INCONCLUSIVE";
            reasons.add("CONFIDENCE_BELOW_THRESHOLD:" + confidence);
        } else {
            boolean fpWorse = after.get("false_positive_rate") - before.get("false_positive_rate") > EFFECTIVENESS_TOLERANCE;
            boolean fnWorse = after.get("false_negative_rate") - before.get("false_negative_rate") > EFFECTIVENESS_TOLERANCE;
            boolean recallWorse = before.get("recall") - after.get("recall") > EFFECTIVENESS_TOLERANCE;
            boolean precisionWorse = before.get("precision") - after.get("precision") > EFFECTIVENESS_TOLERANCE;
            if (fpWorse) reasons.add("FALSE_POSITIVE_RATE_REGRESSED");
            if (fnWorse) reasons.add("FALSE_NEGATIVE_RATE_REGRESSED");
            if (recallWorse) reasons.add("RECALL_REGRESSED");
            if (precisionWorse) reasons.add("PRECISION_REGRESSED");

            if (fpWorse || fnWorse || recallWorse || precisionWorse) {
                decision = "REGRESSION";
            } else {
                boolean fpBetter = before.get("false_positive_rate") - after.get("false_positive_rate") > EFFECTIVENESS_TOLERANCE;
                boolean fnBetter = before.get("false_negative_rate") - after.get("false_negative_rate") > EFFECTIVENESS_TOLERANCE;
                boolean recallBetter = after.get("recall") - before.get("recall") > EFFECTIVENESS_TOLERANCE;
                boolean precisionBetter = after.get("precision") - before.get("precision") > EFFECTIVENESS_TOLERANCE;
                decision = (fpBetter || fnBetter || recallBetter || precisionBetter) ? "IMPROVED" : "EQUIVALENT";
            }
        }

        Map<String, Object> out = base("ONSURE_LEARNING_EFFECTIVENESS_REPORT", targetId);
        out.put("report_id", reportId);
        out.put("candidate_id", candidateId);
        out.put("learning_epoch", learningEpoch);
        out.put("benchmark_id", benchmarkId);
        out.put("before", Map.copyOf(before));
        out.put("after", Map.copyOf(after));
        out.put("variance", variance);
        out.put("confidence", confidence);
        out.put("decision", decision);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    private Map<String, Double> requiredMetricSet(JsonNode node) {
        if (!node.isObject()) return null;
        Map<String, Double> metrics = new LinkedHashMap<>();
        for (String field : EFFECTIVENESS_METRIC_FIELDS) {
            double value = node.path(field).asDouble(-1);
            if (value < 0) return null;
            metrics.put(field, value);
        }
        return metrics;
    }

    private static final List<String> ASSURANCE_LEVELS = List.of(
            "AL0_UNASSESSED", "AL1_EXECUTED", "AL2_EVIDENCE_BOUND",
            "AL3_INDEPENDENTLY_REPERFORMED", "AL4_QUALIFIED", "AL5_PRODUCTION_BOUND_CURRENT");

    /**
     * assurance-strength-ceiling.v1.schema.json real computation (FR-META-049 Assurance Strength
     * Dimension: "상위 결과는 필수 Critical Child의 최저 strength/currentness ceiling을 넘을 수
     * 없다" -- a parent result can never exceed the LOWEST strength/currentness ceiling among its
     * required Critical Children). JSON Schema's enum can validate each individual level is one
     * of the 6 named values, but it cannot compare levels ORDINALLY across fields. This operation
     * computes the real ceiling: effective_assurance_level is the MINIMUM (weakest) level among
     * claimed_assurance_level and every critical child's level -- a caller cannot claim a higher
     * parent-level than its weakest required child, regardless of what is declared.
     */
    private Map<String, Object> assuranceStrengthCeilingCompute(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String subjectId = requiredText(request, "subject_id");
        String claimedLevel = requiredText(request, "claimed_assurance_level");
        if (!ASSURANCE_LEVELS.contains(claimedLevel)) {
            return failClosed("HOLD", List.of("ASSURANCE_LEVEL_INVALID:" + claimedLevel));
        }

        JsonNode childrenNode = request.path("critical_children");
        if (!childrenNode.isArray() || childrenNode.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("ASSURANCE_CRITICAL_CHILDREN_REQUIRED"));
        }

        int minRank = ASSURANCE_LEVELS.indexOf(claimedLevel);
        String ceilingSourceChildId = null;
        java.util.LinkedHashSet<String> childIds = new java.util.LinkedHashSet<>();
        for (JsonNode childNode : childrenNode) {
            String childId = requiredText(childNode, "child_id");
            if (!childIds.add(childId)) return failClosed("HOLD", List.of("DUPLICATE_CHILD_ID:" + childId));
            String childLevel = requiredText(childNode, "assurance_level");
            int childRank = ASSURANCE_LEVELS.indexOf(childLevel);
            if (childRank < 0) return failClosed("HOLD", List.of("ASSURANCE_LEVEL_INVALID:" + childLevel));
            if (childRank < minRank) {
                minRank = childRank;
                ceilingSourceChildId = childId;
            }
        }

        String effectiveLevel = ASSURANCE_LEVELS.get(minRank);
        boolean ceilingApplied = ceilingSourceChildId != null;

        Map<String, Object> out = base("ONSURE_ASSURANCE_STRENGTH_CEILING", targetId);
        out.put("subject_id", subjectId);
        out.put("claimed_assurance_level", claimedLevel);
        out.put("effective_assurance_level", effectiveLevel);
        out.put("ceiling_source_child_id", ceilingApplied ? ceilingSourceChildId : "none");
        out.put("decision", ceilingApplied ? "CEILING_APPLIED" : "CLAIM_WITHIN_CEILING");
        return immutable(out);
    }

    private static final Set<String> RESIDENCY_ASSET_TYPES =
            Set.of("TRAINING_DATA", "VALIDATION_DATA", "DERIVED_LEARNING_ASSET");
    private static final Set<String> RESIDENCY_OPERATIONS =
            Set.of("STORE", "PROCESS", "REPLICATE", "CROSS_REGION_AGGREGATE");

    /**
     * data-residency-check.v1.schema.json real computation (FR-LEARN-054 Data Residency /
     * Cross-region Learning: "학습·검증 데이터와 파생 학습자산의... region 이동은 policy/
     * contract/jurisdiction에 따라 제한한다. 허용되지 않은 cross-region 학습·집계를 금지한다").
     * JSON Schema can validate current_region and allowed_regions are well-formed but cannot
     * check current_region is actually a MEMBER of allowed_regions, nor apply the requirement's
     * own stricter rule for aggregation specifically. This operation computes decision for real:
     * a current_region outside allowed_regions is always FORBIDDEN regardless of operation type;
     * a CROSS_REGION_AGGREGATE additionally requires cross_region_aggregation_authorized=true
     * even when the region itself is allowed for plain STORE/PROCESS/REPLICATE, closing the
     * requirement's own named stricter case for aggregation.
     */
    private Map<String, Object> dataResidencyCheck(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String assetId = requiredText(request, "asset_id");
        String assetType = requiredText(request, "asset_type");
        if (!RESIDENCY_ASSET_TYPES.contains(assetType)) {
            return failClosed("HOLD", List.of("RESIDENCY_ASSET_TYPE_INVALID:" + assetType));
        }
        String operation = requiredText(request, "operation");
        if (!RESIDENCY_OPERATIONS.contains(operation)) {
            return failClosed("HOLD", List.of("RESIDENCY_OPERATION_INVALID:" + operation));
        }
        String currentRegion = requiredText(request, "current_region");
        String jurisdictionBasis = requiredText(request, "jurisdiction_basis");

        JsonNode allowedRegionsNode = request.path("allowed_regions");
        if (!allowedRegionsNode.isArray() || allowedRegionsNode.isEmpty()) {
            return failClosed("INPUT_REQUIRED", List.of("RESIDENCY_ALLOWED_REGIONS_REQUIRED"));
        }
        List<String> allowedRegions = stringList(allowedRegionsNode);
        boolean crossRegionAuthorized = request.path("cross_region_aggregation_authorized").asBoolean(false);

        List<String> reasons = new ArrayList<>();
        String decision;
        if (!allowedRegions.contains(currentRegion)) {
            decision = "FORBIDDEN";
            reasons.add("REGION_NOT_IN_ALLOWED_LIST:" + currentRegion);
        } else if ("CROSS_REGION_AGGREGATE".equals(operation) && !crossRegionAuthorized) {
            decision = "FORBIDDEN";
            reasons.add("CROSS_REGION_AGGREGATION_NOT_SEPARATELY_AUTHORIZED");
        } else {
            decision = "ALLOWED";
        }

        Map<String, Object> out = base("ONSURE_DATA_RESIDENCY_CHECK", targetId);
        out.put("asset_id", assetId);
        out.put("asset_type", assetType);
        out.put("operation", operation);
        out.put("current_region", currentRegion);
        out.put("allowed_regions", allowedRegions);
        out.put("cross_region_aggregation_authorized", crossRegionAuthorized);
        out.put("jurisdiction_basis", jurisdictionBasis);
        out.put("decision", decision);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    private static final List<String> PROVIDER_CHARACTERISTIC_FIELDS = List.of(
            "model_alias", "safety_filter_digest", "tool_semantics_digest", "context_window",
            "rate_limit", "output_policy_digest", "routing_target");

    /**
     * provider-drift-observation.v1.schema.json real computation (34 SS11): a provider staying
     * under the same name/id is never sufficient to keep currentness -- every characteristic field
     * is compared independently between baseline and observed, and any difference is a real
     * material change forcing REASSESSMENT_REQUIRED, never silently CURRENT.
     */
    private Map<String, Object> providerDriftCheck(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String observationId = requiredText(request, "observation_id");
        String providerId = requiredText(request, "provider_id");
        JsonNode baselineNode = request.path("baseline");
        JsonNode observedNode = request.path("observed");

        List<String> changedFields = new ArrayList<>();
        Map<String, Object> baseline = new LinkedHashMap<>();
        Map<String, Object> observed = new LinkedHashMap<>();
        for (String field : PROVIDER_CHARACTERISTIC_FIELDS) {
            JsonNode baselineValue = baselineNode.path(field);
            JsonNode observedValue = observedNode.path(field);
            if (baselineValue.isMissingNode() || observedValue.isMissingNode()) {
                return failClosed("INPUT_REQUIRED", List.of("PROVIDER_CHARACTERISTIC_FIELD_MISSING:" + field));
            }
            baseline.put(field, baselineValue);
            observed.put(field, observedValue);
            if (!baselineValue.equals(observedValue)) changedFields.add(field);
        }

        boolean materialChange = !changedFields.isEmpty();
        String currentnessState = materialChange ? "REASSESSMENT_REQUIRED" : "CURRENT";

        Map<String, Object> out = base("PROVIDER_DRIFT_OBSERVATION", targetId);
        out.put("observation_id", observationId);
        out.put("provider_id", providerId);
        out.put("material_change", materialChange);
        out.put("changed_fields", List.copyOf(changedFields));
        out.put("currentness_state", currentnessState);
        out.put("decision", materialChange ? "HOLD" : "NON_FINAL");
        return immutable(out);
    }

    private static final List<String> AGENT_DEPENDENCY_AXES = List.of(
            "model_id", "provider_id", "prompt_digest", "oracle_id", "knowledge_source_id");

    /**
     * multi-agent-corroboration.v1.schema.json real computation (34 SS9.4/9.5): agent agreement is
     * corroboration, never Ground Truth. Common-mode failure risk is computed for real -- any
     * dependency axis (model/provider/prompt/oracle/knowledge source) shared by two or more agents
     * caps agreement_strength below INDEPENDENT_GROUND_TRUTH -- and even with zero shared axes,
     * majority agreement alone never reaches INDEPENDENT_GROUND_TRUTH without the caller supplying
     * a genuinely separate independent_oracle_confirmed evidence flag; N agents voting the same way
     * is not itself an Executable Oracle/Ground Truth source.
     */
    private Map<String, Object> multiAgentCorroborationCheck(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String corroborationId = requiredText(request, "corroboration_id");
        String subjectId = requiredText(request, "subject_id");
        JsonNode conclusionsNode = request.path("agent_conclusions");
        if (!conclusionsNode.isArray() || conclusionsNode.size() < 2) {
            return failClosed("INPUT_REQUIRED", List.of("MULTI_AGENT_CORROBORATION_REQUIRES_AT_LEAST_TWO_AGENTS"));
        }
        boolean independentOracleConfirmed = request.path("independent_oracle_confirmed").asBoolean(false);

        java.util.LinkedHashSet<String> agentIds = new java.util.LinkedHashSet<>();
        java.util.LinkedHashSet<String> distinctConclusions = new java.util.LinkedHashSet<>();
        Map<String, java.util.LinkedHashSet<String>> valuesByAxis = new LinkedHashMap<>();
        for (String axis : AGENT_DEPENDENCY_AXES) valuesByAxis.put(axis, new java.util.LinkedHashSet<>());

        for (JsonNode row : conclusionsNode) {
            String agentId = requiredText(row, "agent_id");
            if (!agentIds.add(agentId)) return failClosed("HOLD", List.of("DUPLICATE_AGENT_CONCLUSION:" + agentId));
            String conclusion = row.path("conclusion").asText("");
            if (!Set.of("PASS", "FAIL", "BLOCKED", "HOLD", "NOT_RUN", "INCONCLUSIVE").contains(conclusion)) {
                return failClosed("HOLD", List.of("AGENT_CONCLUSION_INVALID:" + agentId));
            }
            distinctConclusions.add(conclusion);
            for (String axis : AGENT_DEPENDENCY_AXES) {
                String value = row.path(axis).asText("");
                if (value.isBlank()) return failClosed("INPUT_REQUIRED", List.of("AGENT_AXIS_MISSING:" + agentId + ":" + axis));
                valuesByAxis.get(axis).add(value);
            }
        }

        List<String> sharedAxes = new ArrayList<>();
        for (String axis : AGENT_DEPENDENCY_AXES) {
            if (valuesByAxis.get(axis).size() < agentIds.size()) sharedAxes.add(axis);
        }
        boolean commonModeRisk = !sharedAxes.isEmpty();
        boolean allAgree = distinctConclusions.size() == 1;

        String agreementStrength;
        if (!allAgree) {
            agreementStrength = "NONE";
        } else if (!commonModeRisk && independentOracleConfirmed) {
            agreementStrength = "INDEPENDENT_GROUND_TRUTH";
        } else {
            agreementStrength = "CORROBORATION_ONLY";
        }

        Map<String, Object> out = base("MULTI_AGENT_CORROBORATION", targetId);
        out.put("corroboration_id", corroborationId);
        out.put("subject_id", subjectId);
        out.put("common_mode_risk", commonModeRisk);
        out.put("shared_dependency_axes", List.copyOf(sharedAxes));
        out.put("agreement_strength", agreementStrength);
        out.put("decision", "INDEPENDENT_GROUND_TRUTH".equals(agreementStrength) ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    private HazardLedger hazardLedger() {
        return new HazardLedger(workspaceRoot.resolve(".onsure/assurance/hazards"));
    }

    private SessionLedger sessionLedger() {
        return new SessionLedger(workspaceRoot.resolve(".onsure/assurance/sessions"));
    }

    /**
     * session-lifecycle-disposition.v1.schema.json real creation (NFR-SESSION, 03 Security
     * Review). A session that would push the user's active session count over session_ceiling
     * evicts the single oldest active session before creation succeeds -- the ceiling is
     * SessionLedger's own real, enforced invariant, not merely reported here.
     */
    private Map<String, Object> sessionCreate(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String sessionId = requiredText(request, "session_id");
        String userId = requiredText(request, "user_id");
        int sessionCeiling = request.path("session_ceiling").asInt(-1);
        if (sessionCeiling < 1) {
            return failClosed("HOLD", List.of("SESSION_CEILING_MUST_BE_POSITIVE"));
        }
        Instant expiresAt;
        try {
            expiresAt = Instant.parse(requiredText(request, "expires_at"));
        } catch (Exception malformed) {
            return failClosed("HOLD", List.of("SESSION_EXPIRY_MALFORMED"));
        }

        SessionLedger ledger = sessionLedger();
        Instant now = Instant.now();
        int activeCountBefore = ledger.activeSessionsFor(userId, now).size();
        SessionLedger.CreateResult result;
        try {
            result = ledger.create(sessionId, userId, expiresAt, sessionCeiling, now);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }

        Map<String, Object> out = base("SESSION_LIFECYCLE_DISPOSITION", targetId);
        out.put("session_id", result.session().sessionId());
        out.put("user_id", result.session().userId());
        out.put("issued_at", result.session().issuedAt());
        out.put("expires_at", result.session().expiresAt());
        out.put("status", result.session().status());
        out.put("session_ceiling", sessionCeiling);
        out.put("active_session_count_before_create", activeCountBefore);
        out.put("evicted_session_id", result.evictedSessionId());
        return immutable(out);
    }

    /** Real expiry check at read time (Instant comparison) -- never trusts a caller-declared claim. */
    private Map<String, Object> sessionCheckValid(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String sessionId = requiredText(request, "session_id");
        String userId = requiredText(request, "user_id");
        boolean valid = sessionLedger().isValid(sessionId, userId, Instant.now());

        Map<String, Object> out = base("SESSION_VALIDITY_CHECK", targetId);
        out.put("session_id", sessionId);
        out.put("user_id", userId);
        out.put("session_valid", valid);
        return immutable(out);
    }

    /** hazard.v1.schema.json real creation: always starts IDENTIFIED, creator bound to the caller. */
    private Map<String, Object> hazardCreate(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String hazardId = requiredText(request, "hazard_id");
        HazardLedger.DispositionEvent event;
        try {
            event = hazardLedger().create(hazardId, identity.actorId());
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("HAZARD_CREATED", targetId);
        out.put("hazard_id", hazardId);
        out.put("disposition", event.disposition());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * hazard.v1.schema.json real disposition advancement (127 SS1.3/1.6). safety_authority_confirmed
     * is never a caller-declared claim -- it is computed here from the authenticated caller's real
     * roles (APPROVER, the same role tier this codebase already uses for other elevated-authority
     * approval gates), not trusted from the request body.
     */
    private Map<String, Object> hazardAdvance(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String hazardId = requiredText(request, "hazard_id");
        String toDisposition = requiredText(request, "to_disposition");
        String justification = requiredText(request, "justification");
        boolean safetyAuthorityConfirmed = identity.roles().contains(AuthenticatedWorkflowIdentity.Role.APPROVER)
                || identity.roles().contains(AuthenticatedWorkflowIdentity.Role.ADMIN);

        HazardLedger.DispositionEvent event;
        try {
            event = hazardLedger().advance(hazardId, toDisposition, identity.actorId(), justification, safetyAuthorityConfirmed);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("HAZARD_ADVANCED", targetId);
        out.put("hazard_id", hazardId);
        out.put("disposition", event.disposition());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private AppealLedger appealLedger() {
        return new AppealLedger(workspaceRoot.resolve(".onsure/assurance/appeals"));
    }

    /**
     * appeal-case.v1.schema.json real filing (127 SS2.3). appellant_principal_id is bound to the
     * authenticated caller; challenged_decision_principal_id is a caller-supplied reference to a
     * different, already-existing principal from a past decision. An appellant challenging their
     * own decision is rejected outright.
     */
    private Map<String, Object> appealFile(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String appealCaseId = requiredText(request, "appeal_case_id");
        String challengedDecisionPrincipalId = requiredText(request, "challenged_decision_principal_id");
        String reasonCode = requiredText(request, "reason_code");

        AppealLedger.FileResult result;
        try {
            result = appealLedger().file(appealCaseId, identity.actorId(), challengedDecisionPrincipalId, reasonCode);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("APPEAL_FILED", targetId);
        out.put("appeal_case_id", result.appealCaseId());
        out.put("status", "FILED");
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * 127 SS2.6 real independence enforcement: the reviewer can never be the challenged decision's
     * original principal, checked against the value recorded at filing time (never a caller
     * re-declaration of who the original principal was).
     */
    private Map<String, Object> appealAssignReviewer(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String appealCaseId = requiredText(request, "appeal_case_id");
        String reviewerPrincipalId = requiredText(request, "reviewer_principal_id");

        AppealLedger.Event event;
        try {
            event = appealLedger().assignReviewer(appealCaseId, reviewerPrincipalId);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("APPEAL_REVIEWER_ASSIGNED", targetId);
        out.put("appeal_case_id", appealCaseId);
        out.put("status", event.status());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /** 127 SS2.7: new evidence is appended, never replaces a prior submission's bytes. */
    private Map<String, Object> appealSubmitEvidence(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String appealCaseId = requiredText(request, "appeal_case_id");
        String evidenceRefSha256 = requiredDigest(request, "evidence_ref_sha256");

        AppealLedger.Event event;
        try {
            event = appealLedger().submitEvidence(appealCaseId, identity.actorId(), evidenceRefSha256);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("APPEAL_EVIDENCE_SUBMITTED", targetId);
        out.put("appeal_case_id", appealCaseId);
        out.put("status", event.status());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> appealTransition(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String appealCaseId = requiredText(request, "appeal_case_id");
        String toStatus = requiredText(request, "to_status");
        String detail = request.path("detail").asText("");

        AppealLedger.Event event;
        try {
            event = appealLedger().transition(appealCaseId, toStatus, identity.actorId(), detail);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("APPEAL_TRANSITIONED", targetId);
        out.put("appeal_case_id", appealCaseId);
        out.put("status", event.status());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * 127 SS2.5 real decision recording: only the actually-assigned reviewer's decision is
     * accepted -- the original decision principal (or anyone else) attempting to decide is
     * rejected. The original decision itself is never deleted or overwritten (append-only ledger);
     * this event is a new, superseding generation on top of it.
     */
    private Map<String, Object> appealDecide(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String appealCaseId = requiredText(request, "appeal_case_id");
        String decision = requiredText(request, "appeal_decision");
        if (!Set.of("UPHOLD", "REVERSE", "MODIFY", "REASSESSMENT_REQUIRED", "INCONCLUSIVE").contains(decision)) {
            return failClosed("HOLD", List.of("APPEAL_DECISION_INVALID"));
        }
        String rationale = requiredText(request, "rationale");

        AppealLedger.Event event;
        try {
            event = appealLedger().decide(appealCaseId, decision, identity.actorId(), rationale);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("APPEAL_DECIDED", targetId);
        out.put("appeal_case_id", appealCaseId);
        out.put("status", event.status());
        out.put("appeal_decision", decision);
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private OffboardingLedger offboardingLedger() {
        return new OffboardingLedger(workspaceRoot.resolve(".onsure/assurance/offboarding"));
    }

    /** FR-FRESH-003 real filing: "offboarding 완료 전 tenant identifier 재사용 금지" is enforced by
     * OffboardingLedger.request itself (rejects a second request while one is in progress or done). */
    private Map<String, Object> offboardingRequest(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String tenantId = requiredText(request, "offboarding_tenant_id");
        OffboardingLedger.StageEvent event;
        try {
            event = offboardingLedger().request(tenantId, identity.actorId());
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("OFFBOARDING_REQUESTED", targetId);
        out.put("offboarding_tenant_id", tenantId);
        out.put("stage", event.stage());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /** FR-FRESH-003 real stage advancement: no stage may be skipped, and legal hold is checked for
     * real at the deletion step rather than trusted as a caller-declared outcome. */
    private Map<String, Object> offboardingAdvance(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String tenantId = requiredText(request, "offboarding_tenant_id");
        String toStage = requiredText(request, "to_stage");
        boolean legalHold = request.path("legal_hold").asBoolean(false);
        String detail = request.path("detail").asText("");

        OffboardingLedger.StageEvent event;
        try {
            event = offboardingLedger().advance(tenantId, toStage, identity.actorId(), legalHold, detail);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("OFFBOARDING_ADVANCED", targetId);
        out.put("offboarding_tenant_id", tenantId);
        out.put("stage", event.stage());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /**
     * engagement-authorization.v1.schema.json real scope check (FR-FRESH-001): default-deny --
     * every dimension (revocation, time window, endpoint, test class, forbidden-action overlap,
     * rate ceiling) is checked for real against the proposed action, and any single mismatch
     * BLOCKS. Holding an ONSure license is never itself evidence of authorization; this operation
     * never infers ALLOWED from anything other than an explicit, current, matching engagement.
     */
    private Map<String, Object> engagementCheckScope(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String engagementId = requiredText(request, "engagement_id");
        boolean revoked = request.path("revoked").asBoolean(true);
        List<String> allowedEndpoints = stringList(request.path("allowed_endpoints"));
        List<String> allowedTestClasses = stringList(request.path("allowed_test_classes"));
        List<String> forbiddenActions = stringList(request.path("forbidden_actions"));
        int rateCeiling = request.path("rate_ceiling_per_minute").asInt(0);

        Instant startsAt;
        Instant endsAt;
        Instant requestedAt;
        try {
            startsAt = Instant.parse(requiredText(request, "starts_at"));
            endsAt = Instant.parse(requiredText(request, "ends_at"));
            requestedAt = Instant.parse(request.path("requested_at").asText(Instant.now().toString()));
        } catch (Exception malformed) {
            return failClosed("BLOCKED", List.of("ENGAGEMENT_TIMESTAMP_MALFORMED"));
        }

        String proposedEndpoint = requiredText(request, "proposed_endpoint");
        String proposedTestClass = requiredText(request, "proposed_test_class");
        int proposedRate = request.path("proposed_rate_per_minute").asInt(0);

        List<String> reasons = new ArrayList<>();
        if (revoked) reasons.add("ENGAGEMENT_REVOKED");
        if (requestedAt.isBefore(startsAt) || requestedAt.isAfter(endsAt)) reasons.add("OUTSIDE_TIME_WINDOW");
        if (!allowedEndpoints.contains(proposedEndpoint)) reasons.add("ENDPOINT_NOT_IN_SCOPE:" + proposedEndpoint);
        if (!allowedTestClasses.contains(proposedTestClass)) reasons.add("TEST_CLASS_NOT_IN_SCOPE:" + proposedTestClass);
        if (forbiddenActions.contains(proposedTestClass)) reasons.add("TEST_CLASS_FORBIDDEN:" + proposedTestClass);
        if (proposedRate > rateCeiling) reasons.add("RATE_CEILING_EXCEEDED");

        Map<String, Object> out = base("ENGAGEMENT_SCOPE_CHECK", targetId);
        out.put("engagement_id", engagementId);
        out.put("proposed_endpoint", proposedEndpoint);
        out.put("proposed_test_class", proposedTestClass);
        out.put("scope_decision", reasons.isEmpty() ? "ALLOWED" : "BLOCKED");
        out.put("reasons", List.copyOf(reasons));
        out.put("decision", reasons.isEmpty() ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    /**
     * accessible-claim-render.v1.schema.json real compliance computation (FR-FRESH-002): a missing
     * or blank screen_reader_label, a color-only signal, or a localization fallback that drops
     * limitation disclosure each independently make the render NON_COMPLIANT -- computed for real
     * from the supplied fields, never assumed compliant by default.
     */
    private Map<String, Object> accessibilityValidateRender(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String renderId = requiredText(request, "render_id");
        Set<String> validDecisionTokens = Set.of("PASS", "FAIL", "BLOCKED", "HOLD", "NOT_RUN", "INCONCLUSIVE", "UNKNOWN");
        String decisionToken = request.path("decision_token").asText("");
        boolean colorOnlySignal = request.path("color_only_signal").asBoolean(true);
        String screenReaderLabel = request.path("screen_reader_label").asText("");
        boolean localizationFallbackUsed = request.path("localization_fallback_used").asBoolean(false);
        boolean limitationDisclosurePresent = request.path("limitation_disclosure_present").asBoolean(false);

        List<String> reasons = new ArrayList<>();
        if (!validDecisionTokens.contains(decisionToken)) reasons.add("DECISION_TOKEN_MISSING_OR_INVALID");
        if (colorOnlySignal) reasons.add("COLOR_ONLY_SIGNAL_NOT_PERMITTED");
        if (screenReaderLabel.isBlank()) reasons.add("SCREEN_READER_LABEL_MISSING");
        if (localizationFallbackUsed && !limitationDisclosurePresent) reasons.add("FALLBACK_DROPPED_LIMITATION_DISCLOSURE");

        Map<String, Object> out = base("ACCESSIBLE_CLAIM_RENDER_VALIDATION", targetId);
        out.put("render_id", renderId);
        out.put("compliant", reasons.isEmpty());
        out.put("reasons", List.copyOf(reasons));
        out.put("decision", reasons.isEmpty() ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    /**
     * migration-reconciliation-report.v1.schema.json real dual-read comparison (137 SS27 Batch 8):
     * old_representation and new_representation are compared field by field for real, never
     * trusted as "the mapping is documented as complete." Any field present in the old
     * representation that is missing or structurally different in the new one is a real
     * divergence. loss_classification is RECOVERABLE only when every diverged field is one the
     * caller has declared reconstructible (reconstructible_fields); otherwise UNRECOVERABLE.
     * cutover_eligible is never true while an UNRECOVERABLE loss remains.
     */
    private Map<String, Object> migrationReconcile(JsonNode request) {
        String targetId = requiredText(request, "target_id");
        String reconciliationId = requiredText(request, "reconciliation_id");
        String subjectId = requiredText(request, "subject_id");
        JsonNode oldRepresentation = request.path("old_representation");
        JsonNode newRepresentation = request.path("new_representation");
        if (!oldRepresentation.isObject() || !newRepresentation.isObject()) {
            return failClosed("INPUT_REQUIRED", List.of("MIGRATION_REPRESENTATIONS_REQUIRED"));
        }
        Set<String> reconstructibleFields = new java.util.HashSet<>(stringList(request.path("reconstructible_fields")));

        List<String> divergedFields = new ArrayList<>();
        var fieldNames = oldRepresentation.fieldNames();
        while (fieldNames.hasNext()) {
            String field = fieldNames.next();
            JsonNode oldValue = oldRepresentation.get(field);
            JsonNode newValue = newRepresentation.get(field);
            if (newValue == null || !oldValue.equals(newValue)) divergedFields.add(field);
        }

        boolean diverged = !divergedFields.isEmpty();
        boolean reconstructionAttempted = diverged && !reconstructibleFields.isEmpty();
        String lossClassification;
        if (!diverged) {
            lossClassification = "NONE";
        } else if (reconstructibleFields.containsAll(divergedFields)) {
            lossClassification = "RECOVERABLE";
        } else {
            lossClassification = "UNRECOVERABLE";
        }
        boolean cutoverEligible = !"UNRECOVERABLE".equals(lossClassification);

        Map<String, Object> out = base("MIGRATION_RECONCILIATION_REPORT", targetId);
        out.put("reconciliation_id", reconciliationId);
        out.put("subject_id", subjectId);
        out.put("old_representation_digest", digest(oldRepresentation));
        out.put("new_representation_digest", digest(newRepresentation));
        out.put("diverged", diverged);
        out.put("diverged_fields", List.copyOf(divergedFields));
        out.put("loss_classification", lossClassification);
        out.put("reconstruction_attempted", reconstructionAttempted);
        out.put("cutover_eligible", cutoverEligible);
        out.put("decision", cutoverEligible ? "NON_FINAL" : "HOLD");
        return immutable(out);
    }

    private ContractSelectorLedger contractSelectorLedger() {
        return new ContractSelectorLedger(workspaceRoot.resolve(".onsure/assurance/contract-selectors"));
    }

    /**
     * contract-active-selector.candidate.v2.schema.json real cutover: blocked outright unless the
     * caller supplies a genuine reconciliation result with cutover_eligible=true -- an
     * UNRECOVERABLE loss forbids cutover regardless of any other claim.
     */
    private Map<String, Object> migrationCutover(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String contractFamily = requiredText(request, "contract_family");
        String toVersion = requiredText(request, "to_version");
        String toContractDigest = requiredDigest(request, "to_contract_digest");
        String migrationReceiptSha256 = requiredDigest(request, "migration_receipt_sha256");
        boolean divergenceResolved = request.path("cutover_eligible").asBoolean(false);

        ContractSelectorLedger.SelectorEntry entry;
        try {
            entry = contractSelectorLedger().cutover(contractFamily, toVersion, toContractDigest, migrationReceiptSha256, divergenceResolved);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("CONTRACT_SELECTOR_CUTOVER", targetId);
        out.put("contract_family", contractFamily);
        out.put("active_version", entry.activeVersion());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    /** Real reversion to the immediately preceding selector entry -- always allowed, no gate. */
    private Map<String, Object> migrationRollback(JsonNode request) throws Exception {
        String targetId = requiredText(request, "target_id");
        String contractFamily = requiredText(request, "contract_family");

        ContractSelectorLedger.SelectorEntry entry;
        try {
            entry = contractSelectorLedger().rollback(contractFamily);
        } catch (IllegalArgumentException invalid) {
            return failClosed("HOLD", List.of(invalid.getMessage()));
        }
        Map<String, Object> out = base("CONTRACT_SELECTOR_ROLLBACK", targetId);
        out.put("contract_family", contractFamily);
        out.put("active_version", entry.activeVersion());
        out.put("decision", "NON_FINAL");
        return immutable(out);
    }

    private Map<String, Object> externalEffectNotImplemented(String operation) {
        return failClosed("BLOCKED", List.of("EXTERNAL_EFFECT_RUNTIME_NOT_WIRED:" + operation));
    }

    private Map<String, Object> envelope(String operation, Map<String, Object> result) {
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("contract", CONTRACT);
        envelope.put("operation", operation);
        envelope.put("authenticated_actor", identity.actorId());
        envelope.put("authenticated_tenant", identity.tenantId());
        envelope.put("result", result);
        envelope.put("server_bound_context", true);
        envelope.put("assurance_class", "SELF_VALIDATION_NONFINAL");
        envelope.put("independent_authority", false);
        envelope.put("final_claim_allowed", false);
        envelope.put("created_at", Instant.now().toString());
        envelope.put("envelope_sha256", digest(envelope));
        return immutable(envelope);
    }

    private Map<String, Object> base(String artifactType, String targetId) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("artifact_type", artifactType);
        out.put("target_id", targetId);
        out.put("tenant_id", identity.tenantId());
        out.put("actor_id", identity.actorId());
        out.put("created_at", Instant.now().toString());
        out.put("final_claim_allowed", false);
        return out;
    }

    private Map<String, Object> failClosed(String decision, List<String> reasons) {
        Map<String, Object> out = base("FAIL_CLOSED_RESULT", "UNKNOWN_TARGET");
        out.put("decision", decision);
        out.put("reasons", List.copyOf(reasons));
        return immutable(out);
    }

    private void requireServerBoundContext(JsonNode request) {
        String target = requiredText(request, "target_id");
        String project = requiredText(request, "project_id");
        if (!target.equals(request.path("_authorized_target_id").asText(""))) {
            throw new SecurityException("SEMANTIC_V2_AUTHORIZED_TARGET_MISMATCH");
        }
        if (!project.equals(request.path("_authorized_project_id").asText(""))) {
            throw new SecurityException("SEMANTIC_V2_AUTHORIZED_PROJECT_MISMATCH");
        }
        authorizedRoot(request, "_authorized_target_root");
    }

    private Path requiredPathWithin(JsonNode request, String field, String rootField) {
        String text = requiredText(request, field);
        Path root = authorizedRoot(request, rootField);
        Path candidate = workspaceRoot.resolve(text).normalize();
        if (!candidate.startsWith(root) || !Files.isRegularFile(candidate)) {
            throw new SecurityException("SEMANTIC_V2_PATH_OUTSIDE_AUTHORIZED_ROOT:" + field);
        }
        return candidate;
    }

    private Path authorizedRoot(JsonNode request, String rootField) {
        String value = request.path(rootField).asText("");
        if (value.isBlank()) throw new SecurityException("SEMANTIC_V2_SERVER_BOUND_ROOT_REQUIRED:" + rootField);
        Path root = Path.of(value).toAbsolutePath().normalize();
        if (!root.startsWith(workspaceRoot) || !Files.isDirectory(root)) {
            throw new SecurityException("SEMANTIC_V2_SERVER_BOUND_ROOT_INVALID:" + rootField);
        }
        return root;
    }

    private String requiredText(JsonNode request, String field) {
        String value = request.path(field).asText("");
        if (value.isBlank()) throw new IllegalArgumentException("SEMANTIC_V2_FIELD_REQUIRED:" + field);
        return value;
    }

    private String requiredDigest(JsonNode request, String field) {
        String value = requiredText(request, field);
        if (!value.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("SEMANTIC_V2_DIGEST_INVALID:" + field);
        return value;
    }

    private List<String> stringList(JsonNode node) {
        if (!node.isArray()) return List.of();
        ArrayList<String> values = new ArrayList<>();
        for (JsonNode item : node) if (item.isTextual() && !item.asText().isBlank()) values.add(item.asText());
        return List.copyOf(values);
    }

    private Map<String, Object> immutable(Map<String, Object> value) {
        return java.util.Collections.unmodifiableMap(new LinkedHashMap<>(value));
    }

    private String digest(Object value) {
        try {
            return Hashing.sha256(mapper.writeValueAsBytes(value));
        } catch (Exception e) {
            throw new IllegalStateException("SEMANTIC_V2_DIGEST_FAILED", e);
        }
    }
}
