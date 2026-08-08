package kr.co.oruda.onsure.assurance;

import java.util.Set;

public record TransitionContext(
        String from,
        String to,
        Set<String> inputReceipts,
        Set<String> outputReceipts,
        boolean criticalOrHighOpen,
        String runtimeKeyId,
        String otesterKeyId,
        String oauditKeyId,
        String regressionRunId1,
        String regressionRunId2,
        Decision claimedDecision) {

    public TransitionContext {
        inputReceipts = Set.copyOf(inputReceipts);
        outputReceipts = Set.copyOf(outputReceipts);
    }
}
