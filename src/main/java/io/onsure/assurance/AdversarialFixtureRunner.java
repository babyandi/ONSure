package io.onsure.assurance;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HexFormat;

public final class AdversarialFixtureRunner {
    private static final String D = "a".repeat(64);
    private static final String D2 = "b".repeat(64);
    private static final String COMMIT = "c".repeat(40);
    public record FixtureResult(String id, Decision decision, List<String> reasons) {}

    public List<FixtureResult> run(InputStream fixtureJson) throws IOException {
        JsonNode root = new ObjectMapper().readTree(fixtureJson);
        List<FixtureResult> results = new ArrayList<>();
        for (JsonNode fixture : root.path("fixtures")) results.add(execute(fixture.path("id").asText()));
        return List.copyOf(results);
    }

    FixtureResult execute(String id) {
        ValidationResult result = switch (id) {
            case "A01_STAGE_SKIP" -> transition("DESIGN_REVIEWED", "OBUILDER_BUILT", false, "r", "t", "a", "run-1", "run-2", Decision.PASS);
            case "A02_MUTABLE_REF" -> new ReceiptEnvelopeValidator().validate(receipt("SOURCE", "ONSURE_RUNTIME", "main", Decision.PASS));
            case "A03_SELF_APPROVAL" -> new OMakerPlanValidator().validate(new OMakerPlanContext("OMAKER_AGENT", "OMAKER_AGENT", D, D, D, D, D, List.of("MODIFY:src/A.java"), List.of("unit"), List.of("control"), true, true, true));
            case "A04_PLAN_SWAP" -> builder(D, D2, D, D, false, false, true, true, List.of("MODIFY:src/A.java"), List.of("MODIFY:src/A.java"));
            case "A05_PATCH_DRIFT" -> builder(D, D, D, D, false, false, true, true, List.of("MODIFY:src/A.java"), List.of("MODIFY:src/A.java", "CREATE:backdoor.sh"));
            case "A06_UNLOCKED_DEPENDENCY" -> new SupplyChainValidator().validateDependencyLock(false);
            case "A07_NETWORK_WITHOUT_PERMIT" -> builder(D, D, D, D, true, false, true, true, List.of("MODIFY:src/A.java"), List.of("MODIFY:src/A.java"));
            case "A08_ARTIFACT_SWAP" -> builder(D, D, D, D2, false, false, true, true, List.of("MODIFY:src/A.java"), List.of("MODIFY:src/A.java"));
            case "A09_RECEIPT_REPLAY" -> replay();
            case "A10_SHARED_SIGNING_KEY" -> transition("TESTED", "OTESTER_VERIFIED", false, "shared", "shared", "audit", "run-1", "run-2", Decision.PASS);
            case "A11_RUNTIME_WRITES_AUDIT" -> new ReceiptEnvelopeValidator().validate(receipt("OAUDIT", "ONSURE_RUNTIME", COMMIT, Decision.PASS));
            case "A12_NOT_RUN_AS_PASS" -> transition("PATCHED", "OBUILDER_BUILT", false, "r", "t", "a", "run-1", "run-2", Decision.NOT_RUN);
            case "A13_OPEN_HIGH_FINDING" -> transition("OAUDIT_VERIFIED", "PUBLICATION_ELIGIBLE", true, "r", "t", "a", "run-1", "run-2", Decision.PASS);
            case "A14_REGRESSION_DUPLICATE" -> new SupplyChainValidator().validateRegression("same", "same", "PASS", "PASS", D, D);
            case "A15_REGRESSION_DIVERGENCE" -> new SupplyChainValidator().validateRegression("run-1", "run-2", "PASS", "FAIL", D, D2);
            case "A16_POLICY_DRIFT" -> new SupplyChainValidator().validatePolicyBinding(D, D2);
            case "A17_EXPIRED_PERMIT" -> new PermitValidator().validate(new PermitContext("p", "w", D, D, Set.of("BUILD"), Instant.EPOCH, Instant.EPOCH.plusSeconds(1), false), "w", D, D, Set.of("BUILD"), Instant.EPOCH.plusSeconds(2));
            case "A18_UNSIGNED_STANDALONE_UPDATE" -> new SupplyChainValidator().validateStandaloneUpdate(false, true);
            case "A19_MISSING_ROLLBACK" -> new SupplyChainValidator().validateStandaloneUpdate(true, false);
            case "A20_EXTERNAL_EMBEDDED_DIVERGENCE" -> new SupplyChainValidator().validateImplementationEquivalence(D, D2);
            default -> new ValidationResult(Decision.NOT_RUN, List.of("UNKNOWN_FIXTURE"));
        };
        return new FixtureResult(id, result.decision(), result.violations());
    }

