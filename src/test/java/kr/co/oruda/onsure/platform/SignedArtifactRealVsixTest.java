package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import kr.co.oruda.onsure.platform.SignedArtifactService.VerifyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Exercises SignedArtifactService against a real .vsix built by the extension's own
 * `npm run package` (`npx @vscode/vsce package`) -- closing
 * SIGNED_ARTIFACT_SERVICE_NOT_YET_INVOKED_ON_A_REAL_VSCE_BUILT_VSIX for real, rather than only
 * against a synthetic byte blob (see SignedArtifactServiceTest).
 *
 * <p>Skips (not fails) when no .vsix is present: building one requires `npm install` +
 * `npm run package` in vscode-extension/, which needs npm/network access this Maven build does
 * not require or assume. This test verifies the signing mechanism when a real artifact happens to
 * be present; it never fabricates one.
 */
class SignedArtifactRealVsixTest {
    @TempDir Path temp;

    @Test
    void signsAndVerifiesARealVsceBuiltVsixWhenOneIsPresent() throws Exception {
        Path vsixDirectory = Path.of("vscode-extension");
        Path realVsix;
        try (var files = Files.exists(vsixDirectory) ? Files.list(vsixDirectory) : null) {
            realVsix = files == null ? null : files
                    .filter(path -> path.getFileName().toString().endsWith(".vsix"))
                    .findFirst().orElse(null);
        }
        assumeTrue(realVsix != null, "no .vsix present; run `npm run package` in vscode-extension/ to build one");

        Path copy = temp.resolve(realVsix.getFileName());
        Files.copy(realVsix, copy);
        KeyPair pair = LocalReceiptCrypto.generate();

        SignedArtifactService.sign(copy, "vsix-release-key-001", pair.getPrivate());
        VerifyResult verified = SignedArtifactService.verify(copy, pair.getPublic());

        assertTrue(verified.integrityValid(), "violations: " + verified.violations());
        assertTrue(verified.signatureValid());
    }
}
