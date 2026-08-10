package com.genealogy.platform.services.genealogy.outbox;

import java.util.Objects;

/**
 * Producer abstraction. The relay publishes outbox rows
 * via this interface; the production implementation
 * wraps the Confluent / Apicurio Kafka client. The unit
 * tests use a fake ({@code FakeKafkaProducer}).
 *
 * <p>The interface is intentionally narrow so the relay
 * stays framework-free (no Spring, no Kafka client types
 * in the domain layer).
 */
public interface KafkaProducerPort {

    /**
     * Publishes the payload to the topic with the
     * supplied partition key + headers. Returns a
     * {@link PublishResult} the relay uses to decide
     * whether to mark the row PUBLISHED, retry, or
     * dead-letter.
     */
    PublishResult publish(String topic, String partitionKey,
            OutboxEventRecord row);

    /**
     * Publishes the payload to the dead-letter topic for
     * the given primary topic. Returns the same
     * {@link PublishResult} semantics.
     */
    PublishResult publishToDlq(String primaryTopic, OutboxEventRecord row,
            DlqReason reason, String error);

    /** Marker for the relay / tests. */
    final class UnreachableTopic extends RuntimeException {
        public UnreachableTopic(String message) {
            super(message);
        }
    }

    /** Marker for the relay / tests. */
    final class SchemaRejected extends RuntimeException {
        public SchemaRejected(String message) {
            super(message);
        }
    }

    /** Marker for the relay / tests. */
    final class TenantMismatch extends RuntimeException {
        public TenantMismatch(String message) {
            super(message);
        }
    }

    /** Marker for the relay / tests. */
    final class ForbiddenPayload extends RuntimeException {
        public ForbiddenPayload(String message) {
            super(message);
        }
    }

    /** Marker for the relay / tests. */
    final class PayloadTooLarge extends RuntimeException {
        public PayloadTooLarge(String message) {
            super(message);
        }
    }

    static String requireTopic(String topic) {
        Objects.requireNonNull(topic, "topic");
        if (topic.isBlank()) {
            throw new IllegalArgumentException("topic must not be blank");
        }
        return topic;
    }
}
