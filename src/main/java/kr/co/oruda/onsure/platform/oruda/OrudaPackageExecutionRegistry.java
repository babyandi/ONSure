package kr.co.oruda.onsure.platform.oruda;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import kr.co.oruda.onsure.assurance.Decision;
import kr.co.oruda.onsure.assurance.ValidationResult;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

/** Seals and verifies all eight ORUDA execution-package output receipts. */
public final class OrudaPackageExecutionRegistry {
    public static final String CONTRACT = "ONSURE_ORUDA_PACKAGE_EXECUTION_REGISTRY_V1";
    public static final String FILE_NAME = "oruda-package-execution-registry.json";
    public static final String EVIDENCE_DIRECTORY = "oruda-package-evidence";

    public record OutputReceipt(
            String outputId,
            String relativePath,
            String receiptSha256,
            String decision,
            String evidencePath,
            String evidenceSha256,
            String semanticDigest) {}

    public record PackageResult(
            String packageId,
            String status,
            String reason,
            List<OutputReceipt> outputReceipts,
            String packageDigest) {
        public PackageResult { outputReceipts = List.copyOf(outputReceipts); }
    }

    public record Registry(
            String contract,
            String targetId,
            String jobId,
            Instant generatedAt,
            String catalogSha256,
            List<PackageResult> packages) {
        public Registry { packages = List.copyOf(packages); }
    }

    private final ObjectMapper mapper = new ObjectMapper()
            .findAndRegisterModules()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    public Registry seal(Path runRoot, Path catalogFile, String targetId, String jobId) throws Exception {
        Path normalizedRun = requireDirectory(runRoot, "ORUDA_PACKAGE_RUN_ROOT_MISSING");
        String checkedTargetId = requiredId(targetId, "targetId");
        String checkedJobId = requiredId(jobId, "jobId");
        OrudaExecutionPackageCatalog.Catalog catalog = new OrudaExecutionPackageCatalog().load(catalogFile);
        OrudaPackageOutputReceiptVerifier outputVerifier = new OrudaPackageOutputReceiptVerifier();
        List<PackageResult> results = new ArrayList<>();

        for (OrudaExecutionPackageCatalog.ExecutionPackage executionPackage : catalog.packages()) {
            List<OutputReceipt> receipts = new ArrayList<>();
            List<String> missing = new ArrayList<>();
            for (String outputId : executionPackage.requiredOutputs()) {
                Path receiptFile = expectedOutputPath(normalizedRun, executionPackage.packageId(), outputId);
                if (!Files.isRegularFile(receiptFile)) {
                    missing.add(outputId);
                    continue;
                }
                OrudaPackageOutputReceiptVerifier.Verification verification = outputVerifier.verify(
                        normalizedRun, receiptFile, checkedTargetId, checkedJobId,
                        executionPackage.packageId(), outputId);
                if (verification.result().decision() != Decision.PASS || verification.receipt() == null) {
                    throw new IllegalArgumentException(
                            "ORUDA_PACKAGE_OUTPUT_RECEIPT_INVALID:" + executionPackage.packageId()
                                    + ":" + outputId + ":" + verification.result().violations());
                }
                var verified = verification.receipt();
                receipts.add(new OutputReceipt(
                        outputId,
                        relative(normalizedRun, receiptFile),
                        verified.receiptSha256(),
                        verified.decision(),
                        verified.evidencePath(),
                        verified.evidenceSha256(),
                        verified.semanticDigest()));
            }
            missing.sort(String::compareTo);
            receipts.sort(Comparator.comparing(OutputReceipt::outputId));
            String status = deriveStatus(missing, receipts);
            String reason = deriveReason(missing, receipts, status);
            results.add(new PackageResult(
                    executionPackage.packageId(),
                    status,
                    reason,
                    receipts,
                    packageDigest(executionPackage.packageId(), status, reason, receipts)));
        }

        Registry registry = new Registry(
                CONTRACT,
                checkedTargetId,
                checkedJobId,
                Instant.now(),
                sha256(catalogFile),
                results);
        Path registryFile = normalizedRun.resolve(FILE_NAME);
        writeAtomic(registryFile, registry);
        ValidationResult verification = verify(normalizedRun, catalogFile, checkedTargetId, checkedJobId);
        if (verification.decision() != Decision.PASS) {
            Files.deleteIfExists(registryFile);
            throw new IllegalStateException("ORUDA_PACKAGE_REGISTRY_VERIFY_FAIL " + verification.violations());
        }
        bindRegistryToRunManifest(normalizedRun, registryFile);
        return registry;
    }

