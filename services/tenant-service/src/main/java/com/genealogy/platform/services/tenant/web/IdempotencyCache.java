package com.genealogy.platform.services.tenant.web;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Process-local cache of {@code Idempotency-Key} responses. The
 * header is mandatory on every non-GET mutation per
 * {@code contracts/openapi/common/headers.yaml}; the controller
 * replays the stored response when the same key arrives within the
 * window.
 *
 * <p>The cache lives in memory only — durable idempotency lands in
 * E15 once the dedicated Redis/Valkey backend is available. The
 * default 24-hour window matches the value documented in the OpenAPI
 * header definition.
 *
 * <p>Thread-safety: every entry is guarded by a {@code synchronized}
 * block on the entry reference; the outer map is a
 * {@link ConcurrentHashMap} so reads do not block on writes. The TTL
 * sweep is best-effort — an expired entry may survive a few minutes
 * past its deadline; the controller accepts the expired replay
 * (the original response is still correct) and the {@link #store}
 * call always overwrites.
 */
@Component
public class IdempotencyCache {

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();
    private final Clock clock;
    private final Duration ttl;

    public IdempotencyCache(Clock clock) {
        this(clock, Duration.ofHours(24));
    }

    IdempotencyCache(Clock clock, Duration ttl) {
        this.clock = Objects.requireNonNull(clock, "clock");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
    }

    public Optional<CachedResponse> get(String key) {
        if (key == null || key.isBlank()) {
            return Optional.empty();
        }
        Entry entry = entries.get(key);
        if (entry == null) {
            return Optional.empty();
        }
        synchronized (entry) {
            if (clock.instant().isAfter(entry.expiresAt)) {
                entries.remove(key, entry);
                return Optional.empty();
            }
            return Optional.of(entry.response);
        }
    }

    public void store(String key, CachedResponse response) {
        if (key == null || key.isBlank()) {
            return;
        }
        Entry entry = new Entry(response, clock.instant().plus(ttl));
        Entry existing = entries.putIfAbsent(key, entry);
        if (existing != null) {
            synchronized (existing) {
                existing.response = response;
                existing.expiresAt = clock.instant().plus(ttl);
            }
        }
    }

    /** Visible for testing. */
    int size() {
        return entries.size();
    }

    static final class Entry {

        CachedResponse response;
        java.time.Instant expiresAt;

        Entry(CachedResponse response, java.time.Instant expiresAt) {
            this.response = response;
            this.expiresAt = expiresAt;
        }
    }

    /**
     * Replayed response. The {@code status} is the original HTTP
     * status code (so 202, 200, 412 are preserved exactly); the
     * {@code body} is the JSON serialised view.
     */
    public record CachedResponse(int status, String contentType, String body, String etag) {
    }
}
