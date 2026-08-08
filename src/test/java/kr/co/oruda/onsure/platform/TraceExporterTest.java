package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import kr.co.oruda.onsure.platform.ObservabilityEvent.Kind;
import kr.co.oruda.onsure.platform.TraceExporter.Span;
import kr.co.oruda.onsure.platform.TraceExporter.Trace;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TraceExporterTest {
    @TempDir Path temp;

    @Test
    void reconstructsAParentChildSpanTreeWithinOneCorrelationId() throws Exception {
        Path logFile = temp.resolve("observability.jsonl");
        StructuredObservabilityRecorder recorder = new StructuredObservabilityRecorder(logFile);

        recorder.record(new ObservabilityEvent(
                "span-root", Instant.parse("2026-08-08T00:00:00Z"), "onsure-core", Kind.TRACE_SPAN,
                "review.run", "corr-1", "", null, Map.of()));
        recorder.record(new ObservabilityEvent(
                "span-child", Instant.parse("2026-08-08T00:00:01Z"), "onsure-core", Kind.TRACE_SPAN,
                "review.static_analysis", "corr-1", "span-root", null, Map.of()));
        recorder.record(new ObservabilityEvent(
                "span-grandchild", Instant.parse("2026-08-08T00:00:02Z"), "onsure-core", Kind.TRACE_SPAN,
                "review.rule_pack_eval", "corr-1", "span-child", null, Map.of()));
        // A non-trace log line must be ignored by the exporter.
        recorder.record(new ObservabilityEvent(
                "log-1", Instant.parse("2026-08-08T00:00:03Z"), "onsure-core", Kind.LOG,
                "info", "corr-1", "", null, Map.of()));

        List<Trace> traces = TraceExporter.readTraces(logFile);
        assertEquals(1, traces.size());
        Trace trace = traces.get(0);
        assertEquals("corr-1", trace.correlationId());
        assertEquals(3, trace.spanCount());
        assertEquals(1, trace.rootSpans().size());

        Span root = trace.rootSpans().get(0);
        assertEquals("span-root", root.eventId());
        assertEquals(1, root.children().size());
        Span child = root.children().get(0);
        assertEquals("span-child", child.eventId());
        assertEquals(1, child.children().size());
        assertEquals("span-grandchild", child.children().get(0).eventId());
    }

    @Test
    void separatesTracesByCorrelationId() throws Exception {
        Path logFile = temp.resolve("observability-multi.jsonl");
        StructuredObservabilityRecorder recorder = new StructuredObservabilityRecorder(logFile);
        recorder.record(new ObservabilityEvent(
                "span-a", Instant.now(), "onsure-core", Kind.TRACE_SPAN, "op-a", "corr-a", "", null, Map.of()));
        recorder.record(new ObservabilityEvent(
                "span-b", Instant.now(), "onsure-core", Kind.TRACE_SPAN, "op-b", "corr-b", "", null, Map.of()));

        List<Trace> traces = TraceExporter.readTraces(logFile);
        assertEquals(2, traces.size());
    }

    @Test
    void missingLogFileYieldsNoTraces() throws Exception {
        assertEquals(List.of(), TraceExporter.readTraces(temp.resolve("does-not-exist.jsonl")));
    }

    @Test
    void writesAValidJsonExportDocument() throws Exception {
        Path logFile = temp.resolve("observability-export.jsonl");
        StructuredObservabilityRecorder recorder = new StructuredObservabilityRecorder(logFile);
        recorder.record(new ObservabilityEvent(
                "span-root", Instant.now(), "onsure-core", Kind.TRACE_SPAN, "op", "corr-1", "", null, Map.of()));

        List<Trace> traces = TraceExporter.readTraces(logFile);
        Path exportFile = temp.resolve("trace-export.json");
        TraceExporter.writeExport(exportFile, traces);

        JsonNode exported = new ObjectMapper().readTree(exportFile.toFile());
        assertEquals(TraceExporter.EXPORT_CONTRACT, exported.path("contract").asText());
        assertEquals(1, exported.path("trace_count").asInt());
        assertTrue(exported.path("traces").isArray());
    }
}
