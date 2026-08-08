package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalPolicySnapshotVerifierTest {
    @TempDir Path temp;

    @Test
    void acceptsExactRepositorySnapshotsAndRejectsDrift() throws Exception {
        Path repository = temp.resolve("repository");
        Path run = temp.resolve("run");
        Path sourceFixture = repository.resolve("fixtures/design/adversarial-transition-fixtures.v1.json");
        Path sourceFindings = repository.resolve("findings/security-findings.v1.json");
        Path fixtureSnapshot = run.resolve("adversarial-transition-fixtures.snapshot.json");
        Path findingsSnapshot = run.resolve("security-findings.snapshot.json");
        Files.createDirectories(sourceFixture.getParent());
        Files.createDirectories(sourceFindings.getParent());
        Files.createDirectories(run);
        Files.writeString(sourceFixture, "fixture-contract");
        Files.writeString(sourceFindings, "security-register");
        Files.copy(sourceFixture, fixtureSnapshot);
        Files.copy(sourceFindings, findingsSnapshot);

        LocalPolicySnapshotVerifier verifier = new LocalPolicySnapshotVerifier();
        assertEquals(Decision.PASS, verifier.verify(run, repository).decision());

        Files.writeString(fixtureSnapshot, "drift");
        assertTrue(verifier.verify(run, repository).violations()
                .contains("ADVERSARIAL_FIXTURE_SNAPSHOT_SOURCE_MISMATCH"));
        Files.copy(sourceFixture, fixtureSnapshot, java.nio.file.StandardCopyOption.REPLACE_EXISTING);

        Files.writeString(findingsSnapshot, "drift");
        assertTrue(verifier.verify(run, repository).violations()
                .contains("SECURITY_FINDINGS_SNAPSHOT_SOURCE_MISMATCH"));
    }
}
