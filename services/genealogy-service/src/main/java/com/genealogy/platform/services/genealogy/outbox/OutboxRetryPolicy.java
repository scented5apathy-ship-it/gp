package com.genealogy.platform.services.genealogy.outbox;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Exponential-backoff-with-jitter retry policy. Mirrors
 * `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.{maxAttempts,initialBackoffSeconds,
 * maxBackoffSeconds,backoffMultiplier,jitterFactor}`
 * (E4.7) + ADR-E0.5-08.
 *
 * <p>The math:
 * <pre>
 *   base   = min(maxBackoff, initialBackoff * multiplier^(attempt - 1))
 *   jitter = base * jitterFactor * (2 * random - 1)   // [-jitter, +jitter]
 *   delay  = max(0, base + jitter)
 * </pre>
 *
 * <p>Pinned thresholds per the contract:
 * {@code maxAttempts = 5}, {@code initialBackoff = 1s},
 * {@code maxBackoff = 60s}, {@code multiplier = 2},
 * {@code jitterFactor = 0.25}. After
 * {@link #maxAttempts} the row is moved to
 * {@link OutboxStatus#DEAD_LETTERED}.
 */
public record OutboxRetryPolicy(
        int maxAttempts,
        Duration initialBackoff,
        Duration maxBackoff,
        double multiplier,
        double jitterFactor) {

    public OutboxRetryPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be > 0");
        }
        Objects.requireNonNull(initialBackoff, "initialBackoff");
        Objects.requireNonNull(maxBackoff, "maxBackoff");
        if (initialBackoff.isNegative() || initialBackoff.isZero()) {
            throw new IllegalArgumentException("initialBackoff must be > 0");
        }
        if (maxBackoff.compareTo(initialBackoff) < 0) {
            throw new IllegalArgumentException("maxBackoff must be >= initialBackoff");
        }
        if (multiplier <= 1.0) {
            throw new IllegalArgumentException("multiplier must be > 1.0");
        }
        if (jitterFactor < 0.0 || jitterFactor > 1.0) {
            throw new IllegalArgumentException("jitterFactor must be in [0, 1]");
        }
    }

    /** Pinned defaults from the contract. */
    public static OutboxRetryPolicy defaults() {
        return new OutboxRetryPolicy(5, Duration.ofSeconds(1), Duration.ofSeconds(60), 2.0, 0.25);
    }

    /**
     * Returns the delay before the {@code nextAttempt} is
     * eligible for retry.
     */
    public Duration backoffFor(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        double baseSeconds = initialBackoff.toMillis() / 1000.0;
        for (int i = 1; i < attempt; i += 1) {
            baseSeconds *= multiplier;
            if (baseSeconds >= maxBackoff.toMillis() / 1000.0) {
                baseSeconds = maxBackoff.toMillis() / 1000.0;
                break;
            }
        }
        double jitterMillis = baseSeconds * 1000.0 * jitterFactor
                * (2.0 * ThreadLocalRandom.current().nextDouble() - 1.0);
        long delayMillis = Math.max(0L, Math.round(baseSeconds * 1000.0 + jitterMillis));
        return Duration.ofMillis(delayMillis);
    }

    /** Returns the unclamped (jitterless) base backoff for documentation / tests. */
    public Duration baseBackoffFor(int attempt) {
        if (attempt < 1) {
            throw new IllegalArgumentException("attempt must be >= 1");
        }
        double baseSeconds = initialBackoff.toMillis() / 1000.0;
        for (int i = 1; i < attempt; i += 1) {
            baseSeconds *= multiplier;
            if (baseSeconds >= maxBackoff.toMillis() / 1000.0) {
                return maxBackoff;
            }
        }
        if (baseSeconds * 1000.0 > maxBackoff.toMillis()) {
            return maxBackoff;
        }
        return Duration.ofMillis(Math.round(baseSeconds * 1000.0));
    }

    /** Returns true iff the row should be moved to DEAD_LETTERED. */
    public boolean isExhausted(int attempts) {
        return attempts >= maxAttempts;
    }
}
