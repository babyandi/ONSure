package kr.co.oruda.onsure.platform.oruda;

import java.nio.file.Path;

/** CLI for evaluating, not issuing, ORUDA Final Candidate status. */
public final class OrudaFinalCandidateMain {
    private OrudaFinalCandidateMain() {}

    public static void main(String[] args) throws Exception {
        if (args.length != 4) {
            System.err.println("usage: OrudaFinalCandidateMain <run-1> <run-2> <target-root> <output-json>");
            System.exit(64);
        }
        FinalCandidateGate.GateResult result = new FinalCandidateGate().evaluateAndWrite(
                Path.of(args[0]), Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        if (!result.eligible()) {
            System.err.println("ORUDA_FINAL_CANDIDATE_BLOCKED " + result.reasons());
            System.exit(78);
        }
        System.out.println("ORUDA_FINAL_CANDIDATE_PASS " + result.candidateId()
                + " final_lock_allowed=" + result.finalLockAllowed());
    }
}
