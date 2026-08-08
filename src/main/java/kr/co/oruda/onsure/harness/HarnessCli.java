package kr.co.oruda.onsure.harness;

import kr.co.oruda.onsure.harness.HarnessModels.Decision;
import java.nio.file.Path;

public final class HarnessCli {
    private HarnessCli() {}

    public static void main(String[] args) throws Exception {
        if (args.length == 0) usage();
        switch (args[0]) {
            case "run" -> run(args);
            case "verify" -> verify(args);
            case "candidate" -> candidate(args);
            case "regression" -> regression(args);
            default -> usage();
        }
    }

    private static void run(String[] args) throws Exception {
        if (args.length != 6) usage();
        Path repo = Path.of(args[1]).toAbsolutePath().normalize();
        UniversalHarnessRunner.RunResult result = new UniversalHarnessRunner().run(
                repo,
                repo.resolve("harness/universal-v1/axes/verification-axes.v1.json"),
                Path.of(args[2]),
                repo.resolve("harness/universal-v1/oracles/default-oracles.v1.json"),
                Path.of(args[3]),
                args[4],
                args[5]);
        System.out.println("ONSURE_UNIVERSAL_HARNESS_" + result.summary().decision()
                + " " + result.runRoot());
        if (result.summary().decision() != Decision.PASS) System.exit(78);
    }

    private static void verify(String[] args) {
        if (args.length != 2) usage();
        RunVerifier.Verification result = new RunVerifier().verify(Path.of(args[1]));
        if (!result.valid()) {
            System.err.println("ONSURE_UNIVERSAL_VERIFY_FAIL " + result.reasons());
            System.exit(78);
        }
        System.out.println("ONSURE_UNIVERSAL_VERIFY_PASS " + result.summary().runId());
    }

    private static void candidate(String[] args) throws Exception {
        if (args.length != 4) usage();
        var result = new FinalCandidateGate().evaluateAndWrite(
                Path.of(args[1]), Path.of(args[2]), Path.of(args[3]));
        if (!result.eligible()) {
            System.err.println("ONSURE_FINAL_CANDIDATE_BLOCKED " + result.reasons());
            System.exit(78);
        }
        System.out.println("ONSURE_FINAL_CANDIDATE_PASS " + result.candidateId()
                + " final_lock_allowed=" + result.finalLockAllowed());
    }

    private static void regression(String[] args) throws Exception {
        if (args.length != 5) usage();
        var result = new RegressionGate().evaluateAndWrite(
                Path.of(args[1]), Path.of(args[2]), Path.of(args[3]), Path.of(args[4]));
        if (!result.eligible()) {
            System.err.println("ONSURE_REGRESSION_BLOCKED " + result.reasons());
            System.exit(78);
        }
        System.out.println("ONSURE_REGRESSION_PASS " + result.regressionDigest());
    }

    private static void usage() {
        System.err.println("usage:\n"
                + "  HarnessCli run <repo-root> <fixtures-json> <output-root> <operator-id> <environment-label>\n"
                + "  HarnessCli verify <run-root>\n"
                + "  HarnessCli candidate <run-1> <run-2> <output-json>\n"
                + "  HarnessCli regression <baseline> <run-1> <run-2> <output-json>");
        System.exit(64);
    }
}
