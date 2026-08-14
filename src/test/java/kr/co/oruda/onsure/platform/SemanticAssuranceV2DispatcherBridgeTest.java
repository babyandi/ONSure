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

    @Test
    void revocationCheckIsClearBeforeAnyRevocationIsIssued() throws Exception {
        Map<?, ?> check = revocationCheck("CERTIFICATE", "cert-never-revoked");
        assertEquals("CLEAR", check.get("revocation_state"));
    }

    @Test
    void issuedRevocationIsFoundByCheck() throws Exception {
        Map<?, ?> issued = revocationIssue("rev-1", "CERTIFICATE", "cert-1", null);
        assertEquals("NON_FINAL", issued.get("decision"));

        Map<?, ?> check = revocationCheck("CERTIFICATE", "cert-1");
        assertEquals("REVOKED", check.get("revocation_state"));
        assertEquals("rev-1", check.get("revocation_id"));
        assertEquals("CRITICAL", check.get("severity"));
    }

    @Test
    void duplicateRevocationIdIsRejected() throws Exception {
        revocationIssue("rev-2", "CERTIFICATE", "cert-2", null);
        Map<?, ?> duplicate = revocationIssue("rev-2", "CERTIFICATE", "cert-2", null);
        assertEquals("HOLD", duplicate.get("decision"));
        assertEquals(List.of("REVOCATION_ID_ALREADY_EXISTS:rev-2"), duplicate.get("reasons"));
    }

    @Test
    void aSupersedingRevocationReplacesTheEarlierOneInTheActiveCheck() throws Exception {
        Map<?, ?> original = revocationIssue("rev-3", "CERTIFICATE", "cert-3", null);
        String originalDigest = (String) original.get("revocation_sha256");
        revocationIssue("rev-4", "CERTIFICATE", "cert-3", originalDigest);

        Map<?, ?> check = revocationCheck("CERTIFICATE", "cert-3");
        assertEquals("REVOKED", check.get("revocation_state"));
        assertEquals("rev-4", check.get("revocation_id"));
    }

    @Test
    void offlineTrustBundleWithinGraceIsNonFinal() throws Exception {
        Map<?, ?> bundle = offlineBundle("HIGH", "SECURE_CLOCK", java.time.Instant.now().minusSeconds(10), 3600);
        assertEquals("OFFLINE_CURRENT_WITHIN_GRACE", bundle.get("offline_status"));
        assertEquals("NON_FINAL", bundle.get("decision"));
    }

    @Test
    void offlineTrustBundlePastFourTimesGraceIsBlocked() throws Exception {
        Map<?, ?> bundle = offlineBundle("HIGH", "SECURE_CLOCK", java.time.Instant.now().minusSeconds(20_000), 3600);
        assertEquals("OFFLINE_BLOCKED", bundle.get("offline_status"));
        assertEquals("HOLD", bundle.get("decision"));
    }

    @Test
    void offlineTrustBundleNeverSyncedIsBlocked() throws Exception {
        Map<?, ?> bundle = offlineBundle("HIGH", "SECURE_CLOCK", null, 3600);
        assertEquals("OFFLINE_BLOCKED", bundle.get("offline_status"));
    }

    @Test
    void untrustedTimeForcesBlockedEvenWhenElapsedTimeWouldOtherwiseBeWithinGrace() throws Exception {
        Map<?, ?> bundle = offlineBundle("UNTRUSTED", "SECURE_CLOCK", java.time.Instant.now().minusSeconds(1), 3600);
        assertEquals("OFFLINE_BLOCKED", bundle.get("offline_status"));
    }

    @Test
    void localClockOnlyCannotClaimHighTrust() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.offline-trust-bundle.evaluate", request(offlineBundleRequest(
                        "HIGH", "LOCAL_OS_CLOCK_ONLY", java.time.Instant.now(), 3600))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("OFFLINE_BUNDLE_LOCAL_CLOCK_TRUST_LEVEL_TOO_HIGH"), result.get("reasons"));
    }

    @Test
    void offlineTrustBundleSignatureIsCryptographicallyVerifiable() throws Exception {
        Map<?, ?> bundle = offlineBundle("HIGH", "SECURE_CLOCK", java.time.Instant.now().minusSeconds(10), 3600);
        @SuppressWarnings("unchecked")
        Map<String, Object> signature = (Map<String, Object>) bundle.get("bundle_signature");
        byte[] publicKeyBytes = java.util.Base64.getDecoder().decode((String) bundle.get("issuer_public_key_der_base64"));
        java.security.PublicKey publicKey = java.security.KeyFactory.getInstance("Ed25519")
                .generatePublic(new java.security.spec.X509EncodedKeySpec(publicKeyBytes));

        Map<String, Object> forVerify = new java.util.LinkedHashMap<>();
        bundle.forEach((key, value) -> forVerify.put((String) key, value));
        forVerify.remove("bundle_signature");
        forVerify.remove("issuer_public_key_der_base64");
        forVerify.put("signature", signature.get("signature"));
        assertEquals(true, kr.co.oruda.onsure.assurance.LocalReceiptCrypto.verify(forVerify, publicKey));
    }

    private Map<String, Object> offlineBundleRequest(
            String trustLevel, String source, java.time.Instant lastSyncOrNull, int gracePeriodSeconds) {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("project_id", "project-1");
        body.put("target_id", "target-1");
        body.put("bundle_id", "bundle-" + java.util.UUID.randomUUID());
        body.put("trusted_root_key_ids", List.of("root-key-1"));
        body.put("key_registry_snapshot_digest", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1");
        body.put("policy_snapshot_digest", "70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b");
        body.put("validator_qualification_snapshot_digest", "07614cb0e158c8e8c223883f5a9173e813b3c0c302fe8d42a9aaf85300810606");
        body.put("revocation_snapshot_digest", "db40281222139a3cc745f264e56507a56bebaeeae19ead23000d88948f9b8faf");
        body.put("trusted_time_evidence", Map.of(
                "source", source, "observed_at", java.time.Instant.now().toString(), "trust_level", trustLevel));
        body.put("grace_period_seconds", gracePeriodSeconds);
        if (lastSyncOrNull != null) body.put("last_online_sync_at", lastSyncOrNull.toString());
        return body;
    }

    private Map<?, ?> offlineBundle(
            String trustLevel, String source, java.time.Instant lastSyncOrNull, int gracePeriodSeconds) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.offline-trust-bundle.evaluate",
                request(offlineBundleRequest(trustLevel, source, lastSyncOrNull, gracePeriodSeconds))).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    private Map<?, ?> revocationIssue(String revocationId, String subjectType, String subjectId, String supersedes) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("project_id", "project-1");
        body.put("target_id", "target-1");
        body.put("revocation_id", revocationId);
        body.put("subject", Map.of(
                "subject_type", subjectType, "subject_id", subjectId,
                "subject_sha256", "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1"));
        body.put("reason", "COMPROMISED_KEY");
        body.put("severity", "CRITICAL");
        body.put("triggering_evidence", List.of(Map.of(
                "id", "finding-1", "sha256", "70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b")));
        body.put("authority", Map.of(
                "principal_profile_sha256", "07614cb0e158c8e8c223883f5a9173e813b3c0c302fe8d42a9aaf85300810606",
                "authority_epoch", "EPOCH::AUTHORITY::0001"));
        body.put("propagation_scope", Map.of(
                "scope_type", "TARGET",
                "scope_digest", "db40281222139a3cc745f264e56507a56bebaeeae19ead23000d88948f9b8faf"));
        body.put("revocation_epoch", "EPOCH::REVOCATION::0001");
        if (supersedes != null) body.put("supersedes_revocation_sha256", supersedes);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.revocation.issue", request(body)).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    private Map<?, ?> revocationCheck(String subjectType, String subjectId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.revocation.check", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "subject", Map.of("subject_type", subjectType, "subject_id", subjectId)))).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    @Test
    void sodRecordStageBlocksTheSameActorUnderAnEnforcedRegulatedPolicy() throws Exception {
        sodRecordStage(bridge, "req-1", "DEVELOP", "REGULATED_FINANCIAL", "ENFORCED");
        SecurityException violation = assertThrows(SecurityException.class,
                () -> sodRecordStage(bridge, "req-1", "VERIFY", "REGULATED_FINANCIAL", "ENFORCED"));
        assertEquals("SOD_VIOLATION:actor_already_performed:DEVELOP:cannot_also_perform:VERIFY", violation.getMessage());
    }

    @Test
    void sodRecordStageAllowsDifferentActorsUnderTheSameEnforcedPolicy() throws Exception {
        SemanticAssuranceV2DispatcherBridge secondActor = new SemanticAssuranceV2DispatcherBridge(
                temp, identity("tenant-a", "admin-b"));
        sodRecordStage(bridge, "req-2", "DEVELOP", "REGULATED_FINANCIAL", "ENFORCED");
        Map<?, ?> verify = sodRecordStage(secondActor, "req-2", "VERIFY", "REGULATED_FINANCIAL", "ENFORCED");
        assertEquals(false, verify.get("advisory_violation"));

        Map<?, ?> check = sodCheck("req-2");
        assertEquals(true, check.get("clean"));
        assertEquals(List.of(), check.get("actors_with_multiple_stages"));
    }

    @Test
    void sodStandardIndustryCannotDeclareEnforcedPolicy() throws Exception {
        Map<?, ?> result = sodRecordStage(bridge, "req-3", "DEVELOP", "STANDARD", "ENFORCED");
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("SOD_STANDARD_INDUSTRY_CANNOT_ENFORCE"), result.get("reasons"));
    }

    @Test
    void sodAdvisoryPolicyRecordsTheConflictAndSodCheckReportsIt() throws Exception {
        sodRecordStage(bridge, "req-4", "DEVELOP", "STANDARD", "ADVISORY");
        Map<?, ?> verify = sodRecordStage(bridge, "req-4", "VERIFY", "STANDARD", "ADVISORY");
        assertEquals(true, verify.get("advisory_violation"));

        Map<?, ?> check = sodCheck("req-4");
        assertEquals(false, check.get("clean"));
        assertEquals(List.of("admin-a"), check.get("actors_with_multiple_stages"));
    }

    private Map<?, ?> sodRecordStage(
            SemanticAssuranceV2DispatcherBridge caller, String improvementRequestId, String stage,
            String industryClass, String sodEnforcement) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) caller.dispatch(
                "assurance.sod.record-stage", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "improvement_request_id", improvementRequestId, "stage", stage,
                        "policy_profile", Map.of(
                                "industry_class", industryClass, "sod_enforcement", sodEnforcement)))).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    private Map<?, ?> sodCheck(String improvementRequestId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.sod.check", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "improvement_request_id", improvementRequestId))).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    @Test
    void fourEyesIsNotSatisfiedByASingleApproverEvenWhenPolicyRequiresIt() throws Exception {
        Map<?, ?> result = fourEyesApprove(bridge, "subject-1");
        assertEquals(false, result.get("satisfied"));
        assertEquals(1L, result.get("distinct_approver_count"));
    }

    @Test
    void fourEyesIsSatisfiedOnceTwoDistinctActorsApprove() throws Exception {
        SemanticAssuranceV2DispatcherBridge secondActor = new SemanticAssuranceV2DispatcherBridge(
                temp, identity("tenant-a", "admin-b"));
        fourEyesApprove(bridge, "subject-2");
        Map<?, ?> second = fourEyesApprove(secondActor, "subject-2");
        assertEquals(true, second.get("satisfied"));

        Map<?, ?> check = fourEyesCheck("subject-2");
        assertEquals(true, check.get("satisfied"));
        assertEquals(List.of("admin-a", "admin-b"), check.get("approver_actor_ids"));
    }

    @Test
    void fourEyesRejectsTheSameActorApprovingTwice() throws Exception {
        fourEyesApprove(bridge, "subject-3");
        SecurityException denied = assertThrows(SecurityException.class, () -> fourEyesApprove(bridge, "subject-3"));
        assertEquals("FOUR_EYES_SAME_ACTOR_CANNOT_COUNT_TWICE:admin-a", denied.getMessage());
    }

    @Test
    void fourEyesRecordApprovalIsANoOpWhenPolicyDoesNotRequireIt() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.four-eyes.record-approval", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "approval_subject_id", "subject-4",
                        "policy_profile", Map.of("four_eyes_required", false)))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("HOLD", result.get("decision"));
        assertEquals(List.of("FOUR_EYES_NOT_REQUIRED_BY_POLICY"), result.get("reasons"));
    }

    private Map<?, ?> fourEyesApprove(SemanticAssuranceV2DispatcherBridge caller, String approvalSubjectId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) caller.dispatch(
                "assurance.four-eyes.record-approval", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "approval_subject_id", approvalSubjectId,
                        "policy_profile", Map.of("four_eyes_required", true)))).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    private Map<?, ?> fourEyesCheck(String approvalSubjectId) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.four-eyes.check", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "approval_subject_id", approvalSubjectId))).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    @Test
    void delegationGrantRejectsADelegatorWhoDoesNotHoldTheRole() throws Exception {
        SecurityException denied = assertThrows(SecurityException.class, () -> bridge.dispatch(
                "assurance.delegation.grant", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "delegation_id", "del-1", "delegate_actor_id", "admin-b",
                        "role", "AUDITOR", "expires_at", java.time.Instant.now().plusSeconds(3600).toString(),
                        "justification", "coverage"))));
        assertEquals("DELEGATION_DELEGATOR_DOES_NOT_HOLD_ROLE:AUDITOR", denied.getMessage());
    }

    @Test
    void delegationGrantSucceedsAndIsFoundByCheckWhenTheDelegatorHoldsTheRole() throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.delegation.grant", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "delegation_id", "del-2", "delegate_actor_id", "admin-b",
                        "role", "ADMIN", "expires_at", java.time.Instant.now().plusSeconds(3600).toString(),
                        "justification", "coverage while on leave"))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals("NON_FINAL", result.get("decision"));

        @SuppressWarnings("unchecked")
        Map<String, Object> checkEnvelope = (Map<String, Object>) bridge.dispatch(
                "assurance.delegation.check", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "delegate_actor_id", "admin-b", "role", "ADMIN"))).get("result");
        Map<?, ?> check = (Map<?, ?>) checkEnvelope.get("result");
        assertEquals(true, check.get("active"));
    }

    @Test
    void breakGlassEventCannotBeReviewedByItsOwnInvoker() throws Exception {
        bridge.dispatch("assurance.break-glass.invoke", request(Map.of(
                "project_id", "project-1", "target_id", "target-1",
                "event_id", "bg-1", "justification", "production outage")));
        SecurityException denied = assertThrows(SecurityException.class, () -> bridge.dispatch(
                "assurance.break-glass.review", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "event_id", "bg-1", "review_notes", "self-approved"))));
        assertEquals("BREAK_GLASS_REVIEWER_CANNOT_BE_THE_INVOKER", denied.getMessage());
    }

    @Test
    void breakGlassEventIsClosedByADistinctReviewer() throws Exception {
        SemanticAssuranceV2DispatcherBridge reviewer = new SemanticAssuranceV2DispatcherBridge(
                temp, identity("tenant-a", "admin-b"));
        bridge.dispatch("assurance.break-glass.invoke", request(Map.of(
                "project_id", "project-1", "target_id", "target-1",
                "event_id", "bg-2", "justification", "production outage")));

        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) reviewer.dispatch(
                "assurance.break-glass.review", request(Map.of(
                        "project_id", "project-1", "target_id", "target-1",
                        "event_id", "bg-2", "review_notes", "confirmed necessary"))).get("result");
        Map<?, ?> result = (Map<?, ?>) envelope.get("result");
        assertEquals(true, result.get("review_completed"));
    }

    @Test
    void pluginQualificationIsRevokedWhenThePublisherIsRevoked() throws Exception {
        Map<?, ?> result = pluginQualify("plugin-1", true, true, "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1", List.of());
        assertEquals("REVOKED", result.get("qualification_state"));
    }

    @Test
    void pluginQualificationFailsWithAnInvalidPublisherSignature() throws Exception {
        Map<?, ?> result = pluginQualify("plugin-2", false, false, "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1", List.of());
        assertEquals("NOT_QUALIFIED", result.get("qualification_state"));
        assertEquals(List.of("PUBLISHER_SIGNATURE_INVALID"), result.get("reasons"));
    }

    @Test
    void pluginQualificationBlocksAnUndeclaredPrivilege() throws Exception {
        Map<?, ?> result = pluginQualify(
                "plugin-3", true, false, "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1",
                List.of("NETWORK_EGRESS"));
        assertEquals("NOT_QUALIFIED", result.get("qualification_state"));
        assertEquals(List.of("UNDECLARED_PRIVILEGE:NETWORK_EGRESS"), result.get("reasons"));
    }

    @Test
    void pluginQualifiesWhenEveryRequiredPrivilegeIsDeclared() throws Exception {
        Map<?, ?> result = pluginQualify(
                "plugin-4", true, false, "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1",
                List.of("FILESYSTEM_READ"));
        assertEquals("QUALIFIED", result.get("qualification_state"));
        assertEquals(List.of(), result.get("reasons"));
    }

    @Test
    void aChangedArtifactDigestDropsAPreviouslyQualifiedPluginToPending() throws Exception {
        Map<?, ?> first = pluginQualify(
                "plugin-5", true, false, "43ac647142dac29a5a3105ed53d8b08638e06e288044d7228ef8c985ab79dfa1", List.of());
        assertEquals("QUALIFIED", first.get("qualification_state"));

        Map<?, ?> second = pluginQualify(
                "plugin-5", true, false, "70213192283560990cc7315457795d1af358aafdb8d1e97c06cbf21dd03d889b", List.of());
        assertEquals("QUALIFICATION_PENDING", second.get("qualification_state"));
        assertEquals(List.of("ARTIFACT_DIGEST_CHANGED_REQUALIFICATION_REQUIRED"), second.get("reasons"));
    }

    private Map<?, ?> pluginQualify(
            String pluginId, boolean signatureValid, boolean revoked, String artifactDigest,
            List<String> requiredPrivileges) throws Exception {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("project_id", "project-1");
        body.put("target_id", "target-1");
        body.put("plugin_id", pluginId);
        body.put("plugin_version", "1.0.0");
        body.put("publisher_signature_valid", signatureValid);
        body.put("publisher_revoked", revoked);
        body.put("artifact_digest", artifactDigest);
        body.put("required_privileges", requiredPrivileges);
        body.put("access_declarations", Map.of(
                "filesystem", "READ_ONLY_SANDBOX", "network", "NONE", "tool_invocation", List.of()));
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.plugin.qualify", request(body)).get("result");
        return (Map<?, ?>) envelope.get("result");
    }

    @Test
    void aFailedProviderLookupNeverReadsAsClean() throws Exception {
        // doc 52 SS10 negative test: "advisory lookup timeout을 0 vulnerability로 처리" must not happen.
        Map<?, ?> result = reconcile("DEPENDENCY_ADVISORY", "pkg-1", "pkg-1", "0-known-cves", "pkg-1", "0-known-cves", false);
        assertEquals("HOLD", result.get("reconciliation_state"));
        assertEquals(List.of("EXTERNAL_LOOKUP_FAILED_NOT_TREATED_AS_CLEAN"), result.get("reasons"));
    }

    @Test
    void aCiStatusForADifferentCommitIsAConflictNotAnAutomaticPick() throws Exception {
        // doc 52 SS10 negative test: "CI status가 다른 commit에서 온 것".
        Map<?, ?> result = reconcile("CI_STATUS", "commit-abc", "commit-abc", "SUCCESS", "commit-xyz", "SUCCESS", true);
        assertEquals("CONFLICT", result.get("reconciliation_state"));
        assertEquals(List.of("EXTERNAL_STATE_CONFLICT_HOLD:SUBJECT_MISMATCH"), result.get("reasons"));
        assertEquals("HOLD", result.get("decision"));
    }

    @Test
    void aLicenseCachedActiveWhileTheProviderNowSaysRevokedIsAConflict() throws Exception {
        // doc 52 SS6 named example: "license ACTIVE cache이나 remote REVOKED".
        Map<?, ?> result = reconcile("LICENSE_STATUS", "license-1", "license-1", "ACTIVE", "license-1", "REVOKED", true);
        assertEquals("CONFLICT", result.get("reconciliation_state"));
        assertEquals(List.of("EXTERNAL_STATE_CONFLICT_HOLD:VALUE_MISMATCH"), result.get("reasons"));
    }

    @Test
    void matchingLocalAndProviderStateIsConsistent() throws Exception {
        Map<?, ?> result = reconcile("CONTAINER_DIGEST", "image:latest", "image:latest", "sha256:aaa", "image:latest", "sha256:aaa", true);
        assertEquals("CONSISTENT", result.get("reconciliation_state"));
        assertEquals(List.of(), result.get("reasons"));
        assertEquals("NON_FINAL", result.get("decision"));
    }

    private Map<?, ?> reconcile(
            String integrationType, String expectedSubject, String localSubject, String localValue,
            String providerSubject, String providerValue, boolean lookupSucceeded) throws Exception {
        Map<String, Object> providerState = new java.util.LinkedHashMap<>();
        providerState.put("lookup_succeeded", lookupSucceeded);
        if (lookupSucceeded) {
            providerState.put("subject", providerSubject);
            providerState.put("value", providerValue);
        }
        Map<String, Object> body = Map.of(
                "project_id", "project-1", "target_id", "target-1",
                "integration_type", integrationType, "expected_subject", expectedSubject,
                "local_state", Map.of("subject", localSubject, "value", localValue),
                "provider_state", providerState);
        @SuppressWarnings("unchecked")
        Map<String, Object> envelope = (Map<String, Object>) bridge.dispatch(
                "assurance.external-integration.reconcile", request(body)).get("result");
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
