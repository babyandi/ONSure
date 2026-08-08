package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import kr.co.oruda.onsure.platform.TraceExporter.Span;
import kr.co.oruda.onsure.platform.TraceExporter.Trace;

/**
 * Converts {@link TraceExporter}'s reconstructed span trees into the OTLP (OpenTelemetry Protocol)
 * JSON trace shape (resourceSpans/scopeSpans/spans), so this product's local trace export can be
 * ingested by standard OTLP-compatible collectors (OBSERVABILITY-OPERATIONS:
 * TRACE_EXPORT_IS_A_LOCAL_JSON_TREE_RECONSTRUCTION_NOT_AN_OTLP_COMPATIBLE_EXPORTER).
 *
 * <p>traceId/spanId are derived deterministically from correlation_id/event_id via sha256
 * (truncated to OTLP's 16-byte/8-byte hex lengths) since this product's ids are opaque strings,
 * not already-hex trace/span ids. Spans are recorded as zero-width (start == end) because
 * {@link ObservabilityEvent} does not currently capture a span's end time or duration -- that
 * remains an explicit limitation, not something this exporter invents.
 */
public final class OtlpTraceExporter {
    private static final int TRACE_ID_HEX_LENGTH = 32;
    private static final int SPAN_ID_HEX_LENGTH = 16;
    private static final int SPAN_KIND_INTERNAL = 1;

    private OtlpTraceExporter() {}

    public static ObjectNode toResourceSpans(Iterable<Trace> traces, String serviceName) {
        if (serviceName == null || serviceName.isBlank()) {
            throw new IllegalArgumentException("OTLP_SERVICE_NAME_REQUIRED");
        }
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode root = mapper.createObjectNode();
        ArrayNode resourceSpans = root.putArray("resourceSpans");
        ObjectNode resourceSpan = resourceSpans.addObject();

        ObjectNode resource = resourceSpan.putObject("resource");
        ArrayNode attributes = resource.putArray("attributes");
        ObjectNode serviceNameAttribute = attributes.addObject();
        serviceNameAttribute.put("key", "service.name");
        serviceNameAttribute.putObject("value").put("stringValue", serviceName);

        ArrayNode scopeSpans = resourceSpan.putArray("scopeSpans");
        ObjectNode scopeSpan = scopeSpans.addObject();
        scopeSpan.putObject("scope").put("name", "onsure.structured-observability");
        ArrayNode spans = scopeSpan.putArray("spans");

        for (Trace trace : traces) {
            String traceId = truncatedHex(trace.correlationId(), TRACE_ID_HEX_LENGTH);
            for (Span rootSpan : trace.rootSpans()) {
                addSpan(spans, traceId, null, rootSpan);
            }
        }
        return root;
    }

    public static void writeTo(Path outputFile, ObjectNode otlpDocument) throws Exception {
        new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(outputFile.toFile(), otlpDocument);
    }

    private static void addSpan(ArrayNode spans, String traceId, String parentSpanId, Span span) {
        String spanId = truncatedHex(span.eventId(), SPAN_ID_HEX_LENGTH);
        ObjectNode node = spans.addObject();
        node.put("traceId", traceId);
        node.put("spanId", spanId);
        if (parentSpanId != null) node.put("parentSpanId", parentSpanId);
        node.put("name", span.name());
        String timeUnixNano = String.valueOf(epochNanos(span.occurredAt()));
        node.put("startTimeUnixNano", timeUnixNano);
        node.put("endTimeUnixNano", timeUnixNano);
        node.put("kind", SPAN_KIND_INTERNAL);
        for (Span child : span.children()) {
            addSpan(spans, traceId, spanId, child);
        }
    }

    private static long epochNanos(String occurredAt) {
        Instant instant = Instant.parse(occurredAt);
        return instant.getEpochSecond() * 1_000_000_000L + instant.getNano();
    }

    private static String truncatedHex(String value, int hexLength) {
        return Hashing.sha256(value).substring(0, hexLength);
    }
}
