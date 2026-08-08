package kr.co.oruda.onsure.assurance;

import java.nio.file.Path;

/** Read-only verification entrypoint for a completed local self-validation run. */
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
        ValidationResult evidenceLock = new LocalFinalLockVerifier().verify(root);
        if (evidenceLock.decision() != Decision.PASS) {
            System.err.println("LOCAL_EVIDENCE_LOCK_VERIFY_FAIL " + evidenceLock.violations());
            System.exit(81);
        }
        ValidationResult nonfinalReceipt = new LocalFinalReceiptVerifier().verify(root);
        if (nonfinalReceipt.decision() != Decision.PASS) {
            System.err.println("LOCAL_NONFINAL_RECEIPT_VERIFY_FAIL " + nonfinalReceipt.violations());
            System.exit(82);
        }
        System.out.println("LOCAL_ASSURANCE_NONFINAL_REVERIFY_PASS " + root);
    }
}
