package io.onsure.assurance;

import java.nio.file.Path;

public final class LocalSecurityGateMain {
    private LocalSecurityGateMain() {}

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("usage: LocalSecurityGateMain <security-findings-file>");
            System.exit(64);
        }
        ValidationResult result = new LocalSecurityGateVerifier().verify(Path.of(args[0]));
        if (result.decision() != Decision.PASS) {
            System.err.println("LOCAL_SECURITY_GATE_FAIL " + result.violations());
            System.exit(78);
        }
        System.out.println("LOCAL_SECURITY_GATE_PASS " + Path.of(args[0]).toAbsolutePath().normalize());
    }
}
