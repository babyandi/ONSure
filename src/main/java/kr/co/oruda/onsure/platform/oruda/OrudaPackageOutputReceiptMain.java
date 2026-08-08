package kr.co.oruda.onsure.platform.oruda;

import java.nio.file.Path;

/** CLI for sealing one executed package output from its canonical evidence artifact. */
public final class OrudaPackageOutputReceiptMain {
    private OrudaPackageOutputReceiptMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 6) {
            System.err.println("usage: OrudaPackageOutputReceiptMain <run-root> <package-id> <output-id> <target-id> <job-id> <decision>");
            System.exit(64);
        }
        Path receipt = new OrudaPackageOutputReceiptWriter().write(
                Path.of(args[0]), args[1], args[2], args[3], args[4], args[5]);
        System.out.println("ORUDA_PACKAGE_OUTPUT_RECEIPT_PASS " + receipt);
    }
}
