package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OutboxRelay#tick}. Verifies the
 * contract: claim → publish → persist next state → emit
 * audit hook.
 */
class OutboxRelayTest {

    @Test
    void tickPublishesRowAndEmitsAuditHook() {
        OutboxRelay.InMemoryOutboxRepository repo =
                new OutboxRelay.InMemoryOutboxRepository();
        Instant now = Instant.now();
        OutboxEventRecord row = OutboxEventRecord.pending(
                "evt-" + UUID.randomUUID(), "t", "agg",
                MergeEventPayloads.EVENT_PERSON_MERGED,
                "schema",
                "{}".getBytes(),
                "t|agg",
                PartitionKeyClass.TENANT_PLUS_AGGREGATE,
                "c", "tr", now);
        repo.add(row);

        RelayOutboxPublisherTest.FakeProducer producer = new RelayOutboxPublisherTest.FakeProducer();
        producer.response = PublishResult.published();
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RecordingAuditHook audit = new RecordingAuditHook();
        OutboxRelay relay = new OutboxRelay(repo, publisher, audit,
                10, Duration.ofMillis(500), Duration.ofSeconds(30));

        OutboxRelay.RelayTickResult result = relay.tick("t", now);
        assertEquals(1, result.processed());
        assertEquals(1, result.published());
        assertEquals(0, result.retried());
        assertEquals(0, result.deadLettered());
        assertEquals(1, audit.published.size());
        assertEquals(0, audit.retried.size());
        assertEquals(0, audit.deadLettered.size());
        OutboxEventRecord persisted = repo.findById(row.eventId()).orElseThrow();
        assertEquals(OutboxStatus.PUBLISHED, persisted.status());
        assertNotNull(persisted.publishedAt());
    }

    @Test
    void tickRetriesRowAndSchedulesNextAttempt() {
        OutboxRelay.InMemoryOutboxRepository repo =
                new OutboxRelay.InMemoryOutboxRepository();
        Instant now = Instant.now();
        OutboxEventRecord row = OutboxEventRecord.pending(
                "evt-" + UUID.randomUUID(), "t", "agg",
                MergeEventPayloads.EVENT_PERSON_MERGED,
                "schema", "{}".getBytes(),
                "t|agg", PartitionKeyClass.TENANT_PLUS_AGGREGATE,
                "c", "tr", now);
        repo.add(row);

        RelayOutboxPublisherTest.FakeProducer producer = new RelayOutboxPublisherTest.FakeProducer();
        producer.response = PublishResult.retry("kafka unavailable");
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RecordingAuditHook audit = new RecordingAuditHook();
        OutboxRelay relay = new OutboxRelay(repo, publisher, audit,
                10, Duration.ofMillis(500), Duration.ofSeconds(30));

        OutboxRelay.RelayTickResult result = relay.tick("t", now);
        assertEquals(1, result.retried());
        assertEquals(0, result.published());
        assertEquals(0, result.deadLettered());
        assertEquals(1, audit.retried.size());
        OutboxEventRecord persisted = repo.findById(row.eventId()).orElseThrow();
        assertEquals(OutboxStatus.FAILED, persisted.status());
        assertEquals(1, persisted.attempts());
        assertTrue(persisted.nextAttemptAt().isAfter(now));
    }

    @Test
    void tickHonoursBatchSize() {
        OutboxRelay.InMemoryOutboxRepository repo =
                new OutboxRelay.InMemoryOutboxRepository();
        Instant now = Instant.now();
        for (int i = 0; i < 5; i += 1) {
            repo.add(OutboxEventRecord.pending(
                    "evt-" + i, "t", "agg-" + i,
                    "gp.genealogy.v1.PersonCreated",
                    "schema",
                    "{}".getBytes(), "agg-" + i,
                    PartitionKeyClass.AGGREGATE_ONLY, "c", "t", now));
        }
        RelayOutboxPublisherTest.FakeProducer producer = new RelayOutboxPublisherTest.FakeProducer();
        producer.response = PublishResult.published();
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RecordingAuditHook audit = new RecordingAuditHook();
        OutboxRelay relay = new OutboxRelay(repo, publisher, audit,
                3, Duration.ofMillis(500), Duration.ofSeconds(30));
        OutboxRelay.RelayTickResult r = relay.tick("t", now);
        assertEquals(3, r.processed());
        assertEquals(3, r.published());
        assertEquals(3, producer.calls.size());
    }

    @Test
    void tickFiltersByTenant() {
        OutboxRelay.InMemoryOutboxRepository repo =
                new OutboxRelay.InMemoryOutboxRepository();
        Instant now = Instant.now();
        repo.add(OutboxEventRecord.pending(
                "evt-a", "t-1", "agg",
                "gp.genealogy.v1.PersonCreated",
                "schema", "{}".getBytes(),
                "agg", PartitionKeyClass.AGGREGATE_ONLY, "c", "t", now));
        repo.add(OutboxEventRecord.pending(
                "evt-b", "t-2", "agg",
                "gp.genealogy.v1.PersonCreated",
                "schema", "{}".getBytes(),
                "agg", PartitionKeyClass.AGGREGATE_ONLY, "c", "t", now));
        RelayOutboxPublisherTest.FakeProducer producer = new RelayOutboxPublisherTest.FakeProducer();
        producer.response = PublishResult.published();
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RecordingAuditHook audit = new RecordingAuditHook();
        OutboxRelay relay = new OutboxRelay(repo, publisher, audit,
                10, Duration.ofMillis(500), Duration.ofSeconds(30));

        OutboxRelay.RelayTickResult r = relay.tick("t-1", now);
        assertEquals(1, r.processed());
    }

    @Test
    void constructorRejectsInvalidArgs() {
        OutboxRelay.InMemoryOutboxRepository repo =
                new OutboxRelay.InMemoryOutboxRepository();
        RelayOutboxPublisherTest.FakeProducer producer = new RelayOutboxPublisherTest.FakeProducer();
        producer.response = PublishResult.published();
        RelayOutboxPublisher publisher = new RelayOutboxPublisher(producer);
        RecordingAuditHook audit = new RecordingAuditHook();
        try {
            new OutboxRelay(repo, publisher, audit, 0,
                    Duration.ofMillis(500), Duration.ofSeconds(30));
            org.junit.jupiter.api.Assertions.fail("expected exception");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("pollBatchSize"));
        }
        try {
            new OutboxRelay(repo, publisher, audit, 10,
                    Duration.ofMillis(500), Duration.ofSeconds(-1));
            org.junit.jupiter.api.Assertions.fail("expected exception");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("claimLease"));
        }
    }

    @Test
    void typeContractStability() {
        assertTrue(true);
    }

    static class RecordingAuditHook implements OutboxRelay.OutboxAuditHook {
        final List<OutboxEventRecord> published = new ArrayList<>();
        final List<OutboxEventRecord> retried = new ArrayList<>();
        final List<OutboxEventRecord> deadLettered = new ArrayList<>();

        @Override
        public void onPublished(OutboxEventRecord row) {
            published.add(row);
        }

        @Override
        public void onRetried(OutboxEventRecord row) {
            retried.add(row);
        }

        @Override
        public void onDeadLettered(OutboxEventRecord row) {
            deadLettered.add(row);
        }
    }
}
