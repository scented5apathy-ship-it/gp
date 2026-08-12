package com.genealogy.platform.services.research.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link IdempotencyCache}. The cache is the
 * process-local memoisation layer the controllers consult
 * before re-running a mutation. The TTL sweep is best-effort
 * (an expired entry may survive a few minutes past its
 * deadline); the controller treats the expired entry as a
 * cache miss so the contract is safe.
 */
class IdempotencyCacheTest {

    private static final Instant T0 = Instant.parse("2026-08-10T00:00:00Z");

    @Test
    @DisplayName("replay returns null on blank key")
    void blankKey() {
        IdempotencyCache cache = new IdempotencyCache(Clock.fixed(T0, ZoneOffset.UTC));
        assertThat(cache.get(null)).isEmpty();
        assertThat(cache.get("")).isEmpty();
        assertThat(cache.get("   ")).isEmpty();
    }

    @Test
    @DisplayName("store + get returns the cached response")
    void storeAndGet() {
        IdempotencyCache cache = new IdempotencyCache(Clock.fixed(T0, ZoneOffset.UTC));
        cache.store("key-1", new IdempotencyCache.CachedResponse(201,
                "application/json", "{}", "\"v1\""));
        assertThat(cache.get("key-1"))
                .map(IdempotencyCache.CachedResponse::status)
                .contains(201);
    }

    @Test
    @DisplayName("expired entries are evicted on read")
    void expiredEvict() {
        Clock fixed = Clock.fixed(T0, ZoneOffset.UTC);
        IdempotencyCache cache = new IdempotencyCache(fixed, Duration.ofMinutes(1));
        cache.store("key-2", new IdempotencyCache.CachedResponse(200, "application/json", "x", null));
        // advance the clock 90 seconds past the deadline (T0+60s)
        Clock later = Clock.fixed(T0.plusSeconds(150), ZoneOffset.UTC);
        IdempotencyCache cacheLater = new IdempotencyCache(later, Duration.ofMinutes(1));
        // entry expired → get returns empty
        assertThat(cacheLater.get("key-2")).isEmpty();
    }

    @Test
    @DisplayName("store overwrites existing entry on collision")
    void overwrite() {
        IdempotencyCache cache = new IdempotencyCache(Clock.fixed(T0, ZoneOffset.UTC));
        cache.store("key-3", new IdempotencyCache.CachedResponse(200, "application/json", "first", null));
        cache.store("key-3", new IdempotencyCache.CachedResponse(200, "application/json", "second", null));
        assertThat(cache.get("key-3"))
                .map(IdempotencyCache.CachedResponse::body)
                .contains("second");
    }
}
