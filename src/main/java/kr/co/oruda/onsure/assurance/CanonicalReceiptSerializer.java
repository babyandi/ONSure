package kr.co.oruda.onsure.assurance;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public final class CanonicalReceiptSerializer {
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true)
            .configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false)
            .findAndRegisterModules();

    public byte[] serializeForSigning(ReceiptEnvelope receipt) {
        if (receipt == null) {
            throw new IllegalArgumentException("receipt");
        }
        Map<String, Object> canonical = new LinkedHashMap<>();
        canonical.put("authority", receipt.authority());
        canonical.put("claims", receipt.claims());
        canonical.put("decision", receipt.decision());
        canonical.put("inputDigests", receipt.inputDigests());
        canonical.put("issuedAt", receipt.issuedAt());
        canonical.put("keyId", receipt.keyId());
        canonical.put("nextState", receipt.nextState());
        canonical.put("outputDigests", receipt.outputDigests());
        canonical.put("permitId", receipt.permitId());
        canonical.put("policyDigest", receipt.policyDigest());
        canonical.put("previousState", receipt.previousState());
        canonical.put("receiptId", receipt.receiptId());
        canonical.put("receiptType", receipt.receiptType());
        canonical.put("selfHash", receipt.selfHash());
        canonical.put("subjectCommitSha", receipt.subjectCommitSha());
        canonical.put("workspaceId", receipt.workspaceId());
        try {
            return mapper.writeValueAsString(canonical).getBytes(StandardCharsets.UTF_8);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("CANONICAL_SERIALIZATION_FAILED", e);
        }
    }
}
