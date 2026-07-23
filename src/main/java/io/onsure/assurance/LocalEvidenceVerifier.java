package io.onsure.assurance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class LocalEvidenceVerifier {
    private final ObjectMapper mapper = new ObjectMapper();

    public ValidationResult verify(Path runRoot) {
        List<String> violations = new ArrayList<>();
        Path fixtureSnapshot = runRoot.resolve("adversarial-transition-fixtures.snapshot.json");
        Path r1Summary = runRoot.resolve("regression-1/test-summary.txt");
        Path r2Summary = runRoot.resolve("regression-2/test-summary.txt");
        Path r1Classes = runRoot.resolve("regression-1/classes.sha256");
        Path r2Classes = runRoot.resolve("regression-2/classes.sha256");
        Path r1Fixtures = runRoot.resolve("regression-1/adversarial-fixtures.tsv");
        Path r2Fixtures = runRoot.resolve("regression-2/adversarial-fixtures.tsv");
        Path r1Evidence = runRoot.resolve("regression-1/evidence.sha256");
        Path r2Evidence = runRoot.resolve("regression-2/evidence.sha256");
        Path otester = runRoot.resolve("otester/receipt.json");
        Path oaudit = runRoot.resolve("oaudit/receipt.json");
        Path registryFile = runRoot.resolve("key-registry.snapshot.json");
        Path runContextFile = runRoot.resolve("run-context.json");
        if (!Files.isRegularFile(fixtureSnapshot)) violations.add("ADVERSARIAL_FIXTURE_SNAPSHOT_MISSING");
        if (!Files.isRegularFile(registryFile)) violations.add("KEY_REGISTRY_SNAPSHOT_MISSING");
        LocalKeyRegistry registry = new LocalKeyRegistry(registryFile);

        LocalRunContext.Context runContext = null;
        LocalRunContext contextReader = new LocalRunContext();
        ValidationResult contextValidation = contextReader.verify(runContextFile);
        if (contextValidation.decision() != Decision.PASS) {
            violations.addAll(contextValidation.violations());
        } else {
            try { runContext = LocalRunContext.read(runContextFile); }
            catch (Exception e) { violations.add("RUN_CONTEXT_UNREADABLE"); }
        }

        requireSame(r1Summary, r2Summary, "REGRESSION_RESULT_DIVERGENCE", violations);
        requireSame(r1Classes, r2Classes, "REGRESSION_ARTIFACT_DIVERGENCE", violations);
        requireSame(r1Fixtures, r2Fixtures, "ADVERSARIAL_FIXTURE_RESULT_DIVERGENCE", violations);
        verifyFixtureReport(r1Fixtures, fixtureSnapshot, violations);
        verifyFixtureReport(r2Fixtures, fixtureSnapshot, violations);
        verifyEvidenceManifest(r1Evidence, List.of(r1Summary, r1Classes, r1Fixtures), violations);
        verifyEvidenceManifest(r2Evidence, List.of(r2Summary, r2Classes, r2Fixtures), violations);

        String expectedOtesterInput = digestOrNull(r2Evidence);
        if (expectedOtesterInput == null) violations.add("REGRESSION_EVIDENCE_DIGEST_MISSING");
        String otesterKeyId = verifyAgentReceipt(
                otester, "OTESTER", expectedOtesterInput, registry, runContext, violations);
        String otesterDigest = digestOrNull(otester);
        String oauditKeyId = verifyAgentReceipt(
                oaudit, "OAUDIT", otesterDigest, registry, runContext, violations);
        if (otesterKeyId != null && Objects.equals(otesterKeyId, oauditKeyId)) {
            violations.add("INDEPENDENCE_KEY_COLLISION");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private String verifyAgentReceipt(Path file, String expectedAuthority, String expectedInputDigest,
            LocalKeyRegistry registry, LocalRunContext.Context runContext, List<String> violations) {
        if (!Files.isRegularFile(file)) {
            violations.add(expectedAuthority + "_RECEIPT_MISSING");
            return null;
        }
        try {
            JsonNode node = mapper.readTree(file.toFile());
            if (!LocalAgentMain.CONTRACT.equals(node.path("contract").asText())) violations.add(expectedAuthority + "_RECEIPT_CONTRACT_MISMATCH");
            if (!expectedAuthority.equals(node.path("authority").asText())) violations.add(expectedAuthority + "_AUTHORITY_MISMATCH");
            if (!"PASS".equals(node.path("decision").asText())) violations.add(expectedAuthority + "_NON_PASS");
            if (!"LOCAL_SEPARATE_JVM".equals(node.path("execution_mode").asText())) violations.add(expectedAuthority + "_PROCESS_BOUNDARY_MISSING");
            if (!LocalRolePolicy.expectedPolicy(expectedAuthority).equals(node.path("role_policy").asText())) violations.add(expectedAuthority + "_ROLE_POLICY_MISMATCH");
            if (!LocalRolePolicy.expectedScope(expectedAuthority).equals(node.path("evidence_scope").asText())) violations.add(expectedAuthority + "_EVIDENCE_SCOPE_MISMATCH");
            if (runContext != null) {
                if (!runContext.runId().equals(node.path("assurance_run_id").asText())) violations.add(expectedAuthority + "_RUN_CONTEXT_ID_MISMATCH");
                if (!runContext.startedAt().toString().equals(node.path("run_started_at").asText())) violations.add(expectedAuthority + "_RUN_CONTEXT_TIME_MISMATCH");
            }
            String inputDigest = node.path("input_digest").asText();
            if (!inputDigest.matches("[0-9a-f]{64}")) violations.add(expectedAuthority + "_INVALID_INPUT_DIGEST");
            if (expectedInputDigest != null && !Objects.equals(expectedInputDigest, inputDigest)) {
                violations.add(expectedAuthority + "_INPUT_EVIDENCE_MISMATCH");
            }
            String keyId = node.path("key_id").asText();
            if (keyId.isBlank()) violations.add(expectedAuthority + "_KEY_ID_MISSING");
            if (!"Ed25519".equals(node.path("signature_algorithm").asText())) violations.add(expectedAuthority + "_SIGNATURE_ALGORITHM_INVALID");
            Instant createdAt;
            try { createdAt = Instant.parse(node.path("created_at").asText()); }
            catch (Exception e) {
                violations.add(expectedAuthority + "_CREATED_AT_INVALID");
                createdAt = null;
            }
            if (createdAt != null && runContext != null && createdAt.isBefore(runContext.startedAt())) {
                violations.add(expectedAuthority + "_CREATED_BEFORE_RUN_START");
            }
            if (createdAt != null) {
                ValidationResult keyValidation = registry.validate(keyId, expectedAuthority, createdAt);
                if (keyValidation.decision() != Decision.PASS) violations.addAll(keyValidation.violations());
            }
            LocalKeyRegistry.KeyRecord record = registry.load().stream()
                    .filter(r -> Objects.equals(r.keyId(), keyId)).findFirst().orElse(null);
            if (record != null) {
                Map<String, Object> map = mapper.readValue(file.toFile(), new TypeReference<>() {});
                if (!LocalReceiptCrypto.verify(map, LocalReceiptCrypto.readPublicKey(Path.of(record.publicKeyFile())))) {
                    violations.add(expectedAuthority + "_SIGNATURE_INVALID");
                }
            }
            return keyId;
        } catch (Exception e) {
            violations.add(expectedAuthority + "_RECEIPT_UNREADABLE");
            return null;
        }
    }

    private void verifyFixtureReport(Path report, Path fixtureSnapshot, List<String> violations) {
        if (!Files.isRegularFile(report)) {
            violations.add("ADVERSARIAL_FIXTURE_REPORT_MISSING");
            return;
        }
        if (!Files.isRegularFile(fixtureSnapshot)) return;
        try {
            List<String> lines = Files.readAllLines(report, StandardCharsets.UTF_8);
            JsonNode fixtureRoot = mapper.readTree(fixtureSnapshot.toFile());
            Map<String, JsonNode> expected = new java.util.LinkedHashMap<>();
            for (JsonNode fixture : fixtureRoot.path("fixtures")) {
                expected.put(fixture.path("id").asText(), fixture);
            }
            if (lines.size() != expected.size() + 1
                    || !lines.get(0).equals("contract\tfixture\texpected_decision\texpected_reason\tactual_decision\tactual_reasons\tresult")) {
                violations.add("ADVERSARIAL_FIXTURE_REPORT_FORMAT_INVALID");
                return;
            }
            Set<String> seen = new HashSet<>();
            for (int i = 1; i < lines.size(); i++) {
                String[] columns = lines.get(i).split("\\t", -1);
                if (columns.length != 7) {
                    violations.add("ADVERSARIAL_FIXTURE_REPORT_FORMAT_INVALID");
                    continue;
                }
                JsonNode fixture = expected.get(columns[1]);
                if (!AdversarialFixtureReportMain.CONTRACT.equals(columns[0])
                        || fixture == null || !seen.add(columns[1])
                        || !fixture.path("expected_decision").asText().equals(columns[2])
                        || !fixture.path("expected_reason").asText().equals(columns[3])
                        || !columns[2].equals(columns[4])
                        || !Arrays.asList(columns[5].split(",", -1)).contains(columns[3])
                        || !"PASS".equals(columns[6])) {
                    violations.add("ADVERSARIAL_FIXTURE_CONTRACT_MISMATCH");
                }
            }
            if (!seen.equals(expected.keySet())) violations.add("ADVERSARIAL_FIXTURE_SET_MISMATCH");
        } catch (Exception e) {
            violations.add("ADVERSARIAL_FIXTURE_REPORT_UNREADABLE");
        }
    }

    private static void verifyEvidenceManifest(
            Path manifest, List<Path> requiredFiles, List<String> violations) {
        if (!Files.isRegularFile(manifest)) {
            violations.add("REGRESSION_EVIDENCE_MANIFEST_MISSING");
            return;
        }
        try {
            Map<Path, String> entries = new java.util.LinkedHashMap<>();
            for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
                int separator = line.indexOf("  ");
                if (separator != 64) {
                    violations.add("REGRESSION_EVIDENCE_MANIFEST_INVALID");
                    continue;
                }
                String expectedDigest = line.substring(0, 64);
                Path file = Path.of(line.substring(separator + 2)).toAbsolutePath().normalize();
                if (!expectedDigest.matches("[0-9a-f]{64}") || entries.put(file, expectedDigest) != null) {
                    violations.add("REGRESSION_EVIDENCE_MANIFEST_INVALID");
                }
            }
            Set<Path> required = new HashSet<>();
            for (Path file : requiredFiles) required.add(file.toAbsolutePath().normalize());
            if (!entries.keySet().equals(required)) violations.add("REGRESSION_EVIDENCE_SET_MISMATCH");
            for (Map.Entry<Path, String> entry : entries.entrySet()) {
                if (!Files.isRegularFile(entry.getKey())
                        || !entry.getValue().equals(sha256(entry.getKey()))) {
                    violations.add("REGRESSION_EVIDENCE_DIGEST_MISMATCH");
                }
            }
        } catch (Exception e) {
            violations.add("REGRESSION_EVIDENCE_MANIFEST_UNREADABLE");
        }
    }

    private static void requireSame(Path a, Path b, String code, List<String> violations) {
        try {
            if (!Files.isRegularFile(a) || !Files.isRegularFile(b)
                    || !java.util.Arrays.equals(Files.readAllBytes(a), Files.readAllBytes(b))) violations.add(code);
        } catch (IOException e) {
            violations.add(code);
        }
    }

    private static String digestOrNull(Path file) {
        try { return sha256(file); }
        catch (Exception e) { return null; }
    }

    private static String sha256(Path file) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(file)));
    }
}
