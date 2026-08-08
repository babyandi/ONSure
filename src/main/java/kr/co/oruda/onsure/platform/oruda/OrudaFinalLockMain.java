package kr.co.oruda.onsure.platform.oruda;

import kr.co.oruda.onsure.assurance.Decision;
import java.nio.file.Path;

/** Explicit CLI for Final Lock creation after human approval. */
public final class OrudaFinalLockMain {
    private OrudaFinalLockMain() {}

    public static void main(String[] args) {
        if (args.length != 5) {
            System.err.println("usage: OrudaFinalLockMain <run-1> <run-2> <target-root> <approval-receipt> <final-lock-json>");
            System.exit(64);
        }
        OrudaFinalLockGate.Outcome outcome = new OrudaFinalLockGate().create(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]));
        if (outcome.result().decision() != Decision.PASS || outcome.finalLock() == null) {
            System.err.println("ORUDA_FINAL_LOCK_BLOCKED " + outcome.result().violations());
            System.exit(78);
        }
        System.out.println("ORUDA_FINAL_LOCK_PASS " + outcome.finalLock().finalLockId());
    }
}
