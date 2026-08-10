package com.genealogy.platform.services.genealogy.outbox;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Test double that captures outbox enqueues without touching a
 * database. Mirrors the JDBC writer surface used by the command
 * service; tests assert on the captured list to verify the
 * correct event payloads are produced.
 */
public final class InMemoryOutboxWriter {

    public record Entry(String aggregateId,
                        String tenantId,
                        String eventType,
                        Object payload,
                        Instant occurredAt,
                        String correlationId) {
    }

    private final List<Entry> entries = new ArrayList<>();

    public String enqueue(String aggregateId,
                          String tenantId,
                          String eventType,
                          Object payload,
                          Instant occurredAt,
                          String correlationId) {
        entries.add(new Entry(aggregateId, tenantId, eventType, payload, occurredAt, correlationId));
        return "evt-" + entries.size();
    }

    public List<Entry> entries() {
        return List.copyOf(entries);
    }

    public Map<String, Long> countsByEventType() {
        Map<String, Long> counts = new java.util.LinkedHashMap<>();
        for (Entry e : entries) {
            counts.merge(e.eventType(), 1L, Long::sum);
        }
        return counts;
    }
}
