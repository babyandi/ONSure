package io.onsure.platform.oruda;

import java.nio.file.Path;

/** CLI for sealing all package output receipts into the run-level package registry. */
public final class OrudaPackageExecutionRegistryMain {
    private OrudaPackageExecutionRegistryMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 3 || args.length > 4) {
            System.err.println("usage: OrudaPackageExecutionRegistryMain <run-root> <target-id> <job-id> [catalog-json]");
            System.exit(64);
        }
        Path catalog = args.length == 4
                ? Path.of(args[3])
                : Path.of("contracts/oruda-execution-packages.v1.json");
        var registry = new OrudaPackageExecutionRegistry().seal(
                Path.of(args[0]), catalog, args[1], args[2]);
        long passed = registry.packages().stream().filter(value -> "PASS".equals(value.status())).count();
        System.out.println("ORUDA_PACKAGE_EXECUTION_REGISTRY_PASS packages=" + passed
                + "/" + registry.packages().size() + " all_pass="
                + new OrudaPackageExecutionRegistry().allPackagesPass(registry));
    }
}
