package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * Extension point for one post-final-target DD semantic evaluator.
 *
 * <p>Implementing this interface is not sufficient for runtime use. The evaluator must also be
 * registered with a current qualification receipt by {@link DdSemanticEvaluatorRegistry}.</p>
 */
public interface DdSemanticEvaluator {
    String ddId();

    Evaluation evaluate(JsonNode request, EvaluationContext context) throws Exception;

    record EvaluationContext(
            String evaluatorId,
            String evaluatorVersion,
            String qualificationReceiptDigest,
            String policyRef,
            String authorityRef) {
        public EvaluationContext {
            if (evaluatorId == null || evaluatorId.isBlank()
                    || evaluatorVersion == null || evaluatorVersion.isBlank()
                    || qualificationReceiptDigest == null || qualificationReceiptDigest.isBlank()) {
                throw new IllegalArgumentException("DD_EVALUATOR_CONTEXT_INCOMPLETE");
            }
        }
    }

    record Evaluation(
            String decision,
            List<String> blockingReasons,
            List<String> evidenceReceiptRefs,
            boolean claimStrengtheningAllowed,
            boolean externalEffectPerformed,
            Map<String, Object> details) {
        public Evaluation {
            blockingReasons = blockingReasons == null ? List.of() : List.copyOf(blockingReasons);
            evidenceReceiptRefs = evidenceReceiptRefs == null ? List.of() : List.copyOf(evidenceReceiptRefs);
            details = details == null ? Map.of() : Map.copyOf(details);
            if (decision == null || decision.isBlank()) {
                throw new IllegalArgumentException("DD_EVALUATOR_DECISION_REQUIRED");
            }
        }
    }
}
