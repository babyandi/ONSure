package kr.co.oruda.onsure.platform;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Append-only structured log sink plus in-memory metric aggregation (NFR-OBS: Trace, Metric,
 * Structured Log). Every {@link ObservabilityEvent} is written as one JSON line so external log
 * pipelines can tail the file without parsing multi-line records; metric events are additionally
 * aggregated in memory for operational queries.
 */
public final class StructuredObservabilityRecorder {
    private final ObjectMapper mapper = new ObjectMapper();
    private final Path logFile;
    private final Map<String, MetricAggregate> metrics = new LinkedHashMap<>();

    public record MetricAggregate(long count, double sum) {
        public double average() { return count == 0 ? 0.0 : sum / count; }
    }

    public StructuredObservabilityRecorder(Path logFile) {
        this.logFile = Objects.requireNonNull(logFile, "logFile").toAbsolutePath().normalize();
        if (Files.exists(this.logFile, LinkOption.NOFOLLOW_LINKS) && Files.isSymbolicLink(this.logFile)) {
            throw new IllegalArgumentException("OBSERVABILITY_LOG_SYMLINK_PROHIBITED");
        }
    }

    public synchronized void record(ObservabilityEvent event) throws IOException {
        Objects.requireNonNull(event, "event");
        String line = mapper.writeValueAsString(toWireMap(event));
        Files.writeString(
                logFile, line + System.lineSeparator(),
                StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        if (event.kind() == ObservabilityEvent.Kind.METRIC) {
            MetricAggregate existing = metrics.getOrDefault(event.name(), new MetricAggregate(0, 0.0));
            metrics.put(event.name(), new MetricAggregate(existing.count() + 1, existing.sum() + event.metricValue()));
        }
    }

    public synchronized MetricAggregate metricSummary(String name) {
        return metrics.getOrDefault(name, new MetricAggregate(0, 0.0));
    }

    public synchronized List<String> tail(int maxLines) throws IOException {
        if (maxLines < 1) throw new IllegalArgumentException("OBSERVABILITY_TAIL_LINES_INVALID");
        if (!Files.exists(logFile)) return List.of();
        ArrayDeque<String> window = new ArrayDeque<>(maxLines);
        for (String line : Files.readAllLines(logFile, StandardCharsets.UTF_8)) {
            if (window.size() == maxLines) window.removeFirst();
            window.addLast(line);
        }
        return List.copyOf(window);
    }

    private Map<String, Object> toWireMap(ObservabilityEvent event) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("event_id", event.eventId());
        wire.put("occurred_at", event.occurredAt().toString());
        wire.put("producer", event.producer());
        wire.put("kind", event.kind().name());
        wire.put("name", event.name());
        wire.put("correlation_id", event.correlationId());
        wire.put("causation_id", event.causationId());
        if (event.metricValue() != null) wire.put("metric_value", event.metricValue());
        wire.put("attributes", event.attributes());
        return wire;
    }
}
