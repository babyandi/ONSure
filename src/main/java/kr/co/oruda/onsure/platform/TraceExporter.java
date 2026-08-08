package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Reconstructs distributed trace span trees from the {@link StructuredObservabilityRecorder} JSON-
 * lines log (NFR-OBS: distributed trace exporter). TRACE_SPAN events sharing a correlation_id are
 * grouped into one trace; a span's causation_id names the event_id of its parent span within that
 * same correlation_id, so the tree is rebuilt without any separate trace-context propagation.
 */
public final class TraceExporter {
    public static final String EXPORT_CONTRACT = "ONSURE_TRACE_EXPORT_V1";

    public record Span(String eventId, String name, String occurredAt, List<Span> children) {
        public Span { children = List.copyOf(children); }
    }

    public record Trace(String correlationId, List<Span> rootSpans, int spanCount) {}

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .enable(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS);

    private TraceExporter() {}

    public static List<Trace> readTraces(Path structuredLogFile) throws Exception {
        if (!Files.isRegularFile(structuredLogFile)) return List.of();
        Map<String, List<JsonNode>> spansByCorrelation = new TreeMap<>();
        for (String line : Files.readAllLines(structuredLogFile, StandardCharsets.UTF_8)) {
            if (line.isBlank()) continue;
            JsonNode node = MAPPER.readTree(line);
            if (!"TRACE_SPAN".equals(node.path("kind").asText())) continue;
            spansByCorrelation.computeIfAbsent(node.path("correlation_id").asText(), ignored -> new ArrayList<>())
                    .add(node);
        }

        List<Trace> traces = new ArrayList<>();
        for (Map.Entry<String, List<JsonNode>> entry : spansByCorrelation.entrySet()) {
            traces.add(buildTrace(entry.getKey(), entry.getValue()));
        }
        return List.copyOf(traces);
    }

    public static void writeExport(Path outputFile, List<Trace> traces) throws Exception {
        Map<String, Object> export = new LinkedHashMap<>();
        export.put("contract", EXPORT_CONTRACT);
        export.put("trace_count", traces.size());
        export.put("traces", traces);
        MAPPER.writeValue(outputFile.toFile(), export);
    }

    private static Trace buildTrace(String correlationId, List<JsonNode> spanNodes) {
        Map<String, JsonNode> byEventId = new LinkedHashMap<>();
        for (JsonNode node : spanNodes) byEventId.put(node.path("event_id").asText(), node);

        Map<String, List<JsonNode>> childrenByParent = new LinkedHashMap<>();
        List<JsonNode> roots = new ArrayList<>();
        for (JsonNode node : spanNodes) {
            String causationId = node.path("causation_id").asText();
            if (!causationId.isEmpty() && byEventId.containsKey(causationId)) {
                childrenByParent.computeIfAbsent(causationId, ignored -> new ArrayList<>()).add(node);
            } else {
                roots.add(node);
            }
        }

        List<Span> rootSpans = roots.stream().map(node -> toSpan(node, childrenByParent)).toList();
        return new Trace(correlationId, rootSpans, spanNodes.size());
    }

    private static Span toSpan(JsonNode node, Map<String, List<JsonNode>> childrenByParent) {
        String eventId = node.path("event_id").asText();
        List<JsonNode> childNodes = childrenByParent.getOrDefault(eventId, List.of());
        List<Span> children = childNodes.stream().map(child -> toSpan(child, childrenByParent)).toList();
        return new Span(eventId, node.path("name").asText(), node.path("occurred_at").asText(), children);
    }
}
