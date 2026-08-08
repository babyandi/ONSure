package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPair;
import kr.co.oruda.onsure.assurance.LocalReceiptCrypto;
import kr.co.oruda.onsure.platform.SignedArtifactService.SignResult;
import kr.co.oruda.onsure.platform.SignedArtifactService.VerifyResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SignedArtifactServiceTest {
    @TempDir Path temp;

    @Test
    void signAndVerifyRoundTripsWithTheMatchingKey() throws Exception {
        Path artifact = temp.resolve("onsure-extension-1.0.0.vsix");
        Files.write(artifact, fakeVsixBytes());
        KeyPair pair = LocalReceiptCrypto.generate();

        SignResult signed = SignedArtifactService.sign(artifact, "vsix-key-001", pair.getPrivate());
        assertTrue(Files.isRegularFile(signed.signatureFile()));
        assertEquals(Hashing.file(artifact), signed.sha256());

        VerifyResult verified = SignedArtifactService.verify(artifact, pair.getPublic());
        assertTrue(verified.integrityValid());
        assertTrue(verified.signatureValid());
        assertEquals(0, verified.violations().size());
    }

    @Test
    void verifyFailsClosedWithTheWrongKey() throws Exception {
        Path artifact = temp.resolve("onsure-extension.vsix");
        Files.write(artifact, fakeVsixBytes());
        KeyPair correct = LocalReceiptCrypto.generate();
        KeyPair wrong = LocalReceiptCrypto.generate();
        SignedArtifactService.sign(artifact, "vsix-key-001", correct.getPrivate());

        VerifyResult verified = SignedArtifactService.verify(artifact, wrong.getPublic());
        assertFalse(verified.signatureValid());
        assertTrue(verified.violations().contains("SIGNED_ARTIFACT_SIGNATURE_INVALID"));
    }

    @Test
    void verifyFailsClosedWhenTheArtifactWasModifiedAfterSigning() throws Exception {
        Path artifact = temp.resolve("onsure-extension-tampered.vsix");
        Files.write(artifact, fakeVsixBytes());
        KeyPair pair = LocalReceiptCrypto.generate();
        SignedArtifactService.sign(artifact, "vsix-key-001", pair.getPrivate());

        Files.write(artifact, "tampered-payload".getBytes());

        VerifyResult verified = SignedArtifactService.verify(artifact, pair.getPublic());
        assertFalse(verified.integrityValid());
        assertTrue(verified.violations().contains("SIGNED_ARTIFACT_INTEGRITY_MISMATCH"));
    }

    @Test
    void verifyReportsMissingSignatureFileDistinctlyFromTampering() throws Exception {
        Path artifact = temp.resolve("unsigned-extension.vsix");
        Files.write(artifact, fakeVsixBytes());

        VerifyResult verified = SignedArtifactService.verify(artifact, null);
        assertFalse(verified.integrityValid());
        assertEquals("SIGNED_ARTIFACT_SIGNATURE_FILE_MISSING", verified.violations().get(0));
    }

    @Test
    void signingTheSameArtifactTwiceIsRejected() throws Exception {
        Path artifact = temp.resolve("double-sign.vsix");
        Files.write(artifact, fakeVsixBytes());
        KeyPair pair = LocalReceiptCrypto.generate();
        SignedArtifactService.sign(artifact, "vsix-key-001", pair.getPrivate());

        assertThrows(IllegalStateException.class,
                () -> SignedArtifactService.sign(artifact, "vsix-key-001", pair.getPrivate()));
    }

    private static byte[] fakeVsixBytes() {
        // A .vsix is just a zip; this test never inspects internal structure, only treats it as
        // an opaque binary blob, so a real vsce-built archive isn't required to prove this works.
        return "PKfake-vsix-zip-bytes-for-signing-test".getBytes();
    }
}
