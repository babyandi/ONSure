package io.onsure.assurance;

import java.nio.file.Path;

/** Read-only verification entrypoint for a completed local assurance run. */
public final class LocalVerifyMain {
    private LocalVerifyMain() {}

    public static void main(String[] args) {
        if (args.length != 2) {
            System.err.println("usage: LocalVerifyMain <run-root> <repository-root>");
            System.exit(64);
        }
        Path root = Path.of(args[0]).toAbsolutePath().normalize();
        Path repositoryRoot = Path.of(args[1]).toAbsolutePath().normalize();

        ValidationResult sourceLock = new LocalSourceLockVerifier().verifyAgainstRepository(
                root.resolve("source-lock.json"), repositoryRoot);
        if (sourceLock.decision() != Decision.PASS) {
            System.err.println("LOCAL_SOURCE_LOCK_VERIFY_FAIL " + sourceLock.violations());
            System.exit(79);
        }
        ValidationResult snapshots = new LocalPolicySnapshotVerifier().verify(root, repositoryRoot);
        if (snapshots.decision() != Decision.PASS) {
            System.err.println("LOCAL_POLICY_SNAPSHOT_VERIFY_FAIL " + snapshots.violations());
            System.exit(77);
        }
        ValidationResult evidence = new LocalEvidenceVerifier().verify(root);
        if (evidence.decision() != Decision.PASS) {
            System.err.println("LOCAL_EVIDENCE_VERIFY_FAIL " + evidence.violations());
            System.exit(80);
        }
        ValidationResult finalLock = new LocalFinalLockVerifier().verify(root);
        if (finalLock.decision() != Decision.PASS) {
            System.err.println("LOCAL_FINAL_LOCK_VERIFY_FAIL " + finalLock.violations());
            System.exit(81);
        }
        ValidationResult finalReceipt = new LocalFinalReceiptVerifier().verify(root);
        if (finalReceipt.decision() != Decision.PASS) {
            System.err.println("LOCAL_FINAL_RECEIPT_VERIFY_FAIL " + finalReceipt.violations());
            System.exit(82);
        }
        System.out.println("LOCAL_ASSURANCE_REVERIFY_PASS " + root);
    }
}
