package com.genealogy.platform.services.genealogy.outbox;

import java.util.Locale;

/**
 * Closed-set lifecycle of an outbox row. Mirrors
 * `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.outboxStatusLifecycle` (E4.7) and the SQL CHECK
 * constraint in
 * `services/genealogy-service/src/main/resources/db/
 * migration/V8__outbox_relay.sql`.
 *
 * <ul>
 *   <li>{@link #PENDING} — freshly inserted by the writer.
 *       The relay will claim and publish it.
 *   <li>{@link #PUBLISHED} — terminal success. The relay
 *       has acknowledged the Kafka publish.
 *   <li>{@link #FAILED} — transient failure. The relay
 *       retries with exponential backoff + jitter until
 *       `maxAttempts` is reached, then transitions the
 *       row to {@link #DEAD_LETTERED}.
 *   <li>{@link #DEAD_LETTERED} — terminal failure. The
 *       row carries a `dlqReason` and an audit hook for
 *       operator triage. No further automatic retry.
 * </ul>
 *
 * Adding a new state requires an ADR supersession
 * (ADR-E0.5-08) and an update to the closed-set in the
 * contract; the lint-outbox-relay-config script enforces
 * the closed-set vocabulary.
 */
public enum OutboxStatus {
    PENDING,
    PUBLISHED,
    FAILED,
    DEAD_LETTERED;

    public static OutboxStatus fromWire(String wire) {
        if (wire == null) {
            return PENDING;
        }
        return OutboxStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isTerminal() {
        return this == PUBLISHED || this == DEAD_LETTERED;
    }
}
