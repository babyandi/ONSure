package kr.co.oruda.onsure.assurance;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

public final class LocalFinalLockVerifier {
    public ValidationResult verify(Path runRoot) {
        return verify(runRoot.resolve("final-lock.sha256"), runRoot);
    }

    public ValidationResult verify(Path lock, Path runRoot) {
        Path normalizedRoot = runRoot.toAbsolutePath().normalize();
        Path normalizedLock = lock.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalizedLock)) return ValidationResult.fail(List.of("FINAL_LOCK_MISSING"));
        List<String> violations = new ArrayList<>();
        Set<Path> covered = new HashSet<>();
        try {
            for (String line : Files.readAllLines(normalizedLock, StandardCharsets.UTF_8)) {
                if (line.isBlank()) continue;
                int separator = line.indexOf("  ");
                if (separator != 64) {
                    violations.add("FINAL_LOCK_ENTRY_INVALID");
                    continue;
                }
                String expected = line.substring(0, 64);
                String rawPath = line.substring(separator + 2);
                if (!expected.matches("[0-9a-f]{64}")) {
                    violations.add("FINAL_LOCK_DIGEST_INVALID");
                    continue;
                }
                Path file = Path.of(rawPath).toAbsolutePath().normalize();
                if (!file.startsWith(normalizedRoot)) {
                    violations.add("FINAL_LOCK_PATH_OUTSIDE_RUN_ROOT");
                    continue;
                }
                if (!Files.isRegularFile(file)) {
                    violations.add("FINAL_LOCK_FILE_MISSING");
                    continue;
                }
                if (!covered.add(file)) violations.add("FINAL_LOCK_DUPLICATE_ENTRY");
                String actual = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                        .digest(Files.readAllBytes(file)));
                if (!expected.equals(actual)) violations.add("FINAL_LOCK_DIGEST_MISMATCH");
            }
            for (Path required : requiredFiles(normalizedRoot)) {
                if (!covered.contains(required.toAbsolutePath().normalize())) {
                    violations.add("FINAL_LOCK_REQUIRED_ENTRY_MISSING");
                }
            }
        } catch (Exception e) {
            violations.add("FINAL_LOCK_UNREADABLE");
        }
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    static List<Path> requiredFiles(Path root) {
        return List.of(
                root.resolve("run-context.json"),
                root.resolve("source-lock.json"),
                root.resolve("adversarial-transition-fixtures.snapshot.json"),
                root.resolve("security-findings.snapshot.json"),
                root.resolve("regression-1/test-summary.txt"),
                root.resolve("regression-1/classes.sha256"),
                root.resolve("regression-1/adversarial-fixtures.tsv"),
                root.resolve("regression-1/evidence.sha256"),
                root.resolve("regression-2/test-summary.txt"),
                root.resolve("regression-2/classes.sha256"),
                root.resolve("regression-2/adversarial-fixtures.tsv"),
                root.resolve("regression-2/evidence.sha256"),
                root.resolve("otester/receipt.json"),
                root.resolve("oaudit/receipt.json"),
                root.resolve("keys/otester-public.key"),
                root.resolve("keys/oaudit-public.key"),
                root.resolve("key-registry.snapshot.json"));
    }
}
