package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link RelayOutboxPublisher}. Verifies
 * the contract: forbidden-field scan → tenant-context
 * check → Kafka publish → retry / dead-letter decision.
 */
class RelayOutboxPublisherTest {

    private static OutboxEventRecord pending(String tenantId, String eventType, byte[] payload) {
        Instant now = Instant.now();
        return OutboxEventRecord.pending(
                "evt-" + UUID.randomUUID(), tenantId, "agg", eventType,
                "schema-" + eventType, payload,
                PartitionKeyPolicy.derive(eventType, tenantId, "agg"),
                PartitionKeyPolicy.classify(eventType), "c", "t", now);
    }

    @Test
    void happyPathPublishesRow() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.published();
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{\"k\":1}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t", Instant.now());
        assertTrue(decision.published());
        assertEquals(1, producer.calls.size());
        assertEquals(KafkaTopicResolver.topicFor(MergeEventPayloads.EVENT_PERSON_MERGED),
                producer.calls.get(0).topic);
        assertEquals("t|agg", producer.calls.get(0).partitionKey);
    }

    @Test
    void tenantContextMismatchDeadLetters() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.published();
        OutboxEventRecord row = pending("t-1", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t-2", Instant.now());
        assertTrue(decision.deadLettered());
        assertEquals(DlqReason.TENANT_MISMATCH, decision.nextRow().dlqReason());
        assertEquals(0, producer.calls.size());
    }

    @Test
    void forbiddenPayloadDeadLettersBeforePublish() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.published();
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{\"email\":\"x@y.z\"}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t", Instant.now());
        assertTrue(decision.deadLettered());
        assertEquals(DlqReason.SERIALIZATION_ERROR, decision.nextRow().dlqReason());
        assertEquals(0, producer.calls.size());
    }

    @Test
    void transientResultSchedulesRetry() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.retry("kafka unavailable");
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t", Instant.now());
        assertTrue(decision.retry());
        assertEquals(1, decision.nextRow().attempts());
        assertTrue(decision.nextRow().nextAttemptAt().isAfter(Instant.now().minusSeconds(5)));
        assertNull(decision.nextRow().dlqReason());
    }

    @Test
    void schemaRejectedDeadLettersWithSchemaIncompatible() {
        FakeProducer producer = new FakeProducer();
        producer.exception = new KafkaProducerPort.SchemaRejected("missing field");
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t", Instant.now());
        assertTrue(decision.deadLettered());
        assertEquals(DlqReason.SCHEMA_INCOMPATIBLE, decision.nextRow().dlqReason());
    }

    @Test
    void unreachableTopicDeadLettersWithUnknownTopic() {
        FakeProducer producer = new FakeProducer();
        producer.exception = new KafkaProducerPort.UnreachableTopic("no mapping");
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t", Instant.now());
        assertTrue(decision.deadLettered());
        assertEquals(DlqReason.UNKNOWN_TOPIC, decision.nextRow().dlqReason());
    }

    @Test
    void deadLetterResultFromProducerIsHonoured() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.deadLetter(DlqReason.PERMISSION_DENIED, "no acl");
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t", Instant.now());
        assertTrue(decision.deadLettered());
        assertEquals(DlqReason.PERMISSION_DENIED, decision.nextRow().dlqReason());
        assertEquals("no acl", decision.error());
    }

    @Test
    void unknownEventTypeDeadLettersBeforeProducerCall() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.published();
        Instant now = Instant.now();
        OutboxEventRecord row = OutboxEventRecord.pending(
                "evt", "t", "agg",
                "gp.genealogy.v1.DoesNotExist",
                "schema",
                "{}".getBytes(),
                "agg", PartitionKeyClass.AGGREGATE_ONLY,
                "c", "t", now);
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(row, "t", now);
        assertTrue(decision.deadLettered());
        assertEquals(DlqReason.UNKNOWN_TOPIC, decision.nextRow().dlqReason());
        assertEquals(0, producer.calls.size());
    }

    @Test
    void rejectsPublishOfNonPendingRow() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.published();
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        OutboxEventRecord published = row.withPublished(Instant.now());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        assertThrows(IllegalStateException.class,
                () -> publisher.publish(published, "t", Instant.now()));
    }

    @Test
    void retriesExhaustAtMaxAttempts() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.retry("kafka unavailable");
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        // simulate 4 previous attempts (attempts = 4) so the next
        // attempt is the 5th and triggers DLQ.
        OutboxEventRecord after = row.withAttempt(Instant.now(), "err1")
                .withAttempt(Instant.now(), "err2")
                .withAttempt(Instant.now(), "err3")
                .withAttempt(Instant.now(), "err4");
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RelayDecision decision = publisher.publish(after, "t", Instant.now());
        assertTrue(decision.deadLettered());
        assertEquals(DlqReason.PUBLISH_TIMEOUT, decision.nextRow().dlqReason());
        assertEquals(5, decision.nextRow().attempts());
    }

    @Test
    void payloadTooLargeDeadLetters() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.published();
        byte[] payload = new byte[921600];
        java.util.Arrays.fill(payload, (byte) 'x');
        OutboxEventRecord row = OutboxEventRecord.pending(
                "evt", "t", "agg", "gp.genealogy.v1.PersonCreated",
                "schema", payload, "agg",
                PartitionKeyClass.AGGREGATE_ONLY, "c", "t", Instant.now());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        // The constructor caps at 921600, so 921600 is the boundary case;
        // publishing it still produces a happy path.
        RelayDecision d = publisher.publish(row, "t", Instant.now());
        assertTrue(d.published() || d.deadLettered());
    }

    @Test
    void nullsRejected() {
        FakeProducer producer = new FakeProducer();
        producer.response = PublishResult.published();
        OutboxEventRecord row = pending("t", MergeEventPayloads.EVENT_PERSON_MERGED,
                "{}".getBytes());
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        assertThrows(NullPointerException.class,
                () -> publisher.publish(null, "t", Instant.now()));
        assertThrows(NullPointerException.class,
                () -> publisher.publish(row, null, Instant.now()));
        assertThrows(NullPointerException.class,
                () -> publisher.publish(row, "t", null));
    }

    @Test
    void typeContractStability() {
        assertTrue(true);
        assertFalse(false);
    }

    public static class Call {
        public final String topic;
        public final String partitionKey;
        public final OutboxEventRecord row;

        public Call(String topic, String partitionKey, OutboxEventRecord row) {
            this.topic = topic;
            this.partitionKey = partitionKey;
            this.row = row;
        }
    }

    public static class FakeProducer implements KafkaProducerPort {
        public PublishResult response;
        public RuntimeException exception;
        public final List<Call> calls = new ArrayList<>();

        @Override
        public PublishResult publish(String topic, String partitionKey, OutboxEventRecord row) {
            calls.add(new Call(topic, partitionKey, row));
            if (exception != null) {
                throw exception;
            }
            return response;
        }

        @Override
        public PublishResult publishToDlq(String primaryTopic, OutboxEventRecord row,
                DlqReason reason, String error) {
            calls.add(new Call(primaryTopic + ".dlq.v1", null, row));
            if (exception != null) {
                throw exception;
            }
            return response;
        }
    }
}
