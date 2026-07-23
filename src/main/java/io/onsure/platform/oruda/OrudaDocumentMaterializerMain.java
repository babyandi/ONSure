package io.onsure.platform.oruda;

import java.nio.file.Path;

/** CLI for one-time immutable import of the 87 ORUDA source documents. */
public final class OrudaDocumentMaterializerMain {
    private OrudaDocumentMaterializerMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length < 2 || args.length > 3) {
            System.err.println("usage: OrudaDocumentMaterializerMain <source-directory> <output-directory> [catalog-json]");
            System.exit(64);
        }
        Path catalog = args.length == 3
                ? Path.of(args[2])
                : Path.of("contracts/oruda-execution-packages.v1.json");
        OrudaDocumentMaterializer.Manifest manifest = new OrudaDocumentMaterializer().materialize(
                Path.of(args[0]), catalog, Path.of(args[1]));
        System.out.println("ORUDA_DOCUMENT_MATERIALIZATION_PASS " + manifest.materializationId()
                + " documents=" + manifest.documentCount());
    }
}
