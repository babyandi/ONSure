package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import kr.co.oruda.onsure.platform.DeploymentPackageBuilder.BuildResult;
import kr.co.oruda.onsure.platform.DeploymentPackageBuilder.VerifyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeploymentPackageBuilderTest {
    @TempDir Path temp;

    @Test
    void airGappedProfileRequiresASigningKey() throws Exception {
        Path source = writeSourceFiles();
        assertThrows(IllegalArgumentException.class, () -> DeploymentPackageBuilder.build(
                source, temp.resolve("out-unsigned"), DeploymentProfile.AIR_GAPPED, null, null));
    }

    @Test
    void onPremisesProfileDoesNotRequireASigningKey() throws Exception {
        Path source = writeSourceFiles();
        BuildResult result = DeploymentPackageBuilder.build(
                source, temp.resolve("out-on-prem"), DeploymentProfile.ON_PREMISES, null, null);
        assertFalse(result.signed());
        assertTrue(result.fileCount() >= 2);
    }

    @Test
    void signedPackageVerifiesWithTheMatchingPublicKeyAndFailsWithAWrongOne() throws Exception {
        Path source = writeSourceFiles();
        KeyPair correct = LocalReceiptCrypto.generate();
        KeyPair wrong = LocalReceiptCrypto.generate();
        Path outputDir = temp.resolve("out-signed");

        BuildResult built = DeploymentPackageBuilder.build(
                source, outputDir, DeploymentProfile.AIR_GAPPED, correct.getPrivate(), "airgap-key-001");
        assertTrue(built.signed());

        VerifyResult withCorrectKey = DeploymentPackageBuilder.verify(outputDir, correct.getPublic());
        assertTrue(withCorrectKey.integrityValid());
        assertTrue(withCorrectKey.signed());
        assertTrue(withCorrectKey.signatureValid());
        assertEquals(0, withCorrectKey.violations().size());

        VerifyResult withWrongKey = DeploymentPackageBuilder.verify(outputDir, wrong.getPublic());
        assertFalse(withWrongKey.signatureValid());
        assertTrue(withWrongKey.violations().contains("DEPLOYMENT_PACKAGE_SIGNATURE_INVALID"));
    }

    @Test
    void verifyFailsClosedOnATamperedFile() throws Exception {
        Path source = writeSourceFiles();
        Path outputDir = temp.resolve("out-tamper");
        DeploymentPackageBuilder.build(source, outputDir, DeploymentProfile.ON_PREMISES, null, null);

        try (var files = Files.walk(outputDir)) {
            Path target = files.filter(Files::isRegularFile)
                    .filter(path -> !path.getFileName().toString().equals("deployment-package-manifest.json"))
                    .findFirst().orElseThrow();
            Files.writeString(target, "tampered content");
        }

        VerifyResult result = DeploymentPackageBuilder.verify(outputDir, null);
        assertFalse(result.integrityValid());
        assertTrue(result.violations().stream().anyMatch(v -> v.startsWith("DEPLOYMENT_PACKAGE_FILE_INTEGRITY_MISMATCH")));
    }

    @Test
    void buildRefusesANonEmptyOutputDirectory() throws Exception {
        Path source = writeSourceFiles();
        Path outputDir = temp.resolve("out-occupied");
        Files.createDirectories(outputDir);
        Files.writeString(outputDir.resolve("pre-existing.txt"), "already here");

        assertThrows(IllegalStateException.class, () -> DeploymentPackageBuilder.build(
                source, outputDir, DeploymentProfile.ON_PREMISES, null, null));
    }

    private Path writeSourceFiles() throws Exception {
        Path source = temp.resolve("source-" + System.nanoTime());
        Files.createDirectories(source.resolve("lib"));
        Files.writeString(source.resolve("onsure.jar"), "fake-jar-bytes");
        Files.writeString(source.resolve("lib/config.yaml"), "profile: on-prem");
        return source;
    }
}
