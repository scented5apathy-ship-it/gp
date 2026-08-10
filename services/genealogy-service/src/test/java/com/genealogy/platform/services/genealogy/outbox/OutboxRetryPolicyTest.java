package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OutboxRetryPolicy}. Mirrors the
 * pinned thresholds in
 * `contracts/genealogy/outbox-relay-policy.yaml`.
 */
class OutboxRetryPolicyTest {

    @Test
    void defaultsMatchContract() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
        assertEquals(5, policy.maxAttempts());
        assertEquals(Duration.ofSeconds(1), policy.initialBackoff());
        assertEquals(Duration.ofSeconds(60), policy.maxBackoff());
        assertEquals(2.0, policy.multiplier());
        assertEquals(0.25, policy.jitterFactor());
    }

    @Test
    void backoffIsMonotonicAndClampedAtMax() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(
                5, Duration.ofSeconds(1), Duration.ofSeconds(60), 2.0, 0.0);
        assertTrue(policy.backoffFor(1).toMillis() >= 900);
        assertTrue(policy.backoffFor(1).toMillis() <= 1100);
        assertTrue(policy.backoffFor(2).toMillis() >= 1900);
        assertTrue(policy.backoffFor(2).toMillis() <= 2100);
        assertTrue(policy.backoffFor(3).toMillis() >= 3900);
        assertTrue(policy.backoffFor(3).toMillis() <= 4100);
        assertTrue(policy.backoffFor(4).toMillis() >= 7900);
        assertTrue(policy.backoffFor(4).toMillis() <= 8100);
        assertTrue(policy.backoffFor(5).toMillis() >= 15_900);
        assertTrue(policy.backoffFor(5).toMillis() <= 16_100);
        assertEquals(60_000L, policy.baseBackoffFor(7).toMillis());
        assertEquals(60_000L, policy.baseBackoffFor(10).toMillis());
    }

    @Test
    void jitterStaysWithinContractFactor() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(
                5, Duration.ofSeconds(10), Duration.ofSeconds(60), 2.0, 0.25);
        for (int i = 0; i < 100; i += 1) {
            long ms = policy.backoffFor(1).toMillis();
            assertTrue(ms >= 7_500 && ms <= 12_500,
                    "jittered backoff out of bounds: " + ms);
        }
    }

    @Test
    void isExhaustedReturnsTrueAtOrAboveMaxAttempts() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
        assertFalse(policy.isExhausted(0));
        assertFalse(policy.isExhausted(4));
        assertTrue(policy.isExhausted(5));
        assertTrue(policy.isExhausted(6));
    }

    @Test
    void rejectsInvalidConstruction() {
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxRetryPolicy(0, Duration.ofSeconds(1),
                        Duration.ofSeconds(60), 2.0, 0.25));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxRetryPolicy(5, Duration.ofSeconds(0),
                        Duration.ofSeconds(60), 2.0, 0.25));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxRetryPolicy(5, Duration.ofSeconds(10),
                        Duration.ofSeconds(5), 2.0, 0.25));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxRetryPolicy(5, Duration.ofSeconds(1),
                        Duration.ofSeconds(60), 1.0, 0.25));
        assertThrows(IllegalArgumentException.class,
                () -> new OutboxRetryPolicy(5, Duration.ofSeconds(1),
                        Duration.ofSeconds(60), 2.0, 1.5));
    }

    @Test
    void attemptMustBeAtLeastOne() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
        assertThrows(IllegalArgumentException.class, () -> policy.backoffFor(0));
    }

    @Test
    void smokeProducesNonNegativeBackoff() {
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
        for (int attempt = 1; attempt <= 5; attempt += 1) {
            assertTrue(policy.backoffFor(attempt).toMillis() >= 0);
        }
    }

    @Test
    void typeContractStability() {
        assertEquals(5, OutboxRetryPolicy.defaults().maxAttempts());
        Instant now = Instant.now();
        assertTrue(OutboxRetryPolicy.defaults().backoffFor(1)
                .compareTo(Duration.ZERO) >= 0);
        assertTrue(now.plusMillis(1).isAfter(now));
    }
}
