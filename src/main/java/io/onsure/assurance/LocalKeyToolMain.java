package io.onsure.assurance;

import java.nio.file.Path;

public final class LocalKeyToolMain {
    private LocalKeyToolMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            System.err.println("usage: LocalKeyToolMain <private-key-file> <public-key-file>");
            System.exit(64);
        }
        var pair = LocalReceiptCrypto.generate();
        LocalReceiptCrypto.writePrivateKey(Path.of(args[0]), pair.getPrivate());
        LocalReceiptCrypto.writePublicKey(Path.of(args[1]), pair.getPublic());
    }
}