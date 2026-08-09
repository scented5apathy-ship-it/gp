package com.genealogy.platform.services.tenant.application.outbox;

/**
 * Append-only port for {@link OutboxEvent} rows. The runtime
 * implementation persists the row inside the aggregate transaction
 * so that the relay (E4.7) can publish the event without losing the
 * mutation if the JVM crashes before Kafka ack.
 *
 * <p>The interface is intentionally narrow: a single {@code append}
 * call that the application service invokes once per mutation. The
 * runtime implementation is responsible for ensuring the insert
 * participates in the current transaction
 * ({@code Propagation.MANDATORY}).
 */
public interface OutboxWriter {

    void append(OutboxEvent event);
}
