package kr.co.oruda.onsure.platform;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Map;
import kr.co.oruda.onsure.platform.ObservabilityEvent.Kind;
import kr.co.oruda.onsure.platform.StructuredObservabilityRecorder.MetricAggregate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class StructuredObservabilityRecorderTest {
    @TempDir Path temp;

    @Test
    void appendsOneJsonLinePerEventAndPreservesCorrelationChain() throws Exception {
        var recorder = new StructuredObservabilityRecorder(temp.resolve("events.jsonl"));
        recorder.record(new ObservabilityEvent(
                "evt-1", Instant.parse("2026-08-08T00:00:00Z"), "OVerification", Kind.LOG,
                "verification.started", "corr-1", "", null, Map.of("case_id", "CASE-1")));
        recorder.record(new ObservabilityEvent(
                "evt-2", Instant.parse("2026-08-08T00:00:01Z"), "OVerification", Kind.LOG,
                "verification.completed", "corr-1", "evt-1", null, Map.of("decision", "PASS")));

        var lines = recorder.tail(10);
        assertEquals(2, lines.size());
        assertTrue(lines.get(0).contains("\"correlation_id\":\"corr-1\""));
        assertTrue(lines.get(1).contains("\"causation_id\":\"evt-1\""));
        assertTrue(lines.get(0).contains("\"kind\":\"LOG\""));
    }

    @Test
    void aggregatesMetricEventsByName() throws Exception {
        var recorder = new StructuredObservabilityRecorder(temp.resolve("events.jsonl"));
        for (double value : new double[] {100.0, 200.0, 300.0}) {
            recorder.record(new ObservabilityEvent(
                    "evt-" + value, Instant.now(), "OVerification", Kind.METRIC,
                    "verification.duration_ms", "corr-2", "", value, Map.of()));
        }
        MetricAggregate summary = recorder.metricSummary("verification.duration_ms");
        assertEquals(3, summary.count());
        assertEquals(600.0, summary.sum());
        assertEquals(200.0, summary.average());
        assertEquals(0, recorder.metricSummary("unknown.metric").count());
    }

    @Test
    void metricEventWithoutValueIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> new ObservabilityEvent(
                "evt-x", Instant.now(), "OVerification", Kind.METRIC,
                "bad.metric", "corr-3", "", null, Map.of()));
    }

    @Test
    void tailReturnsOnlyTheMostRecentRequestedLines() throws Exception {
        var recorder = new StructuredObservabilityRecorder(temp.resolve("events.jsonl"));
        for (int i = 0; i < 5; i++) {
            recorder.record(new ObservabilityEvent(
                    "evt-" + i, Instant.now(), "OVerification", Kind.LOG,
                    "tick", "corr-4", "", null, Map.of("i", String.valueOf(i))));
        }
        var lastTwo = recorder.tail(2);
        assertEquals(2, lastTwo.size());
        assertTrue(lastTwo.get(0).contains("\"i\":\"3\""));
        assertTrue(lastTwo.get(1).contains("\"i\":\"4\""));
    }

    @Test
    void rejectsSymlinkedLogFile() throws Exception {
        Path real = temp.resolve("real.jsonl");
        Files.writeString(real, "");
        Path link = temp.resolve("link.jsonl");
        try {
            Files.createSymbolicLink(link, real);
        } catch (UnsupportedOperationException | java.io.IOException unsupported) {
            return; // filesystem does not support symlinks in this environment; skip
        }
        assertThrows(IllegalArgumentException.class, () -> new StructuredObservabilityRecorder(link));
    }
}
