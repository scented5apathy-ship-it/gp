package com.genealogy.platform.libs.security.abac;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class AbacDecisionCacheTest {

    @Test
    @DisplayName("MaxAge zero is rejected — TTL-only cache is forbidden")
    void zeroMaxAgeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new AbacDecisionCache.DecisionCacheConfig(Duration.ZERO, 16));
    }

    @Test
    @DisplayName("Negative maxAge is rejected")
    void negativeMaxAgeRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                new AbacDecisionCache.DecisionCacheConfig(Duration.ofSeconds(-1), 16));
    }

    @Test
    @DisplayName("invalidateByPrefix removes every matching entry and leaves the rest")
    void invalidateByPrefixRemovesMatches() {
        AbacDecisionCache cache = new AbacDecisionCache();
        cache.put("abac:t1:tree:a", AbacDecision.allow("d", AbacObligation.none()));
        cache.put("abac:t1:tree:b", AbacDecision.allow("d", AbacObligation.none()));
        cache.put("abac:t2:tree:a", AbacDecision.allow("d", AbacObligation.none()));

        int removed = cache.invalidateByPrefix("abac:t1:");
        assertEquals(2, removed);
        assertEquals(1, cache.size());
    }

    @Test
    @DisplayName("invalidate on a missing key is safe")
    void invalidateMissingSafe() {
        AbacDecisionCache cache = new AbacDecisionCache();
        cache.invalidate("does-not-exist");
        assertEquals(0, cache.size());
    }

    @Test
    @DisplayName("Default config has a positive maxAge")
    void defaultsArePositive() {
        AbacDecisionCache.DecisionCacheConfig defaults =
                AbacDecisionCache.DecisionCacheConfig.defaults();
        assertTrue(defaults.getMaxAge().toMillis() > 0);
    }
}