    public ValidationResult verify(Path runRoot, Path catalogFile, String targetId, String jobId) {
        List<String> violations = new ArrayList<>();
        try {
            Path normalizedRun = requireDirectory(runRoot, "ORUDA_PACKAGE_RUN_ROOT_MISSING");
            Path registryFile = normalizedRun.resolve(FILE_NAME);
            if (!Files.isRegularFile(registryFile)) {
                return ValidationResult.fail(List.of("ORUDA_PACKAGE_EXECUTION_REGISTRY_MISSING"));
            }
            OrudaExecutionPackageCatalog.Catalog catalog = new OrudaExecutionPackageCatalog().load(catalogFile);
            Registry registry = mapper.readValue(registryFile.toFile(), Registry.class);
            if (!CONTRACT.equals(registry.contract())) violations.add("ORUDA_PACKAGE_REGISTRY_CONTRACT_MISMATCH");
            if (!Objects.equals(targetId, registry.targetId())) violations.add("ORUDA_PACKAGE_REGISTRY_TARGET_MISMATCH");
            if (!Objects.equals(jobId, registry.jobId())) violations.add("ORUDA_PACKAGE_REGISTRY_JOB_MISMATCH");
            if (!Objects.equals(sha256(catalogFile), registry.catalogSha256())) {
                violations.add("ORUDA_PACKAGE_REGISTRY_CATALOG_HASH_MISMATCH");
            }

            Map<String, OrudaExecutionPackageCatalog.ExecutionPackage> expectedPackages = new LinkedHashMap<>();
            for (OrudaExecutionPackageCatalog.ExecutionPackage value : catalog.packages()) {
                expectedPackages.put(value.packageId(), value);
            }
            Map<String, PackageResult> actualPackages = new LinkedHashMap<>();
            for (PackageResult value : registry.packages()) {
                if (actualPackages.put(value.packageId(), value) != null) {
                    violations.add("ORUDA_PACKAGE_REGISTRY_DUPLICATE_PACKAGE:" + value.packageId());
                }
            }
            if (!actualPackages.keySet().equals(expectedPackages.keySet())) {
                violations.add("ORUDA_PACKAGE_REGISTRY_PACKAGE_SET_MISMATCH");
            }

            OrudaPackageOutputReceiptVerifier outputVerifier = new OrudaPackageOutputReceiptVerifier();
            for (Map.Entry<String, OrudaExecutionPackageCatalog.ExecutionPackage> entry : expectedPackages.entrySet()) {
                String packageId = entry.getKey();
                PackageResult result = actualPackages.get(packageId);
                if (result == null) continue;
                Map<String, OutputReceipt> receipts = new LinkedHashMap<>();
                for (OutputReceipt receipt : result.outputReceipts()) {
                    if (receipts.put(receipt.outputId(), receipt) != null) {
                        violations.add("ORUDA_PACKAGE_DUPLICATE_OUTPUT_RECEIPT:" + packageId + ":" + receipt.outputId());
                    }
                    Path expectedReceiptFile = expectedOutputPath(normalizedRun, packageId, receipt.outputId());
                    Path declaredReceiptFile = normalizedRun.resolve(receipt.relativePath()).normalize();
                    if (!declaredReceiptFile.equals(expectedReceiptFile)) {
                        violations.add("ORUDA_PACKAGE_OUTPUT_PATH_MISMATCH:" + packageId + ":" + receipt.outputId());
                        continue;
                    }
                    var verification = outputVerifier.verify(
                            normalizedRun, declaredReceiptFile, targetId, jobId, packageId, receipt.outputId());
                    if (verification.result().decision() != Decision.PASS || verification.receipt() == null) {
                        violations.addAll(verification.result().violations());
                        continue;
                    }
                    var verified = verification.receipt();
                    if (!Objects.equals(receipt.receiptSha256(), verified.receiptSha256())) {
                        violations.add("ORUDA_PACKAGE_RECEIPT_HASH_MISMATCH:" + packageId + ":" + receipt.outputId());
                    }
                    if (!Objects.equals(receipt.decision(), verified.decision())) {
                        violations.add("ORUDA_PACKAGE_OUTPUT_DECISION_MISMATCH:" + packageId + ":" + receipt.outputId());
                    }
                    if (!Objects.equals(receipt.evidencePath(), verified.evidencePath())
                            || !Objects.equals(receipt.evidenceSha256(), verified.evidenceSha256())) {
                        violations.add("ORUDA_PACKAGE_EVIDENCE_BINDING_MISMATCH:" + packageId + ":" + receipt.outputId());
                    }
                    if (!Objects.equals(receipt.semanticDigest(), verified.semanticDigest())) {
                        violations.add("ORUDA_PACKAGE_SEMANTIC_DIGEST_MISMATCH:" + packageId + ":" + receipt.outputId());
                    }
                }
                Set<String> expectedOutputs = Set.copyOf(entry.getValue().requiredOutputs());
                List<String> missing = expectedOutputs.stream()
                        .filter(outputId -> !receipts.containsKey(outputId)).sorted().toList();
                String expectedStatus = deriveStatus(missing, result.outputReceipts());
                String expectedReason = deriveReason(missing, result.outputReceipts(), expectedStatus);
                if (!Objects.equals(result.status(), expectedStatus)) {
                    violations.add("ORUDA_PACKAGE_STATUS_MISMATCH:" + packageId);
                }
                if (!Objects.equals(result.reason(), expectedReason)) {
                    violations.add("ORUDA_PACKAGE_REASON_MISMATCH:" + packageId);
                }
                if ("PASS".equals(result.status()) && !receipts.keySet().equals(expectedOutputs)) {
                    violations.add("ORUDA_PACKAGE_PASS_WITH_INCOMPLETE_OUTPUTS:" + packageId);
                }
                String digest = packageDigest(
                        result.packageId(), result.status(), result.reason(), result.outputReceipts());
                if (!Objects.equals(digest, result.packageDigest())) {
                    violations.add("ORUDA_PACKAGE_DIGEST_MISMATCH:" + packageId);
                }
            }
        } catch (Exception e) {
            violations.add("ORUDA_PACKAGE_EXECUTION_REGISTRY_UNREADABLE:" + e.getClass().getSimpleName());
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    public boolean allPackagesPass(Registry registry) {
        return registry != null && registry.packages().size() >= 6
                && registry.packages().stream().allMatch(value -> "PASS".equals(value.status()));
    }

    public Registry read(Path runRoot) throws Exception {
        return mapper.readValue(runRoot.resolve(FILE_NAME).toFile(), Registry.class);
    }

    public static Path expectedOutputPath(Path runRoot, String packageId, String outputId) {
        validateIdentifiers(packageId, outputId);
        Path normalizedRun = runRoot.toAbsolutePath().normalize();
        Path file = normalizedRun.resolve(EVIDENCE_DIRECTORY)
                .resolve(packageId).resolve("receipts").resolve(outputId + ".json").normalize();
        if (!file.startsWith(normalizedRun)) throw new IllegalArgumentException("ORUDA_PACKAGE_OUTPUT_PATH_ESCAPE");
        return file;
    }

    private static void validateIdentifiers(String packageId, String outputId) {
        if (packageId == null || !packageId.matches("ORU-PKG-[0-9]{2}")) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_ID_INVALID");
        }
        if (outputId == null || !outputId.matches("[a-z0-9_]{1,128}")) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_OUTPUT_ID_INVALID");
        }
    }

