package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Map;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Exercises deployment.install/deployment.rollback through the real dispatch() boundary (RBAC included). */
class LocalWorkflowDispatcherDeploymentTest {
    @TempDir Path temp;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void installTwoVersionsThenRollbackThroughTheDispatcher() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        Path v1 = buildUnsignedPackage("dispatch-v1", "content-v1");
        Path v2 = buildUnsignedPackage("dispatch-v2", "content-v2");
        Path installRoot = temp.resolve(".onsure/deployment");

        Map<String, Object> installed1 = result(dispatcher.dispatch("deployment.install", request(Map.of(
                "package_dir", v1.toString(), "version", "1.0.0", "install_root", installRoot.toString()))));
        DeploymentInstallationService.InstalledVersion installedVersion1 =
                (DeploymentInstallationService.InstalledVersion) installed1.get("installed_version");
        assertEquals("1.0.0", installedVersion1.version());

        dispatcher.dispatch("deployment.install", request(Map.of(
                "package_dir", v2.toString(), "version", "2.0.0", "install_root", installRoot.toString())));
        assertEquals("2.0.0", new DeploymentInstallationService(installRoot).activeVersion());

        Map<String, Object> rolledBack = result(dispatcher.dispatch("deployment.rollback", request(Map.of(
                "target_version", "1.0.0", "install_root", installRoot.toString()))));
        DeploymentInstallationService.InstalledVersion rolledBackVersion =
                (DeploymentInstallationService.InstalledVersion) rolledBack.get("rolled_back_to");
        assertEquals("1.0.0", rolledBackVersion.version());
        assertEquals("1.0.0", new DeploymentInstallationService(installRoot).activeVersion());
    }

    @Test
    void signedPackageInstallRequiresTheVerificationKeyThroughTheDispatcher() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        KeyPair pair = LocalReceiptCrypto.generate();
        Path source = temp.resolve("signed-source");
        Files.createDirectories(source);
        Files.writeString(source.resolve("onsure.jar"), "signed-bytes");
        Path packageDir = temp.resolve("signed-package");
        DeploymentPackageBuilder.build(source, packageDir, DeploymentProfile.AIR_GAPPED, pair.getPrivate(), "key-001");
        Path installRoot = temp.resolve(".onsure/deployment-signed");

        assertThrows(IllegalStateException.class, () -> dispatcher.dispatch("deployment.install", request(Map.of(
                "package_dir", packageDir.toString(), "version", "1.0.0",
                "install_root", installRoot.toString()))));

        Path publicKeyFile = temp.resolve("verification-public.key");
        LocalReceiptCrypto.writePublicKey(publicKeyFile, pair.getPublic());
        dispatcher.dispatch("deployment.install", request(Map.of(
                "package_dir", packageDir.toString(), "version", "1.0.0",
                "install_root", installRoot.toString(), "verification_key_file", publicKeyFile.toString())));
        assertEquals("1.0.0", new DeploymentInstallationService(installRoot).activeVersion());
    }

    @Test
    void packageDirOutsideWorkspaceIsRejected() throws Exception {
        LocalWorkflowDispatcher dispatcher = new LocalWorkflowDispatcher(temp);
        assertThrows(IllegalArgumentException.class, () -> dispatcher.dispatch("deployment.install", request(Map.of(
                "package_dir", "/etc", "version", "1.0.0"))));
    }

    private Path buildUnsignedPackage(String label, String content) throws Exception {
        Path source = temp.resolve("source-" + label);
        Files.createDirectories(source);
        Files.writeString(source.resolve("onsure.jar"), content);
        Path packageDir = temp.resolve("package-" + label);
        DeploymentPackageBuilder.build(source, packageDir, DeploymentProfile.ON_PREMISES, null, null);
        return packageDir;
    }

    private JsonNode request(Map<String, Object> value) {
        return mapper.valueToTree(value);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> result(Map<String, Object> envelope) {
        return (Map<String, Object>) envelope.get("result");
    }
}
