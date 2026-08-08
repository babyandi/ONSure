package kr.co.oruda.onsure.assurance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalSecurityGateVerifierTest {
    @TempDir Path temp;

    @Test
    void acceptsCompleteRegisterWithClosedBlockingFindings() {
        assertEquals(Decision.PASS, new LocalSecurityGateVerifier().verify(
                Path.of("findings/security-findings.v1.json")).decision());
    }

    @Test
    void rejectsOpenHighFinding() throws Exception {
        Path file = temp.resolve("findings.json");
        Files.writeString(file, """
                {
                  "contract":"ONSURE_SECURITY_FINDINGS_V1",
                  "review_status":"COMPLETE",
                  "review_method":"TEST",
                  "findings":[{
                    "id":"ONSURE-SEC-999",
                    "severity":"HIGH",
                    "status":"OPEN",
                    "summary":"open",
                    "resolution":"pending"
                  }]
                }
                """);
        assertTrue(new LocalSecurityGateVerifier().verify(file).violations()
                .contains("OPEN_BLOCKING_SECURITY_FINDING"));
    }

    @Test
    void rejectsIncompleteOrMissingRegister() throws Exception {
        Path file = temp.resolve("findings.json");
        Files.writeString(file, "{\"contract\":\"ONSURE_SECURITY_FINDINGS_V1\",\"review_status\":\"INCOMPLETE\",\"review_method\":\"TEST\",\"findings\":[]}");
        assertTrue(new LocalSecurityGateVerifier().verify(file).violations().contains("SECURITY_REVIEW_INCOMPLETE"));
        assertTrue(new LocalSecurityGateVerifier().verify(temp.resolve("missing.json")).violations()
                .contains("SECURITY_FINDINGS_SNAPSHOT_MISSING"));
    }
}
