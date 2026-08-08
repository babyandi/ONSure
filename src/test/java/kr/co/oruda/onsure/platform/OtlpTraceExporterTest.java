package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.co.oruda.onsure.platform.ObservabilityEvent.Kind;
import kr.co.oruda.onsure.platform.TraceExporter.Trace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class OtlpTraceExporterTest {
    @TempDir Path temp;

    @Test
    void convertsAReconstructedTraceTreeIntoValidOtlpShapeWithParentChildLinkage() throws Exception {
        Path logFile = temp.resolve("observability.jsonl");
        StructuredObservabilityRecorder recorder = new StructuredObservabilityRecorder(logFile);
        recorder.record(new ObservabilityEvent(
                "span-root", Instant.parse("2026-08-08T00:00:00Z"), "onsure-core", Kind.TRACE_SPAN,
                "review.run", "corr-1", "", null, Map.of()));
        recorder.record(new ObservabilityEvent(
                "span-child", Instant.parse("2026-08-08T00:00:01Z"), "onsure-core", Kind.TRACE_SPAN,
                "review.static_analysis", "corr-1", "span-root", null, Map.of()));

        List<Trace> traces = TraceExporter.readTraces(logFile);
        ObjectNode otlp = OtlpTraceExporter.toResourceSpans(traces, "onsure-core");

        JsonNode resourceSpan = otlp.path("resourceSpans").get(0);
        assertEquals("service.name", resourceSpan.path("resource").path("attributes").get(0).path("key").asText());
        assertEquals("onsure-core",
                resourceSpan.path("resource").path("attributes").get(0).path("value").path("stringValue").asText());

        JsonNode spans = resourceSpan.path("scopeSpans").get(0).path("spans");
        assertEquals(2, spans.size());

        JsonNode rootSpanNode = findSpanByName(spans, "review.run");
        JsonNode childSpanNode = findSpanByName(spans, "review.static_analysis");
        assertTrue(rootSpanNode.path("traceId").asText().matches("[0-9a-f]{32}"));
        assertTrue(rootSpanNode.path("spanId").asText().matches("[0-9a-f]{16}"));
        assertTrue(rootSpanNode.path("parentSpanId").isMissingNode());
        assertEquals(rootSpanNode.path("traceId").asText(), childSpanNode.path("traceId").asText());
        assertEquals(rootSpanNode.path("spanId").asText(), childSpanNode.path("parentSpanId").asText());
    }

    @Test
    void requiresANonBlankServiceName() throws Exception {
        assertThrows(IllegalArgumentException.class,
                () -> OtlpTraceExporter.toResourceSpans(List.of(), ""));
        assertThrows(IllegalArgumentException.class,
                () -> OtlpTraceExporter.toResourceSpans(List.of(), null));
    }

    @Test
    void writesAValidJsonDocumentToDisk() throws Exception {
        ObjectNode otlp = OtlpTraceExporter.toResourceSpans(List.of(), "onsure-core");
        Path output = temp.resolve("otlp-export.json");
        OtlpTraceExporter.writeTo(output, otlp);

        JsonNode reloaded = new ObjectMapper().readTree(output.toFile());
        assertTrue(reloaded.path("resourceSpans").isArray());
    }

    private static JsonNode findSpanByName(JsonNode spans, String name) {
        for (JsonNode span : spans) {
            if (name.equals(span.path("name").asText())) return span;
        }
        throw new AssertionError("span not found: " + name);
    }
}