    private static String deriveStatus(List<String> missing, List<OutputReceipt> receipts) {
        if (receipts.stream().anyMatch(value -> "BLOCKED".equals(value.decision()))) return "BLOCKED";
        if (receipts.stream().anyMatch(value -> "FAIL".equals(value.decision()))) return "FAIL";
        if (!missing.isEmpty() || receipts.stream().anyMatch(value -> "NOT_RUN".equals(value.decision()))) return "NOT_RUN";
        return "PASS";
    }

    private static String deriveReason(List<String> missing, List<OutputReceipt> receipts, String status) {
        if (!missing.isEmpty()) return "MISSING_REQUIRED_OUTPUTS:" + String.join(",", missing);
        List<String> nonPass = receipts.stream()
                .filter(value -> !"PASS".equals(value.decision()))
                .map(value -> value.outputId() + "=" + value.decision())
                .sorted().toList();
        return "PASS".equals(status)
                ? "ALL_REQUIRED_OUTPUT_RECEIPTS_VERIFIED_PASS"
                : "NON_PASS_OUTPUT_RECEIPTS:" + String.join(",", nonPass);
    }

    private static void bindRegistryToRunManifest(Path runRoot, Path registryFile) throws Exception {
        Path manifest = runRoot.resolve("manifest.sha256");
        if (!Files.isRegularFile(manifest)) throw new IllegalStateException("ORUDA_PRIMARY_RUN_MANIFEST_MISSING");
        Map<String, String> entries = new TreeMap<>();
        for (String line : Files.readAllLines(manifest, StandardCharsets.UTF_8)) {
            int separator = line.indexOf("  ");
            if (separator != 64) throw new IllegalStateException("ORUDA_PRIMARY_RUN_MANIFEST_INVALID");
            entries.put(line.substring(separator + 2), line.substring(0, 64));
        }
        entries.put(FILE_NAME, sha256(registryFile));
        StringBuilder updated = new StringBuilder();
        entries.forEach((path, digest) -> updated.append(digest).append("  ").append(path).append('\n'));
        Path temporary = manifest.resolveSibling(manifest.getFileName() + ".tmp");
        Files.writeString(temporary, updated.toString(), StandardCharsets.UTF_8);
        try {
            Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, manifest, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private void writeAtomic(Path file, Registry registry) throws Exception {
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        mapper.writeValue(temporary.toFile(), registry);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String packageDigest(String packageId, String status, String reason,
            List<OutputReceipt> receipts) throws Exception {
        StringBuilder value = new StringBuilder(packageId).append('|').append(status).append('|').append(reason);
        receipts.stream().sorted(Comparator.comparing(OutputReceipt::outputId)).forEach(receipt ->
                value.append('|').append(receipt.outputId()).append(':')
                        .append(receipt.relativePath()).append(':').append(receipt.receiptSha256()).append(':')
                        .append(receipt.decision()).append(':').append(receipt.evidencePath()).append(':')
                        .append(receipt.evidenceSha256()).append(':').append(receipt.semanticDigest()));
        return sha256(value.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static Path requireDirectory(Path path, String error) {
        if (path == null) throw new IllegalArgumentException(error);
        Path normalized = path.toAbsolutePath().normalize();
        if (!Files.isDirectory(normalized)) throw new IllegalArgumentException(error);
        return normalized;
    }

    private static String requiredId(String value, String name) {
        if (value == null || !value.matches("[A-Za-z0-9._-]{1,256}")) {
            throw new IllegalArgumentException("ORUDA_PACKAGE_" + name + "_INVALID");
        }
        return value;
    }

    private static String relative(Path root, Path file) {
        return root.relativize(file.toAbsolutePath().normalize()).toString().replace('\\', '/');
    }

    private static String sha256(Path file) throws Exception {
        return sha256(Files.readAllBytes(file));
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
