package io.onsure.assurance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class LocalPolicySnapshotVerifier {
    public ValidationResult verify(Path runRoot, Path repositoryRoot) {
        if (runRoot == null || repositoryRoot == null) {
            return ValidationResult.fail(List.of("POLICY_SNAPSHOT_ROOT_MISSING"));
        }
        Path run = runRoot.toAbsolutePath().normalize();
        Path repository = repositoryRoot.toAbsolutePath().normalize();
        List<String> violations = new ArrayList<>();
        requireSame(
                run.resolve("adversarial-transition-fixtures.snapshot.json"),
                repository.resolve("fixtures/design/adversarial-transition-fixtures.v1.json"),
                "ADVERSARIAL_FIXTURE_SNAPSHOT_SOURCE_MISMATCH",
                violations);
        requireSame(
                run.resolve("security-findings.snapshot.json"),
                repository.resolve("findings/security-findings.v1.json"),
                "SECURITY_FINDINGS_SNAPSHOT_SOURCE_MISMATCH",
                violations);
        return violations.isEmpty() ? ValidationResult.pass() : ValidationResult.fail(violations);
    }

    private static void requireSame(
            Path snapshot, Path source, String code, List<String> violations) {
        try {
            if (!Files.isRegularFile(snapshot) || !Files.isRegularFile(source)
                    || !Arrays.equals(Files.readAllBytes(snapshot), Files.readAllBytes(source))) {
                violations.add(code);
            }
        } catch (Exception e) {
            violations.add(code);
        }
    }
}
