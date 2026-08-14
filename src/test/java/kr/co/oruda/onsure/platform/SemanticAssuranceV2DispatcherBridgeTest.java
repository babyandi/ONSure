package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SemanticAssuranceV2DispatcherBridgeTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private SemanticAssuranceV2DispatcherBridge bridge;

    @BeforeEach
    void registerOwnedTarget() throws Exception {
        AuthenticatedWorkflowIdentity admin = identity("tenant-a", "admin-a");
        bridge = new SemanticAssuranceV2DispatcherBridge(temp, admin);
        Files.createDirectories(temp.resolve("target-src"));
        Files.writeString(temp.resolve("target-src/subject.txt"), "subject");
        Files.writeString(temp.resolve("outside.txt"), "other-tenant-or-target");
        bridge.dispatch("project.register-workspace", request(Map.of(
                "workspace_id", "workspace-1", "workspace_name", "Workspace")));
        bridge.dispatch("project.register", request(Map.of(
                "workspace_id", "workspace-1", "project_id", "project-1", "project_name", "Project")));
        bridge.dispatch("project.register-target", request(Map.of(
                "project_id", "project-1",
                "target_id", "target-1",
                "target_name", "Target",
                "target_type", "GENERAL_SOFTWARE",
                "source_root", temp.resolve("target-src").toString())));
    }

    @Test
    void reperformanceMayReadOnlyInsideServerResolvedTargetRoot() throws Exception {
        String digest = Hashing.file(temp.resolve("target-src/subject.txt"));
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) bridge.dispatch(
                "semantic.reperformance.run", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "subject_path", "target-src/subject.txt",
                        "subject_sha256", digest,
                        "oracle_state", "PASS"))).get("result");
        assertEquals("NON_FINAL", ((Map<?, ?>) result.get("result")).get("decision"));

        SecurityException escape = assertThrows(SecurityException.class, () -> bridge.dispatch(
                "semantic.reperformance.run", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "subject_path", "outside.txt",
                        "subject_sha256", Hashing.file(temp.resolve("outside.txt")),
                        "oracle_state", "PASS"))));
        assertEquals("SEMANTIC_V2_TARGET_PATH_ESCAPE:subject_path", escape.getMessage());
    }

    @Test
    void crossTenantSemanticOperationIsDeniedInsideDurableOwnershipTransaction() throws Exception {
        SemanticAssuranceV2DispatcherBridge tenantB = new SemanticAssuranceV2DispatcherBridge(
                temp, identity("tenant-b", "admin-b"));
        SecurityException denied = assertThrows(SecurityException.class, () -> tenantB.dispatch(
                "semantic.denominator.discover", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "items", java.util.List.of(Map.of(
                                "item_id", "REQ-1", "item_sha256", "a".repeat(64)))))));
        assertEquals("CROSS_TENANT_RESOURCE_ACCESS_DENIED:project:project-1", denied.getMessage());
    }

    @Test
    void callerCannotInjectServerAuthorityFields() {
        SecurityException denied = assertThrows(SecurityException.class, () -> bridge.dispatch(
                "semantic.denominator.discover", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "_authorized_target_root", temp.toString(),
                        "items", java.util.List.of()))));
        assertEquals("SEMANTIC_V2_SERVER_AUTHORITY_FIELD_SUBSTITUTION:_authorized_target_root", denied.getMessage());
    }

    @Test
    void deploymentVerificationStaysBlockedUntilDeploymentIsTargetBound() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> result = (Map<String, Object>) bridge.dispatch(
                "deployment.verify-installed", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "verified_artifact_path", "target-src/subject.txt",
                        "deployed_artifact_path", "outside.txt"))).get("result");
        assertEquals("BLOCKED", result.get("decision"));
        assertEquals("TARGET_BOUND_DEPLOYMENT_IDENTITY_NOT_AVAILABLE",
                ((java.util.List<?>) result.get("reasons")).get(0));
    }

    @Test
    void deploymentVerificationSucceedsOnceRegisteredAndInstalled() throws Exception {
        bridge.dispatch("deployment.register-target", request(Map.of(
                "project_id", "project-1",
                "target_id", "target-1",
                "deployment_target_id", "deploy-1",
                "environment_class", "PROD",
                "deployment_root", temp.resolve("deployment-1").toString())));

        Files.createDirectories(temp.resolve("package-src"));
        Files.writeString(temp.resolve("package-src/subject.txt"), "subject");
        DeploymentPackageBuilder.build(
                temp.resolve("package-src"), temp.resolve("package-build"),
                DeploymentProfile.ON_PREMISES, null, null);
        new DeploymentInstallationService(temp.resolve("deployment-1"))
                .install(temp.resolve("package-build"), "v1", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "deployment.verify-installed", request(Map.of(
                        "project_id", "project-1",
                        "target_id", "target-1",
                        "deployment_target_id", "deploy-1",
                        "verified_artifact_path", "target-src/subject.txt",
                        "deployed_artifact_path", "deployment-1/versions/v1/subject.txt"))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("NON_FINAL", result.get("decision"));
        assertEquals(true, result.get("identity_equal"));
    }

    @Test
    void evidenceGraphValidatesAnAcyclicPopulationWithARealDerivationEdge() throws Exception {
        Map<String, Object> node1 = evidenceNode("node-1", "PRIMARY", "66570ff05a2074043084d4aca94293ef067530dde94ff4e92b8d8459253eb779", null);
        Map<String, Object> node2 = evidenceNode("node-2", "DERIVED", "93ef37c6157138222b21a42be52183d08d75cd4fed49c1cbba571b06a69e39a4", null);
        Map<String, Object> edge = Map.of(
                "edge_id", "edge-1", "edge_type", "DERIVES_FROM",
                "source_node_id", "node-2", "target_node_id", "node-1",
                "source_digest", "93ef37c6157138222b21a42be52183d08d75cd4fed49c1cbba571b06a69e39a4",
                "target_digest", "66570ff05a2074043084d4aca94293ef067530dde94ff4e92b8d8459253eb779",
                "rule_id", "RULE-DERIVE-1", "evidence_digest", "092cd5e29db964781ac7520814627b0e5615fb9b04d4d2e8ce0eed8bdc97d318");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.evidence-graph.validate", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "evidence_graph_id", "graph-1",
                        "nodes", List.of(node1, node2), "edges", List.of(edge)))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("NON_FINAL", result.get("decision"));
        assertEquals(List.of(), result.get("violations"));
        assertEquals(2, ((Number) result.get("node_count")).intValue());
        assertEquals(1, ((Number) result.get("edge_count")).intValue());
    }

    @Test
    void evidenceGraphRejectsACycleInTheSupersessionSubgraph() throws Exception {
        Map<String, Object> nodeX = evidenceNode("node-x", "PRIMARY", "acc23c0064112f4a6c6a3e43c84c61c99fcc5212a50500099c18949cb5d7e000", null);
        Map<String, Object> nodeY = evidenceNode("node-y", "PRIMARY", "8a14663daa887134b9091eb444fa6055d5b0ba19dbef43674476984bdd59776d", null);
        Map<String, Object> edgeXtoY = supersedesEdge("edge-x-y", "node-x", "node-y",
                "acc23c0064112f4a6c6a3e43c84c61c99fcc5212a50500099c18949cb5d7e000",
                "8a14663daa887134b9091eb444fa6055d5b0ba19dbef43674476984bdd59776d");
        Map<String, Object> edgeYtoX = supersedesEdge("edge-y-x", "node-y", "node-x",
                "8a14663daa887134b9091eb444fa6055d5b0ba19dbef43674476984bdd59776d",
                "acc23c0064112f4a6c6a3e43c84c61c99fcc5212a50500099c18949cb5d7e000");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.evidence-graph.validate", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "evidence_graph_id", "graph-2",
                        "nodes", List.of(nodeX, nodeY), "edges", List.of(edgeXtoY, edgeYtoX)))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("EVIDENCE_GRAPH_CYCLE_DETECTED"), result.get("violations"));
    }

    @Test
    void evidenceGraphRejectsAnEdgeWhoseDigestNoLongerMatchesItsNode() throws Exception {
        Map<String, Object> node1 = evidenceNode("node-1", "PRIMARY", "66570ff05a2074043084d4aca94293ef067530dde94ff4e92b8d8459253eb779", null);
        Map<String, Object> node2 = evidenceNode("node-2", "DERIVED", "93ef37c6157138222b21a42be52183d08d75cd4fed49c1cbba571b06a69e39a4", null);
        Map<String, Object> edge = Map.of(
                "edge_id", "edge-1", "edge_type", "DERIVES_FROM",
                "source_node_id", "node-2", "target_node_id", "node-1",
                "source_digest", "93ef37c6157138222b21a42be52183d08d75cd4fed49c1cbba571b06a69e39a4",
                "target_digest", "c74e255491c53f792c2efe8b79b00da8e06f2deef1d0dc59c5ccd76ae9e0a408",
                "rule_id", "RULE-DERIVE-1", "evidence_digest", "092cd5e29db964781ac7520814627b0e5615fb9b04d4d2e8ce0eed8bdc97d318");

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.evidence-graph.validate", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "evidence_graph_id", "graph-3",
                        "nodes", List.of(node1, node2), "edges", List.of(edge)))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("EDGE_TARGET_DIGEST_MISMATCH:edge-1"), result.get("violations"));
    }

    @Test
    void evidenceGraphRejectsADerivedNodeWithNoDerivationEdge() throws Exception {
        Map<String, Object> node1 = evidenceNode("node-1", "PRIMARY", "66570ff05a2074043084d4aca94293ef067530dde94ff4e92b8d8459253eb779", null);
        Map<String, Object> node2 = evidenceNode("node-2", "DERIVED", "93ef37c6157138222b21a42be52183d08d75cd4fed49c1cbba571b06a69e39a4", null);

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.evidence-graph.validate", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "evidence_graph_id", "graph-4",
                        "nodes", List.of(node1, node2), "edges", List.of()))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("DERIVED_NODE_WITHOUT_DERIVATION_EDGE:node-2"), result.get("violations"));
    }

    private Map<String, Object> evidenceNode(String nodeId, String origin, String contentDigest, String supersededBy) {
        Map<String, Object> node = new java.util.LinkedHashMap<>();
        node.put("node_id", nodeId);
        node.put("node_type", "OBSERVATION");
        node.put("content_digest", contentDigest);
        node.put("origin_class", origin);
        node.put("tenant_id", "tenant-a");
        if (supersededBy != null) node.put("superseded_by_node_id", supersededBy);
        return node;
    }

    private Map<String, Object> supersedesEdge(
            String edgeId, String source, String target, String sourceDigest, String targetDigest) {
        return Map.of(
                "edge_id", edgeId, "edge_type", "SUPERSEDES",
                "source_node_id", source, "target_node_id", target,
                "source_digest", sourceDigest, "target_digest", targetDigest,
                "rule_id", "RULE-SUPERSEDE-1", "evidence_digest", "092cd5e29db964781ac7520814627b0e5615fb9b04d4d2e8ce0eed8bdc97d318");
    }

    @Test
    void compositionPassesOnlyWhenEveryHardChildPassesAndNothingIsOutstanding() throws Exception {
        Map<?, ?> result = compositionResult("comp-1", List.of(
                inputResult("subject-a", "HARD", "PASS"),
                inputResult("subject-b", "SOFT", "FAIL"),
                inputResult("subject-c", "INFORMATIONAL", "FAIL")));
        assertEquals("PASS", result.get("decision"));
        assertEquals(List.of(), result.get("ceiling_reasons"));
    }

    @Test
    void compositionFailsWhenAHardChildFails() throws Exception {
        Map<?, ?> result = compositionResult("comp-2", List.of(
                inputResult("subject-a", "HARD", "FAIL"),
                inputResult("subject-b", "HARD", "PASS")));
        assertEquals("FAIL", result.get("decision"));
        assertEquals(List.of("HARD_EDGE_CHILD_FAIL:subject-a"), result.get("ceiling_reasons"));
    }

    @Test
    void compositionIsBlockedNotFailedWhenAHardChildIsOnlyBlocked() throws Exception {
        Map<?, ?> result = compositionResult("comp-3", List.of(
                inputResult("subject-a", "HARD", "BLOCKED")));
        assertEquals("BLOCKED", result.get("decision"));
    }

    @Test
    void compositionHoldsWhenAnyChildIsStillOutstandingRegardlessOfEdgeClass() throws Exception {
        Map<?, ?> result = compositionResult("comp-4", List.of(
                inputResult("subject-a", "HARD", "PASS"),
                inputResult("subject-b", "INFORMATIONAL", "HOLD")));
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("CHILD_HOLD:subject-b"), result.get("ceiling_reasons"));
    }

    @Test
    void compositionRejectsDuplicateSubjectIds() throws Exception {
        Map<?, ?> result = compositionResult("comp-5", List.of(
                inputResult("subject-a", "HARD", "PASS"),
                inputResult("subject-a", "HARD", "PASS")));
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("DUPLICATE_COMPOSITION_SUBJECT:subject-a"), result.get("reasons"));
    }

    @Test
    void certificateIssuanceNeverClaimsPassBecauseNoCurrentnessVerifierIsWired() throws Exception {
        Map<?, ?> certificate = certificateResult("PASS");
        assertEquals("HOLD", certificate.get("decision"));
        assertEquals("UNKNOWN", certificate.get("currentness_state_at_issue"));
        assertEquals(false, certificate.get("final_claim_allowed"));
    }

    @Test
    void certificateIssuanceIsBlockedWhenCompositionDidNotPass() throws Exception {
        Map<?, ?> certificate = certificateResult("FAIL");
        assertEquals("BLOCKED", certificate.get("decision"));
    }

    @Test
    void certificateSignatureIsCryptographicallyVerifiableAgainstItsOwnEmbeddedKey() throws Exception {
        Map<?, ?> certificate = certificateResult("PASS");
        @SuppressWarnings("unchecked")
        Map<String, Object> signature = (Map<String, Object>) certificate.get("signature");
        byte[] publicKeyBytes = java.util.Base64.getDecoder().decode((String) certificate.get("issuer_public_key_der_base64"));
        java.security.PublicKey publicKey = java.security.KeyFactory.getInstance("Ed25519")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(publicKeyBytes));

        Map<String, Object> forVerify = new java.util.LinkedHashMap<>();
        certificate.forEach((key, value) -> forVerify.put((String) key, value));
        forVerify.put("signature", signature.get("signature"));
        assertEquals(true, kr.co.oruda.onsure.assurance.LocalReceiptCrypto.verify(forVerify, publicKey));
    }

    private Map<String, Object> inputResult(String subjectId, String edgeClass, String childDecision) {
        return Map.of(
                "subject_id", subjectId, "edge_propagation_class", edgeClass,
                "child_decision", childDecision,
                "result_digest", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1");
    }

    private Map<?, ?> compositionResult(String compositionId, List<Map<String, Object>> inputs) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.composition.compute", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "composition_id", compositionId, "input_results", inputs))).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    private Map<?, ?> certificateResult(String compositionDecision) throws Exception {
        Map<String, Object> body = Map.ofEntries(
                Map.entry("project_id", "project-1"), Map.entry("target_id", "target-1"),
                Map.entry("certificate_id", "cert-1"), Map.entry("subject_id", "subject-a"),
                Map.entry("subject_digest", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1"),
                Map.entry("product_version", "1.0.0"),
                Map.entry("target_manifest_digest", "db40281222139a3cc745f264e56507a56bebaeeae19ead23000d88948f9b8faf"),
                Map.entry("requirement_epoch", "EPOCH::REQUIREMENT::0001"),
                Map.entry("composition_snapshot_digest", "70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b"),
                Map.entry("final_lock_digest", "fb3ad22cb997c7e8e3c4d27ffc0bf0dff7acdb7bf03b72b66687ca05c133a47b"),
                Map.entry("assurance_tier", "TIER_2_STANDARD"),
                Map.entry("composition_decision", compositionDecision),
                Map.entry("verifier_identity_ref", "admin-a"));
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.certificate.issue", request(body)).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    private AuthenticatedWorkflowIdentity identity(String tenant, String actor) {
        return new AuthenticatedWorkflowIdentity(
                "organization", tenant, "workspace", actor,
                Set.of(AuthenticatedWorkflowIdentity.Role.ADMIN), "LOCAL",
                AuthenticatedWorkflowIdentity.AuthenticationMethod.SIGNED_ENTERPRISE_IDENTITY);
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }
}
