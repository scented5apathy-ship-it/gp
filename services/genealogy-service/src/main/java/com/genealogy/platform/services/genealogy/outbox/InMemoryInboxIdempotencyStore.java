package com.genealogy.platform.services.genealogy.outbox;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory implementation of {@link InboxIdempotencyStore}.
 * Used by the unit tests and as a fallback when the relay
 * runs without Valkey / Redis.
 *
 * <p>Records that have been seen for longer than
 * {@code ttl} are evicted on {@link #tryClaim}. The
 * eviction is lazy (read-side) so the store never
 * accumulates stale rows.
 */
public final class InMemoryInboxIdempotencyStore implements InboxIdempotencyStore {

    private record Entry(Instant receivedAt) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Duration ttl;

    public InMemoryInboxIdempotencyStore(int ttlDays) {
        if (ttlDays <= 0) {
            throw new IllegalArgumentException("ttlDays must be > 0");
        }
        this.ttl = Duration.ofDays(ttlDays);
    }

    public InMemoryInboxIdempotencyStore() {
        this(7);
    }

    @Override
    public int ttlDays() {
        return (int) ttl.toDays();
    }

    @Override
    public boolean tryClaim(String eventId, String tenantId, String aggregateId,
            String schemaId, String payloadHash, String strategy,
            String correlationId, String traceId, Instant now) {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(now, "now");
        evictExpired(now);
        Entry existing = entries.putIfAbsent(eventId, new Entry(now));
        return existing == null;
    }

    @Override
    public Optional<Instant> receivedAt(String eventId) {
        Objects.requireNonNull(eventId, "eventId");
        Entry entry = entries.get(eventId);
        return entry == null ? Optional.empty() : Optional.of(entry.receivedAt);
    }

    private void evictExpired(Instant now) {
        Instant cutoff = now.minus(ttl);
        entries.entrySet().removeIf(e -> e.getValue().receivedAt.isBefore(cutoff));
    }
}