    private ValidationResult transition(String from, String to, boolean open, String rk, String tk, String ak, String run1, String run2, Decision d) {
        return new StateTransitionValidator().validate(new TransitionContext(from, to, Set.of("in"), Set.of("out"), open, rk, tk, ak, run1, run2, d));
    }

    private ValidationResult builder(String approvedPlan, String consumedPlan, String buildArtifact, String runtimeArtifact, boolean networkUsed, boolean networkPermit, boolean isolated, boolean reproducible, List<String> declared, List<String> actual) {
        try {
            Path root = Files.createTempDirectory("onsure-obuilder-fixture-");
            var plan = evidence(root, "approved-plan", approvedPlan);
            var consumed = evidence(root, "consumed-plan", consumedPlan);
            var source = evidence(root, "source", "source");
            var artifact = evidence(root, "artifact", buildArtifact);
            var runtime = evidence(root, "runtime", runtimeArtifact);
            var dependency = evidence(root, "dependency", "dependency");
            var sbom = evidence(root, "sbom", "sbom");
            var provenance = evidence(root, "provenance", "provenance");
            var toolchain = evidence(root, "toolchain", "toolchain");
            var log = evidence(root, "build-log", "build-log");
            String runId = "run-" + root.getFileName();
            var permit = networkUsed && networkPermit
                    ? new OBuilderBuildContext.NetworkPermitReceipt(
                            "permit-1", runId, source.claimedSha256(), true,
                            Instant.now().plusSeconds(300), "permit-key", true)
                    : null;
            var execution = new OBuilderBuildContext.BuildExecutionReceipt(
                    runId, source.claimedSha256(), toolchain.claimedSha256(), log.claimedSha256(),
                    isolated, networkUsed, reproducible, reproducible, artifact.claimedSha256(),
                    artifact.claimedSha256(), "builder-key", true);
            ValidationResult result = new OBuilderBuildValidator().validate(new OBuilderBuildContext(
                    plan, consumed, source, artifact, runtime, dependency, sbom, provenance,
                    toolchain, log, declared, actual, permit, execution));
            return result;
        } catch (Exception exception) {
            return ValidationResult.fail(List.of("OBUILDER_FIXTURE_EXECUTION_ERROR"));
        }
    }

    private static OBuilderBuildContext.EvidenceFile evidence(Path root, String name, String value)
            throws Exception {
        Path file = root.resolve(name);
        Files.writeString(file, value);
        String digest = HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));
        return new OBuilderBuildContext.EvidenceFile(file, digest);
    }

    private ReceiptEnvelope receipt(String type, String authority, String sourceRef, Decision decision) {
        return new ReceiptEnvelope("r-" + type, type, authority, "w", sourceRef, D, "p", "S1", "S2", decision, Instant.now(), List.of(D), List.of(D2), Map.of("result", "x"), "key", "sig", D);
    }

    private ValidationResult replay() {
        ReceiptReplayLedger ledger = new ReceiptReplayLedger();
        ReceiptEnvelope first = receipt("OTESTER", "OTESTER_AGENT", COMMIT, Decision.PASS);
        ledger.consume(first);
        ReceiptEnvelope mutatedWorkspace = new ReceiptEnvelope(first.receiptId(), first.receiptType(), first.authority(), "other", first.subjectCommitSha(), first.policyDigest(), first.permitId(), first.previousState(), first.nextState(), first.decision(), first.issuedAt(), first.inputDigests(), first.outputDigests(), first.claims(), first.keyId(), first.signature(), first.selfHash());
        return ledger.consume(mutatedWorkspace);
    }
}
