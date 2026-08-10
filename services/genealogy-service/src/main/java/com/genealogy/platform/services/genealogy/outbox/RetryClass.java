package com.genealogy.platform.services.genealogy.outbox;

import java.util.Locale;

/**
 * Closed-set classification of a relay failure. Mirrors
 * `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.retryClassClosedSet` (E4.7) and is used by
 * {@link OutboxRetryPolicy} to decide whether a failure
 * is retriable.
 *
 * <ul>
 *   <li>{@link #TRANSIENT} — recoverable. Retry with
 *       exponential backoff + jitter.
 *   <li>{@link #PERMANENT} — not recoverable. Move the
 *       row straight to DEAD_LETTERED.
 *   <li>{@link #RATE_LIMITED} — recoverable but bounded.
 *       Retry with extended backoff (the retry policy
 *       applies the standard backoff).
 *   <li>{@link #SCHEMA_MISMATCH} — not recoverable. Move
 *       the row to DEAD_LETTERED with
 *       {@code dlqReason = SCHEMA_INCOMPATIBLE}.
 * </ul>
 *
 * The wiring between this enum and the
 * {@link DlqReason} closed-set is intentional: a
 * {@link #SCHEMA_MISMATCH} retry class always pairs with
 * {@link DlqReason#SCHEMA_INCOMPATIBLE}; a
 * {@link #PERMANENT} retry class always pairs with
 * {@link DlqReason#SERIALIZATION_ERROR}.
 */
public enum RetryClass {
    TRANSIENT,
    PERMANENT,
    RATE_LIMITED,
    SCHEMA_MISMATCH;

    public static RetryClass fromWire(String wire) {
        if (wire == null) {
            return TRANSIENT;
        }
        return RetryClass.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isRetryable() {
        return this == TRANSIENT || this == RATE_LIMITED;
    }
}
