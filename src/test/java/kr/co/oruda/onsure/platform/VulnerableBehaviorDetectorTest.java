package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class VulnerableBehaviorDetectorTest {

    @Test
    void cleanOutputHasNoSignals() {
        assertEquals(Set.of(), VulnerableBehaviorDetector.scan("hello world, all tests passed"));
    }

    @Test
    void blankOrNullOutputHasNoSignals() {
        assertEquals(Set.of(), VulnerableBehaviorDetector.scan(""));
        assertEquals(Set.of(), VulnerableBehaviorDetector.scan(null));
    }

    @Test
    void awsAccessKeyIsDetected() {
        Set<String> signals = VulnerableBehaviorDetector.scan("leaked credential: AKIAABCDEFGHIJKLMNOP in log");
        assertTrue(signals.contains("SECRET_LEAKAGE:AWS_ACCESS_KEY"));
    }

    @Test
    void privateKeyMaterialIsDetected() {
        Set<String> signals = VulnerableBehaviorDetector.scan(
                "-----BEGIN RSA PRIVATE KEY-----\nMIIBOgIBAAJBAK...\n-----END RSA PRIVATE KEY-----");
        assertTrue(signals.contains("SECRET_LEAKAGE:PRIVATE_KEY_MATERIAL"));
    }

    @Test
    void commandInjectionUidDisclosureIsDetected() {
        Set<String> signals = VulnerableBehaviorDetector.scan("uid=0(root) gid=0(root) groups=0(root)");
        assertTrue(signals.contains("COMMAND_INJECTION_SIGNAL:UID_DISCLOSURE"));
    }

    @Test
    void passwdFileDisclosureIsDetected() {
        Set<String> signals = VulnerableBehaviorDetector.scan("root:x:0:0:root:/root:/bin/bash\ndaemon:x:1:1::/usr/sbin:");
        assertTrue(signals.contains("PATH_TRAVERSAL_SIGNAL:PASSWD_DISCLOSURE"));
    }

    @Test
    void multipleSignalsCanBeReportedTogether() {
        Set<String> signals = VulnerableBehaviorDetector.scan(
                "AKIAABCDEFGHIJKLMNOP leaked and root:x:0:0:root:/root:/bin/bash also disclosed");
        assertEquals(2, signals.size());
    }
}
