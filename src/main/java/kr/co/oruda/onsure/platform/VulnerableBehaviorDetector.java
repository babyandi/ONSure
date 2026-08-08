package kr.co.oruda.onsure.platform;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Pattern-based detector for vulnerable-behavior signals in captured fixture output
 * (P0-BEHAVIOR-VULNERABILITY-TAXONOMY: successful, failed, policy-violating and vulnerable
 * behavior conditions must be distinguished). This is deliberately narrow: it recognizes a fixed
 * set of well-known, deterministic disclosure/injection canary signatures in already-captured
 * text -- it is not a dynamic taint tracker or a general vulnerability scanner, and a signal here
 * is evidence to route to human/security review, not itself a final vulnerability determination.
 */
public final class VulnerableBehaviorDetector {

    private static final Pattern AWS_ACCESS_KEY = Pattern.compile("AKIA[0-9A-Z]{16}");
    private static final Pattern PRIVATE_KEY_HEADER = Pattern.compile("-----BEGIN [A-Z ]*PRIVATE KEY-----");
    private static final Pattern UNIX_ID_DISCLOSURE = Pattern.compile("uid=\\d+\\([a-zA-Z0-9_.-]*\\)");
    private static final Pattern PASSWD_FILE_DISCLOSURE = Pattern.compile("root:x:0:0:");

    private VulnerableBehaviorDetector() {}

    public static Set<String> scan(String observedOutput) {
        Set<String> signals = new LinkedHashSet<>();
        if (observedOutput == null || observedOutput.isEmpty()) return signals;

        if (AWS_ACCESS_KEY.matcher(observedOutput).find()) {
            signals.add("SECRET_LEAKAGE:AWS_ACCESS_KEY");
        }
        if (PRIVATE_KEY_HEADER.matcher(observedOutput).find()) {
            signals.add("SECRET_LEAKAGE:PRIVATE_KEY_MATERIAL");
        }
        if (UNIX_ID_DISCLOSURE.matcher(observedOutput).find()) {
            signals.add("COMMAND_INJECTION_SIGNAL:UID_DISCLOSURE");
        }
        if (PASSWD_FILE_DISCLOSURE.matcher(observedOutput).find()) {
            signals.add("PATH_TRAVERSAL_SIGNAL:PASSWD_DISCLOSURE");
        }
        return signals;
    }
}
