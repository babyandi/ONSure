package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves immutable evidence references for DD semantic evaluators.
 *
 * <p>Evaluators must never interpret caller supplied context as an oracle. Semantic facts are
 * consumed only from evidence that this resolver marks integrity-verified and current.</p>
 */
public interface DdEvidenceResolver {
    record ResolvedEvidence(
            String evidenceRef,
            String contentDigest,
            JsonNode document,
            boolean integrityVerified,
            boolean current,
            String authorityRef) {
        public ResolvedEvidence {
            if (evidenceRef == null || evidenceRef.isBlank()) {
                throw new IllegalArgumentException("DD_EVIDENCE_REF_REQUIRED");
            }
            if (contentDigest == null || contentDigest.isBlank()) {
                throw new IllegalArgumentException("DD_EVIDENCE_DIGEST_REQUIRED");
            }
            if (document == null) {
                throw new IllegalArgumentException("DD_EVIDENCE_DOCUMENT_REQUIRED");
            }
            if (authorityRef == null || authorityRef.isBlank()) {
                authorityRef = "UNRESOLVED";
            }
        }
    }

    Optional<ResolvedEvidence> resolve(String evidenceRef);

    static DdEvidenceResolver rejecting() {
        return evidenceRef -> Optional.empty();
    }

    /** Test/manual-harness helper. Production callers should use an immutable receipt-backed store. */
    static DdEvidenceResolver inMemory(Map<String, ResolvedEvidence> evidence) {
        Map<String, ResolvedEvidence> copy = Map.copyOf(
                evidence == null ? Map.<String, ResolvedEvidence>of() : new LinkedHashMap<>(evidence));
        return evidenceRef -> Optional.ofNullable(copy.get(evidenceRef));
    }
}
