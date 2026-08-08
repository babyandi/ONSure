package kr.co.oruda.onsure.assurance;

import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

public final class LocalKeyRegistryMain {
    private LocalKeyRegistryMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 5) {
            System.err.println("usage: LocalKeyRegistryMain <registry> <key-id> <authority> <public-key-file> <valid-days>");
            System.exit(64);
        }
        int validDays = Integer.parseInt(args[4]);
        if (validDays < 1) {
            System.err.println("valid-days must be positive");
            System.exit(65);
        }
        Instant now = Instant.now();
        LocalKeyRegistry registry = new LocalKeyRegistry(Path.of(args[0]));
        ValidationResult result = registry.register(new LocalKeyRegistry.KeyRecord(
                args[1], args[2], Path.of(args[3]).toAbsolutePath().normalize().toString(),
                now.minus(1, ChronoUnit.MINUTES), now.plus(validDays, ChronoUnit.DAYS), false, null));
        if (result.decision() != Decision.PASS) {
            System.err.println("KEY_REGISTRY_FAIL " + result.violations());
            System.exit(66);
        }
    }
}
