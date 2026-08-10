package com.genealogy.platform.services.genealogy.outbox;

import java.time.Instant;
import java.util.Optional;

/**
 * Consumer-side dedup store. Mirrors
 * `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.{defaultIdempotencyStrategy,
 * inboxIdempotencyTtlDays}` (E4.7) + `design.md` §7.3
 * ("Consumer inbox/idempotency; retry topic và
 * dead-letter có quy trình replay").
 *
 * <p>The contract's default strategy is {@code EVENT_ID};
 * the alternate {@code AGGREGATE_VERSION} strategy lets a
 * consumer dedup by aggregate version (at-least-once +
 * replay-safe). {@code SCHEMA_HASH} is reserved.
 */
public interface InboxIdempotencyStore {

    /**
     * Atomically checks whether {@code eventId} has been
     * seen and, if not, records it. Returns {@code true}
     * iff this is the first time the consumer sees this
     * event (i.e. the caller MUST process the payload).
     * Returns {@code false} iff the event was already
     * processed (the caller MUST skip it).
     */
    boolean tryClaim(String eventId, String tenantId, String aggregateId,
            String schemaId, String payloadHash, String strategy,
            String correlationId, String traceId, Instant now);

    /** TTL config the relay reads from the contract. */
    int ttlDays();

    /** Optional peek for tests / observability. */
    Optional<Instant> receivedAt(String eventId);
}
