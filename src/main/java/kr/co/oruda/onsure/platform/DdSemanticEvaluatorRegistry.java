package kr.co.oruda.onsure.platform;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Qualification-aware registry for DD semantic evaluators. */
public final class DdSemanticEvaluatorRegistry {
    public record Registration(
            DdSemanticEvaluator evaluator,
            String evaluatorId,
            String evaluatorVersion,
            String qualificationReceiptDigest,
            boolean qualificationCurrent,
            boolean independentQualification) {
        public Registration {
            if (evaluator == null) throw new IllegalArgumentException("DD_EVALUATOR_REQUIRED");
            if (evaluator.ddId() == null || !evaluator.ddId().matches("DD-(00[1-9]|0[1-3][0-9]|040)")) {
                throw new IllegalArgumentException("DD_EVALUATOR_ID_INVALID");
            }
            if (evaluatorId == null || evaluatorId.isBlank()
                    || evaluatorVersion == null || evaluatorVersion.isBlank()) {
                throw new IllegalArgumentException("DD_EVALUATOR_PROVENANCE_REQUIRED");
            }
            if (qualificationCurrent || independentQualification) {
                if (!(qualificationCurrent && independentQualification)) {
                    throw new IllegalArgumentException("DD_EVALUATOR_PARTIAL_QUALIFICATION_FORBIDDEN");
                }
                if (qualificationReceiptDigest == null || qualificationReceiptDigest.isBlank()
                        || "UNQUALIFIED".equals(qualificationReceiptDigest)) {
                    throw new IllegalArgumentException("DD_EVALUATOR_QUALIFICATION_RECEIPT_REQUIRED");
                }
            } else if (qualificationReceiptDigest == null || qualificationReceiptDigest.isBlank()) {
                qualificationReceiptDigest = "UNQUALIFIED";
            }
        }
    }

    private final Map<String, Registration> byDd;

    public DdSemanticEvaluatorRegistry(List<Registration> registrations) {
        Map<String, Registration> values = new LinkedHashMap<>();
        for (Registration registration : registrations == null ? List.<Registration>of() : registrations) {
            Registration prior = values.putIfAbsent(registration.evaluator().ddId(), registration);
            if (prior != null) {
                throw new IllegalArgumentException("DD_EVALUATOR_DUPLICATE:" + registration.evaluator().ddId());
            }
        }
        this.byDd = Map.copyOf(values);
    }

    public static DdSemanticEvaluatorRegistry empty() {
        return new DdSemanticEvaluatorRegistry(List.of());
    }

    public static DdSemanticEvaluatorRegistry builtInUnqualified() {
        return new DdSemanticEvaluatorRegistry(BuiltInDdSemanticEvaluators.all().stream()
                .map(evaluator -> new Registration(
                        evaluator,
                        "builtin-" + evaluator.ddId().toLowerCase(),
                        BuiltInDdSemanticEvaluators.VERSION,
                        "UNQUALIFIED",
                        false,
                        false))
                .toList());
    }

    public Optional<Registration> qualified(String ddId) {
        Registration value = byDd.get(ddId);
        if (value == null || !value.qualificationCurrent() || !value.independentQualification()) {
            return Optional.empty();
        }
        return Optional.of(value);
    }

    public Optional<Registration> registered(String ddId) {
        return Optional.ofNullable(byDd.get(ddId));
    }

    public int registeredCount() { return byDd.size(); }

    public long qualifiedCount() {
        return byDd.values().stream()
                .filter(Registration::qualificationCurrent)
                .filter(Registration::independentQualification)
                .count();
    }
}
