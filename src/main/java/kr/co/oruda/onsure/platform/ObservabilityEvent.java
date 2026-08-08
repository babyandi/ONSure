package kr.co.oruda.onsure.platform;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * One structured observability record (NFR-OBS: Trace, Metric, Structured Log). Mirrors this
 * product's event envelope shape (event_id, occurred_at, producer, correlation_id, causation_id)
 * so observability events can be joined against the same correlation chain as product Evidence.
 */
public record ObservabilityEvent(
        String eventId,
        Instant occurredAt,
        String producer,
        Kind kind,
        String name,
        String correlationId,
        String causationId,
        Double metricValue,
        Map<String, String> attributes) {

    public enum Kind { LOG, METRIC, TRACE_SPAN }

    public ObservabilityEvent {
        if (eventId == null || eventId.isBlank()) throw new IllegalArgumentException("eventId");
        occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
        if (producer == null || producer.isBlank()) throw new IllegalArgumentException("producer");
        kind = Objects.requireNonNull(kind, "kind");
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name");
        if (correlationId == null || correlationId.isBlank()) throw new IllegalArgumentException("correlationId");
        causationId = causationId == null ? "" : causationId;
        if (kind == Kind.METRIC && metricValue == null) {
            throw new IllegalArgumentException("METRIC_EVENT_REQUIRES_METRIC_VALUE");
        }
        attributes = attributes == null ? Map.of() : Map.copyOf(attributes);
    }
}
