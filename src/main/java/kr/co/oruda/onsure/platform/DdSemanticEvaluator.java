package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;

/**
 * Extension point for one post-final-target DD semantic evaluator.
 *
 * <p>Implementing this interface is not sufficient for runtime use. The evaluator must also be
 * registered with a current independent qualification receipt by {@link DdSemanticEvaluatorRegistry}.</p>
 */
public interface DdSemanticEvaluator {
    String ddId();

    Evaluation evaluate(JsonNode request, EvaluationContext context) throws Exception;

    record EvaluationContext(
            String evaluatorId,
            String evaluatorVersion,
            String qualificationReceiptDigest,
            String policyRef,
            String authorityRef,
            DdEvidenceResolver evidenceResolver) {
        public EvaluationContext {
            if (evaluatorId == null || evaluatorId.isBlank()
                    || evaluatorVersion == null || evaluatorVersion.isBlank()) {
                throw new IllegalArgumentException("DD_EVALUATOR_CONTEXT_INCOMPLETE");
            }
            if (qualificationReceiptDigest == null || qualificationReceiptDigest.isBlank()) {
                qualificationReceiptDigest = "UNQUALIFIED";
            }
            if (policyRef == null || policyRef.isBlank()) policyRef = "UNRESOLVED";
            if (authorityRef == null || authorityRef.isBlank()) authorityRef = "UNRESOLVED";
            if (evidenceResolver == null) {
                throw new IllegalArgumentException("DD_EVIDENCE_RESOLVER_REQUIRED");
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
