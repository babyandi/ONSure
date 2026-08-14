package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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
            "assurance.certificate.issue");
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
