package io.onsure.assurance;

public final class LocalRolePolicy {
    public static final String OTESTER_POLICY = "ONSURE_OTESTER_POLICY_V1";
    public static final String OAUDIT_POLICY = "ONSURE_OAUDIT_POLICY_V1";
    public static final String OTESTER_SCOPE = "REGRESSION_RESULTS_AND_COMPILED_ARTIFACTS";
    public static final String OAUDIT_SCOPE = "SIGNED_OTESTER_RECEIPT_AND_PUBLICATION_EVIDENCE";

    private LocalRolePolicy() {}

    public static String expectedPolicy(String authority) {
        return switch (authority) {
            case "OTESTER" -> OTESTER_POLICY;
            case "OAUDIT" -> OAUDIT_POLICY;
            default -> throw new IllegalArgumentException("unsupported authority: " + authority);
        };
    }

    public static String expectedScope(String authority) {
        return switch (authority) {
            case "OTESTER" -> OTESTER_SCOPE;
            case "OAUDIT" -> OAUDIT_SCOPE;
            default -> throw new IllegalArgumentException("unsupported authority: " + authority);
        };
    }
}
