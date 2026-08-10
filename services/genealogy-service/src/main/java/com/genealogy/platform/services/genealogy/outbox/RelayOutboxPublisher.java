package com.genealogy.platform.services.genealogy.outbox;

import java.time.Instant;
import java.util.Objects;

/**
 * Single-row relay publisher. Applies the contract:
 * forbidden-field scan → tenant-context check →
 * Kafka publish → retry / dead-letter decision.
 *
 * <p>The publisher is pure: it does NOT touch the
 * database (that is {@link OutboxRelay}'s job); it only
 * maps a {@link OutboxEventRecord} to a
 * {@link PublishResult} (and the next-action state).
 *
 * <p>Thread-safe and stateless. The relay can hold a
 * single instance for the lifetime of the process.
 */
public final class RelayOutboxPublisher {

    private final KafkaProducerPort producer;

    public RelayOutboxPublisher(KafkaProducerPort producer) {
        this.producer = Objects.requireNonNull(producer, "producer");
    }

    /**
     * Publishes a single row and returns the next-state
     * {@link RelayDecision}. The caller (relay loop) is
     * responsible for persisting the decision back to
     * the database in the same transaction as any other
     * aggregate work.
     *
     * @param row the outbox row to publish (must be in
     *            {@link OutboxStatus#PENDING})
     * @param tenantContext the trusted tenant id from
     *                       the request context (defense
     *                       in depth on top of RLS)
     * @param now the wall-clock time used to set
     *            {@code lastAttemptAt} / {@code publishedAt}
     */
    public RelayDecision publish(OutboxEventRecord row, String tenantContext, Instant now) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(tenantContext, "tenantContext");
        Objects.requireNonNull(now, "now");
        if (row.status() != OutboxStatus.PENDING && row.status() != OutboxStatus.FAILED) {
            throw new IllegalStateException(
                    "cannot publish row in status " + row.status());
        }
        if (!row.tenantId().equals(tenantContext)) {
            return RelayDecision.deadLetter(row,
                    DlqReason.TENANT_MISMATCH,
                    "tenantContext=" + tenantContext
                            + " != row.tenantId=" + row.tenantId());
        }
        try {
            PayloadForbiddenFieldScan.check(row.payload());
        } catch (KafkaProducerPort.ForbiddenPayload ex) {
            return RelayDecision.deadLetter(row, DlqReason.SERIALIZATION_ERROR, ex.getMessage());
        }
        if (row.payloadByteSize() > 921600) {
            return RelayDecision.deadLetter(row, DlqReason.SERIALIZATION_ERROR,
                    "payload too large: " + row.payloadByteSize());
        }
        String topic;
        try {
            topic = KafkaTopicResolver.topicFor(row.eventType());
        } catch (IllegalArgumentException ex) {
            return RelayDecision.deadLetter(row, DlqReason.UNKNOWN_TOPIC, ex.getMessage());
        }
        try {
            PublishResult result = producer.publish(topic, row.partitionKey(), row);
            return switch (result.outcome()) {
                case PUBLISHED -> RelayDecision.published(row, now);
                case RETRY -> RelayDecision.retry(row, now, result.error());
                case DEAD_LETTER -> RelayDecision.deadLetter(row,
                        result.dlqReason() == null ? DlqReason.PUBLISH_TIMEOUT : result.dlqReason(),
                        result.error());
            };
        } catch (KafkaProducerPort.SchemaRejected ex) {
            return RelayDecision.deadLetter(row, DlqReason.SCHEMA_INCOMPATIBLE, ex.getMessage());
        } catch (KafkaProducerPort.ForbiddenPayload ex) {
            return RelayDecision.deadLetter(row, DlqReason.SERIALIZATION_ERROR, ex.getMessage());
        } catch (KafkaProducerPort.PayloadTooLarge ex) {
            return RelayDecision.deadLetter(row, DlqReason.SERIALIZATION_ERROR, ex.getMessage());
        } catch (KafkaProducerPort.TenantMismatch ex) {
            return RelayDecision.deadLetter(row, DlqReason.TENANT_MISMATCH, ex.getMessage());
        } catch (KafkaProducerPort.UnreachableTopic ex) {
            return RelayDecision.deadLetter(row, DlqReason.UNKNOWN_TOPIC, ex.getMessage());
        }
    }
}
