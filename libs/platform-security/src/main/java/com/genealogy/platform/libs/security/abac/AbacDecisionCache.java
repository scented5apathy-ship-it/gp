package com.genealogy.platform.libs.security.abac;

import java.time.Clock;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Short-lived cache for ABAC decisions.
 *
 * <p>Per {@code design.md} §6.2 ("cache policy must be invalidated
 * when role/policy/consent change") this cache is
 * <strong>never</strong> a TTL-only cache — every entry MUST carry
 * an explicit {@link #invalidate(String) invalidate} path, called by
 * the role / policy / consent write flows. The TTL on
 * {@link DecisionCacheConfig#getMaxAge()} is the upper bound for
 * emergency-only staleness; the cache MUST NOT be the source of
 * truth for authorisation.
 *
 * <p>Per ADR-E0.5-06 ("cache invalidation mandatory on every Write,
 * TTL-only forbidden") the same invariant applies to OpenFGA; the
 * ABAC cache follows the same discipline because every allow /
 * deny here maps to the same eventual-consistency window.
 */
public final class AbacDecisionCache {

    private final DecisionCacheConfig config;
    private final Clock clock;
    private final ConcurrentMap<String, Entry> entries = new ConcurrentHashMap<>();

    public AbacDecisionCache() {
        this(DecisionCacheConfig.defaults(), Clock.systemUTC());
    }

    public AbacDecisionCache(DecisionCacheConfig config, Clock clock) {
        this.config = Objects.requireNonNull(config, "config");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public DecisionCacheConfig config() {
        return config;
    }

    /**
     * Returns the cached decision for the key, or {@code null} when
     * the entry is missing or has expired. The cache never returns
     * a partially-applied decision (e.g. one with an audit
     * obligation that was already emitted): callers re-validate
     * audit obligations on every hit when {@code strict} is true.
     */
    public AbacDecision get(String key) {
        Objects.requireNonNull(key, "key");
        Entry entry = entries.get(key);
        if (entry == null) {
            return null;
        }
        long now = clock.millis();
        long ageMs = now - entry.storedAtMs;
        if (ageMs > config.getMaxAge().toMillis()) {
            entries.remove(key, entry);
            return null;
        }
        return entry.decision;
    }

    /** Stores the decision with the supplied key. */
    public void put(String key, AbacDecision decision) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(decision, "decision");
        entries.put(key, new Entry(decision, clock.millis()));
    }

    /** Removes a single entry. Safe to call when the key is absent. */
    public void invalidate(String key) {
        Objects.requireNonNull(key, "key");
        entries.remove(key);
    }

    /**
     * Removes every cached decision whose key starts with the
     * supplied prefix. Used by role / policy / consent revocation
     * flows (E3.4 acceptance criterion: invalidation when role /
     * policy / consent change).
     */
    public int invalidateByPrefix(String prefix) {
        Objects.requireNonNull(prefix, "prefix");
        int removed = 0;
        for (String key : entries.keySet()) {
            if (key.startsWith(prefix) && entries.remove(key) != null) {
                removed++;
            }
        }
        return removed;
    }

    /** Clears the entire cache. Used by administrative flows only. */
    public int invalidateAll() {
        int size = entries.size();
        entries.clear();
        return size;
    }

    /** Snapshot of the current entry count. */
    public int size() {
        return entries.size();
    }

    /** Cache configuration record. */
    public static final class DecisionCacheConfig {
        private final Duration maxAge;
        private final int maxEntries;

        public DecisionCacheConfig(Duration maxAge, int maxEntries) {
            this.maxAge = Objects.requireNonNull(maxAge, "maxAge");
            if (maxAge.isNegative() || maxAge.isZero()) {
                throw new IllegalArgumentException(
                        "maxAge must be positive (cache.invalidationOnWrite policy)");
            }
            if (maxEntries <= 0) {
                throw new IllegalArgumentException(
                        "maxEntries must be positive");
            }
            this.maxEntries = maxEntries;
        }

        public Duration getMaxAge() {
            return maxAge;
        }

        public int getMaxEntries() {
            return maxEntries;
        }

        public static DecisionCacheConfig defaults() {
            return new DecisionCacheConfig(Duration.ofSeconds(5), 4096);
        }
    }

    private static final class Entry {
        private final AbacDecision decision;
        private final long storedAtMs;

        Entry(AbacDecision decision, long storedAtMs) {
            this.decision = decision;
            this.storedAtMs = storedAtMs;
        }
    }
}
