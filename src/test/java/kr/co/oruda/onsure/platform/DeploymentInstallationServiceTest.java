package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import kr.co.oruda.onsure.platform.DeploymentInstallationService.InstalledVersion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DeploymentInstallationServiceTest {
    @TempDir Path temp;

    @Test
    void installThenUpdateThenRollbackTracksTheActiveVersion() throws Exception {
        Path v1 = buildUnsignedPackage("1.0.0", "hello v1");
        Path v2 = buildUnsignedPackage("2.0.0", "hello v2");
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root"));

        service.install(v1, "1.0.0", null);
        assertEquals("1.0.0", service.activeVersion());

        service.install(v2, "2.0.0", null);
        assertEquals("2.0.0", service.activeVersion());
        assertEquals(2, service.history().size());

        InstalledVersion rolledBack = service.rollback("1.0.0", null);
        assertEquals("1.0.0", rolledBack.version());
        assertEquals("1.0.0", service.activeVersion());
        assertEquals(3, service.history().size());
        assertEquals("ROLLBACK", service.history().get(2).get("event"));
    }

    @Test
    void rollbackToAnUninstalledVersionFailsClosed() throws Exception {
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-missing"));
        assertThrows(IllegalStateException.class, () -> service.rollback("9.9.9", null));
    }

    @Test
    void installingTheSameVersionTwiceIsRejected() throws Exception {
        Path v1 = buildUnsignedPackage("1.0.0", "hello");
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-dup"));
        service.install(v1, "1.0.0", null);
        assertThrows(IllegalStateException.class, () -> service.install(v1, "1.0.0", null));
    }

    @Test
    void signedPackageRequiresTheMatchingKeyToInstallAndRollback() throws Exception {
        KeyPair correct = LocalReceiptCrypto.generate();
        KeyPair wrong = LocalReceiptCrypto.generate();
        Path source = temp.resolve("signed-source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("onsure.jar"), "signed-bytes");
        Path packageDir = temp.resolve("signed-package");
        DeploymentPackageBuilder.build(source, packageDir, DeploymentProfile.AIR_GAPPED, correct.getPrivate(), "key-001");

        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-signed"));
        assertThrows(IllegalStateException.class, () -> service.install(packageDir, "1.0.0", wrong.getPublic()));
        service.install(packageDir, "1.0.0", correct.getPublic());
        assertEquals("1.0.0", service.activeVersion());

        assertThrows(IllegalStateException.class, () -> service.rollback("1.0.0", wrong.getPublic()));
        service.rollback("1.0.0", correct.getPublic());
    }

    @Test
    void rollbackFailsClosedWhenTheArchivedVersionWasTamperedWith() throws Exception {
        Path v1 = buildUnsignedPackage("1.0.0", "hello");
        DeploymentInstallationService service = new DeploymentInstallationService(temp.resolve("install-root-tamper"));
        service.install(v1, "1.0.0", null);

        Path archivedFile = temp.resolve("install-root-tamper/versions/1.0.0/onsure.jar");
        assertTrue(Files.isRegularFile(archivedFile));
        Files.writeString(archivedFile, "tampered");

        assertThrows(IllegalStateException.class, () -> service.rollback("1.0.0", null));
    }

    private Path buildUnsignedPackage(String version, String content) throws Exception {
        Path source = temp.resolve("source-" + version + "-" + System.nanoTime());
        Files.createDirectories(source);
        Files.writeString(source.resolve("onsure.jar"), content);
        Path packageDir = temp.resolve("package-" + version + "-" + System.nanoTime());
        DeploymentPackageBuilder.build(source, packageDir, DeploymentProfile.ON_PREMISES, null, null);
        return packageDir;
    }
}
