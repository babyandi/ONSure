package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

final class ProductReceiptWriter {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);
    private static final ObjectMapper CANONICAL_MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private ProductReceiptWriter() {}

    static void write(Path file, String contract, String authority, String jobId,
            Map<String, Object> claims) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contract", contract);
        body.put("authority", authority);
        body.put("job_id", jobId);
        body.put("decision", "PASS");
        body.put("created_at", Instant.now().toString());
        body.put("claims", new TreeMap<>(claims));
        body.put("receipt_sha256", digest(body));
        Files.createDirectories(file.getParent());
        Path temporary = file.resolveSibling(file.getFileName() + ".tmp");
        MAPPER.writeValue(temporary.toFile(), body);
        try {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    static void verify(Path file, String expectedContract, String expectedAuthority, String expectedJobId)
            throws Exception {
        if (!Files.isRegularFile(file)) throw new IllegalStateException("PRODUCT_RECEIPT_MISSING");
        Map<String, Object> value = MAPPER.readValue(file.toFile(), new TypeReference<>() {});
        Object stored = value.remove("receipt_sha256");
        if (!expectedContract.equals(value.get("contract"))) {
            throw new IllegalStateException("PRODUCT_RECEIPT_CONTRACT_MISMATCH");
        }
        if (!expectedAuthority.equals(value.get("authority"))) {
            throw new IllegalStateException("PRODUCT_RECEIPT_AUTHORITY_MISMATCH");
        }
        if (!expectedJobId.equals(value.get("job_id")) || !"PASS".equals(value.get("decision"))) {
            throw new IllegalStateException("PRODUCT_RECEIPT_JOB_OR_DECISION_MISMATCH");
        }
        if (!(stored instanceof String digest) || !digest.matches("[0-9a-f]{64}")
                || !digest.equals(digest(value))) {
            throw new IllegalStateException("PRODUCT_RECEIPT_HASH_MISMATCH");
        }
    }

    private static String digest(Map<String, Object> body) throws Exception {
        return Hashing.sha256(CANONICAL_MAPPER.writeValueAsBytes(new TreeMap<>(body)));
    }
}
