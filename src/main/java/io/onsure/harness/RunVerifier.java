package io.onsure.harness;

import io.onsure.harness.HarnessModels.Evidence;
import io.onsure.harness.HarnessModels.Receipt;
import io.onsure.harness.HarnessModels.RunReceipt;
import io.onsure.harness.HarnessModels.RunSummary;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class RunVerifier {
    public record Verification(boolean valid, List<String> reasons, RunSummary summary) {
        public Verification { reasons = List.copyOf(reasons); }
    }

    public Verification verify(Path runRoot) {
        List<String> reasons = new ArrayList<>();
        RunSummary summary = null;
        try {
            Path root = runRoot.toAbsolutePath().normalize();
            for (String required : List.of("run-summary.json", "run-receipt.json", "evidence-manifest.sha256")) {
                if (!Files.isRegularFile(root.resolve(required))) reasons.add("REQUIRED_FILE_MISSING:" + required);
            }
            if (!reasons.isEmpty()) return new Verification(false, reasons, null);
            summary = JsonSupport.read(root.resolve("run-summary.json"), RunSummary.class);
            RunReceipt runReceipt = JsonSupport.read(root.resolve("run-receipt.json"), RunReceipt.class);
            verifyManifest(root, reasons);
            if (!summary.runId().equals(runReceipt.runId())) reasons.add("RUN_RECEIPT_ID_MISMATCH");
            if (!Hashing.sha256(root.resolve("run-summary.json")).equals(runReceipt.runSummarySha256())) {
                reasons.add("RUN_SUMMARY_HASH_MISMATCH");
            }
            if (!Hashing.sha256(root.resolve("evidence-manifest.sha256")).equals(runReceipt.evidenceManifestSha256())) {
                reasons.add("RUN_MANIFEST_HASH_MISMATCH");
            }
            if (summary.decision() != runReceipt.decision()) reasons.add("RUN_DECISION_MISMATCH");
            if (!runReceipt.receiptSha256().equals(runReceiptDigest(runReceipt))) {
                reasons.add("RUN_RECEIPT_DIGEST_MISMATCH");
            }
            verifyFixtureLineage(root, summary, reasons);
        } catch (Exception e) {
            reasons.add("RUN_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return new Verification(reasons.isEmpty(), reasons, summary);
    }

    private static void verifyManifest(Path root, List<String> reasons) throws Exception {
        Map<String, String> declared = new LinkedHashMap<>();
        for (String line : Files.readAllLines(root.resolve("evidence-manifest.sha256"), StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            int separator = line.indexOf("  ");
            if (separator != 64) {
                reasons.add("MANIFEST_FORMAT_INVALID");
                continue;
            }
            String digest = line.substring(0, separator);
            String relative = line.substring(separator + 2);
            if (!digest.matches("[0-9a-f]{64}") || relative.isBlank() || declared.put(relative, digest) != null) {
                reasons.add("MANIFEST_ENTRY_INVALID:" + relative);
                continue;
            }
            Path file = root.resolve(relative).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                reasons.add("MANIFEST_PATH_INVALID:" + relative);
            } else if (!digest.equals(Hashing.sha256(file))) {
                reasons.add("MANIFEST_HASH_MISMATCH:" + relative);
            }
        }
        Set<String> actual = new HashSet<>();
        try (var stream = Files.walk(root)) {
            stream.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("evidence-manifest.sha256"))
                    .filter(path -> !path.getFileName().toString().equals("run-receipt.json"))
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .forEach(actual::add);
        }
        if (!declared.keySet().equals(actual)) reasons.add("MANIFEST_FILE_SET_MISMATCH");
    }

    private static void verifyFixtureLineage(Path root, RunSummary summary, List<String> reasons) throws Exception {
        Set<String> fixtureIds = new HashSet<>();
        for (var result : summary.fixtureResults()) {
            if (!fixtureIds.add(result.fixtureId())) {
                reasons.add("DUPLICATE_FIXTURE_RESULT:" + result.fixtureId());
                continue;
            }
            Path evidenceFile = root.resolve(result.evidencePath()).normalize();
            Path receiptFile = root.resolve(result.receiptPath()).normalize();
            if (!evidenceFile.startsWith(root) || !receiptFile.startsWith(root)) {
                reasons.add("FIXTURE_PATH_ESCAPE:" + result.fixtureId());
                continue;
            }
            if (!Files.isRegularFile(evidenceFile) || !Files.isRegularFile(receiptFile)) {
                reasons.add("FIXTURE_LINEAGE_FILE_MISSING:" + result.fixtureId());
                continue;
            }
            if (!Hashing.sha256(evidenceFile).equals(result.evidenceSha256())) {
                reasons.add("FIXTURE_EVIDENCE_HASH_MISMATCH:" + result.fixtureId());
            }
            if (!Hashing.sha256(receiptFile).equals(result.receiptSha256())) {
                reasons.add("FIXTURE_RECEIPT_FILE_HASH_MISMATCH:" + result.fixtureId());
            }
            Evidence evidence = JsonSupport.read(evidenceFile, Evidence.class);
            Receipt receipt = JsonSupport.read(receiptFile, Receipt.class);
            if (!summary.runId().equals(evidence.runId()) || !summary.runId().equals(receipt.runId())) {
                reasons.add("FIXTURE_RUN_LINEAGE_MISMATCH:" + result.fixtureId());
            }
            if (!result.fixtureId().equals(evidence.fixtureId()) || !result.fixtureId().equals(receipt.fixtureId())) {
                reasons.add("FIXTURE_ID_LINEAGE_MISMATCH:" + result.fixtureId());
            }
            if (!result.evidenceSha256().equals(receipt.evidenceSha256())) {
                reasons.add("RECEIPT_EVIDENCE_HASH_MISMATCH:" + result.fixtureId());
            }
            if (result.decision() != evidence.decision() || result.decision() != receipt.decision()) {
                reasons.add("FIXTURE_DECISION_LINEAGE_MISMATCH:" + result.fixtureId());
            }
            if (!receipt.receiptSha256().equals(receiptDigest(receipt))) {
                reasons.add("FIXTURE_RECEIPT_DIGEST_MISMATCH:" + result.fixtureId());
            }
        }
    }

    private static String receiptDigest(Receipt receipt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", receipt.contract());
        body.put("receipt_id", receipt.receiptId());
        body.put("run_id", receipt.runId());
        body.put("fixture_id", receipt.fixtureId());
        body.put("oracle_id", receipt.oracleId());
        body.put("evidence_sha256", receipt.evidenceSha256());
        body.put("decision", receipt.decision().name());
        body.put("reason", receipt.reason());
        body.put("severity", receipt.severity().name());
        body.put("rca_required", receipt.rcaRequired());
        body.put("created_at", receipt.createdAt().toString());
        return Hashing.sha256(JsonSupport.canonicalBytes(body));
    }

    private static String runReceiptDigest(RunReceipt receipt) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", receipt.contract());
        body.put("run_id", receipt.runId());
        body.put("run_summary_sha256", receipt.runSummarySha256());
        body.put("evidence_manifest_sha256", receipt.evidenceManifestSha256());
        body.put("decision", receipt.decision().name());
        body.put("created_at", receipt.createdAt().toString());
        return Hashing.sha256(JsonSupport.canonicalBytes(body));
    }
}
