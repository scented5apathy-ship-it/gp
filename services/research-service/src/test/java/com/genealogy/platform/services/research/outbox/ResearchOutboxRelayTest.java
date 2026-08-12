package com.genealogy.platform.services.research.outbox;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class ResearchOutboxRelayTest {

    private static ResearchOutboxEventRecord pendingRow(String tenantId, String eventType) {
        return ResearchOutboxEventRecord.pending(
                "evt-" + java.util.UUID.randomUUID(),
                tenantId,
                "agg-1",
                eventType,
                ResearchPartitionKeyPolicy.schemaId(eventType),
                "{\"claimReference\":\"r-1\"}",
                "agg-1",
                ResearchPartitionKeyClass.AGGREGATE_ONLY,
                "corr-1",
                "trace-1",
                Instant.parse("2026-08-01T00:00:00Z"));
    }

    private static List<ResearchOutboxEventRecord> snapshot(
            ResearchOutboxRelay.InMemoryOutboxRepository repo) {
        return new ArrayList<>(repo.snapshot().values());
    }

    @Test
    @DisplayName("publish: row flips to PUBLISHED on first attempt with successful producer")
    void publishHappyPath() {
        ResearchOutboxRelay.InMemoryOutboxRepository repo =
                new ResearchOutboxRelay.InMemoryOutboxRepository(
                        List.of(pendingRow("tenant-a", "gp.research.v1.CitationCreated")));
        ResearchKafkaProducerPort producer = (row, now) -> ResearchKafkaProducerPort.Result.published();
        ResearchPayloadForbiddenFieldScan scan = new ResearchPayloadForbiddenFieldScan();
        ResearchOutboxRelay.ResearchOutboxAuditHook hook = new NoopAuditHook();
        ResearchOutboxRelay relay = new ResearchOutboxRelay(
                repo, producer, scan, hook,
                10, Duration.ofMillis(1000), Duration.ofMillis(30_000), 5);
        ResearchOutboxRelay.RelayTickResult result = relay.tick(
                "tenant-a", Instant.parse("2026-08-01T00:00:01Z"));
        assertThat(result.published()).isEqualTo(1);
        assertThat(result.retried()).isZero();
        assertThat(result.deadLettered()).isZero();
        List<ResearchOutboxEventRecord> rows = snapshot(repo);
        assertThat(rows).hasSize(1);
        ResearchOutboxEventRecord r = rows.get(0);
        assertThat(r.status()).isEqualTo(ResearchOutboxStatus.PUBLISHED);
        assertThat(r.publishedAt()).isNotNull();
    }

    @Test
    @DisplayName("forbidden field in payload → DEAD_LETTERED, never published")
    void forbiddenFieldShortcutsToDeadLetter() {
        ResearchOutboxRelay.InMemoryOutboxRepository repo =
                new ResearchOutboxRelay.InMemoryOutboxRepository();
        repo.add(ResearchOutboxEventRecord.pending(
                "evt-1",
                "tenant-a",
                "agg-1",
                "gp.research.v1.CitationCreated",
                "research/v1/citation-created.avsc",
                "{\"dnaRaw\":\"hidden\",\"citationId\":\"cite-1\"}",
                "agg-1",
                ResearchPartitionKeyClass.AGGREGATE_ONLY,
                "corr-1",
                "trace-1",
                Instant.parse("2026-08-01T00:00:00Z")));
        ResearchKafkaProducerPort producer = (row, now) -> {
            throw new AssertionError("producer must NEVER be called for a forbidden field");
        };
        ResearchPayloadForbiddenFieldScan scan = new ResearchPayloadForbiddenFieldScan();
        ResearchOutboxRelay.ResearchOutboxAuditHook hook = new NoopAuditHook();
        ResearchOutboxRelay relay = new ResearchOutboxRelay(
                repo, producer, scan, hook,
                10, Duration.ofMillis(1000), Duration.ofMillis(30_000), 5);
        ResearchOutboxRelay.RelayTickResult result = relay.tick(
                "tenant-a", Instant.parse("2026-08-01T00:00:01Z"));
        assertThat(result.deadLettered()).isEqualTo(1);
        List<ResearchOutboxEventRecord> rows = snapshot(repo);
        assertThat(rows).hasSize(1);
        ResearchOutboxEventRecord r = rows.get(0);
        assertThat(r.status()).isEqualTo(ResearchOutboxStatus.DEAD_LETTERED);
        assertThat(r.dlqReason()).isEqualTo(ResearchDlqReason.PAYLOAD_ENCODE_FAILED);
    }

    @Test
    @DisplayName("transient failure → FAILED + retry attempt counter increments")
    void transientFailureRetries() {
        ResearchOutboxRelay.InMemoryOutboxRepository repo =
                new ResearchOutboxRelay.InMemoryOutboxRepository(
                        List.of(pendingRow("tenant-a", "gp.research.v1.CitationCreated")));
        ResearchKafkaProducerPort producer = (row, now) ->
                ResearchKafkaProducerPort.Result.transientFailure("broker timeout");
        ResearchPayloadForbiddenFieldScan scan = new ResearchPayloadForbiddenFieldScan();
        ResearchOutboxRelay.ResearchOutboxAuditHook hook = new NoopAuditHook();
        ResearchOutboxRelay relay = new ResearchOutboxRelay(
                repo, producer, scan, hook,
                10, Duration.ofMillis(1000), Duration.ofMillis(30_000), 5);
        ResearchOutboxRelay.RelayTickResult result = relay.tick(
                "tenant-a", Instant.parse("2026-08-01T00:00:01Z"));
        assertThat(result.retried()).isEqualTo(1);
        List<ResearchOutboxEventRecord> rows = snapshot(repo);
        assertThat(rows).hasSize(1);
        ResearchOutboxEventRecord r = rows.get(0);
        assertThat(r.status()).isEqualTo(ResearchOutboxStatus.FAILED);
        assertThat(r.attempts()).isEqualTo(1);
        assertThat(r.lastError()).isEqualTo("broker timeout");
    }

    @Test
    @DisplayName("max attempts reached → DEAD_LETTERED with PRODUCER_RETRY_EXHAUSTED")
    void maxAttemptsReached() {
        ResearchOutboxRelay.InMemoryOutboxRepository repo =
                new ResearchOutboxRelay.InMemoryOutboxRepository();
        repo.add(ResearchOutboxEventRecord.pending(
                "evt-1",
                "tenant-a",
                "agg-1",
                "gp.research.v1.CitationCreated",
                "research/v1/citation-created.avsc",
                "{\"citationId\":\"cite-1\"}",
                "agg-1",
                ResearchPartitionKeyClass.AGGREGATE_ONLY,
                "corr-1",
                "trace-1",
                Instant.parse("2026-08-01T00:00:00Z")));
        ResearchKafkaProducerPort producer = (row, now) ->
                ResearchKafkaProducerPort.Result.transientFailure("broker timeout");
        ResearchPayloadForbiddenFieldScan scan = new ResearchPayloadForbiddenFieldScan();
        ResearchOutboxRelay.ResearchOutboxAuditHook hook = new NoopAuditHook();
        ResearchOutboxRelay relay = new ResearchOutboxRelay(
                repo, producer, scan, hook,
                10, Duration.ofMillis(1000), Duration.ofMillis(30_000), 1);
        ResearchOutboxRelay.RelayTickResult result = relay.tick(
                "tenant-a", Instant.parse("2026-08-01T00:00:01Z"));
        assertThat(result.deadLettered()).isEqualTo(1);
        List<ResearchOutboxEventRecord> rows = snapshot(repo);
        assertThat(rows).hasSize(1);
        ResearchOutboxEventRecord r = rows.get(0);
        assertThat(r.status()).isEqualTo(ResearchOutboxStatus.DEAD_LETTERED);
        assertThat(r.dlqReason()).isEqualTo(ResearchDlqReason.PRODUCER_RETRY_EXHAUSTED);
    }

    @Test
    @DisplayName("permanent failure short-circuits to DEAD_LETTERED")
    void permanentFailureDeadLetters() {
        ResearchOutboxRelay.InMemoryOutboxRepository repo =
                new ResearchOutboxRelay.InMemoryOutboxRepository(
                        List.of(pendingRow("tenant-a", "gp.research.v1.CitationCreated")));
        ResearchKafkaProducerPort producer = (row, now) ->
                ResearchKafkaProducerPort.Result.permanentFailure("schema-invalid");
        ResearchPayloadForbiddenFieldScan scan = new ResearchPayloadForbiddenFieldScan();
        ResearchOutboxRelay.ResearchOutboxAuditHook hook = new NoopAuditHook();
        ResearchOutboxRelay relay = new ResearchOutboxRelay(
                repo, producer, scan, hook,
                10, Duration.ofMillis(1000), Duration.ofMillis(30_000), 5);
        ResearchOutboxRelay.RelayTickResult result = relay.tick(
                "tenant-a", Instant.parse("2026-08-01T00:00:01Z"));
        assertThat(result.deadLettered()).isEqualTo(1);
        List<ResearchOutboxEventRecord> rows = snapshot(repo);
        assertThat(rows).hasSize(1);
        ResearchOutboxEventRecord r = rows.get(0);
        assertThat(r.status()).isEqualTo(ResearchOutboxStatus.DEAD_LETTERED);
        assertThat(r.dlqReason()).isEqualTo(ResearchDlqReason.PAYLOAD_ENCODE_FAILED);
    }

    private static final class NoopAuditHook implements ResearchOutboxRelay.ResearchOutboxAuditHook {
        @Override
        public void onPublished(ResearchOutboxEventRecord row) {
        }

        @Override
        public void onRetried(ResearchOutboxEventRecord row) {
        }

        @Override
        public void onDeadLettered(ResearchOutboxEventRecord row) {
        }
    }
}
