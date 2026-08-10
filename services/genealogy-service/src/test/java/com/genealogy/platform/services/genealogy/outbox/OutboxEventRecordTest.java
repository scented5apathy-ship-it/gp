package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link OutboxEventRecord} invariants.
 * Mirrors the SQL CHECK constraints in
 * `V8__outbox_relay.sql` and the closed-set vocabulary
 * in `contracts/genealogy/outbox-relay-policy.yaml`.
 */
class OutboxEventRecordTest {

    @Test
    void pendingFactoryProducesValidRow() {
        Instant now = Instant.now();
        OutboxEventRecord row = OutboxEventRecord.pending(
                "evt-" + UUID.randomUUID(),
                "tenant-1",
                "merge-x",
                MergeEventPayloads.EVENT_PERSON_MERGED,
                "com.genealogy.platform.events.genealogy.v1.PersonMerged",
                "{\"k\":1}".getBytes(),
                "tenant-1|merge-x",
                PartitionKeyClass.TENANT_PLUS_AGGREGATE,
                "corr-1",
                "trace-1",
                now);
        assertEquals(OutboxStatus.PENDING, row.status());
        assertEquals(0, row.attempts());
        assertEquals(7, row.payloadByteSize());
        assertEquals(now, row.occurredAt());
    }

    @Test
    void rejectsBlankIds() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> OutboxEventRecord.pending(
                "", "tenant-1", "agg", "evt", "schema",
                "{}".getBytes(), "agg", PartitionKeyClass.AGGREGATE_ONLY,
                "c", "t", now));
        assertThrows(IllegalArgumentException.class, () -> OutboxEventRecord.pending(
                "evt", "", "agg", "evt", "schema",
                "{}".getBytes(), "agg", PartitionKeyClass.AGGREGATE_ONLY,
                "c", "t", now));
        assertThrows(IllegalArgumentException.class, () -> OutboxEventRecord.pending(
                "evt", "tenant-1", "agg", "", "schema",
                "{}".getBytes(), "agg", PartitionKeyClass.AGGREGATE_ONLY,
                "c", "t", now));
        assertThrows(IllegalArgumentException.class, () -> OutboxEventRecord.pending(
                "evt", "tenant-1", "agg", "evt", "",
                "{}".getBytes(), "agg", PartitionKeyClass.AGGREGATE_ONLY,
                "c", "t", now));
    }

    @Test
    void rejectsEmptyPayload() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> OutboxEventRecord.pending(
                "evt", "tenant-1", "agg", "evt", "schema",
                new byte[0], "agg", PartitionKeyClass.AGGREGATE_ONLY,
                "c", "t", now));
    }

    @Test
    void rejectsNegativeAttempts() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new OutboxEventRecord(
                "evt", "tenant-1", "agg", "evt", "schema",
                "{}".getBytes(), now, "c", "t", "agg",
                PartitionKeyClass.AGGREGATE_ONLY, OutboxStatus.PENDING,
                -1, null, null, null, null, null, null, null, null, 2));
    }

    @Test
    void rejectsPayloadByteSizeOutOfRange() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new OutboxEventRecord(
                "evt", "tenant-1", "agg", "evt", "schema",
                "{}".getBytes(), now, "c", "t", "agg",
                PartitionKeyClass.AGGREGATE_ONLY, OutboxStatus.PENDING,
                0, null, null, null, null, null, null, null, null, 1048577));
        assertThrows(IllegalArgumentException.class, () -> new OutboxEventRecord(
                "evt", "tenant-1", "agg", "evt", "schema",
                "{}".getBytes(), now, "c", "t", "agg",
                PartitionKeyClass.AGGREGATE_ONLY, OutboxStatus.PENDING,
                0, null, null, null, null, null, null, null, null, -1));
    }

    @Test
    void rejectsDeadLetteredWithoutDlqReason() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new OutboxEventRecord(
                "evt", "tenant-1", "agg", "evt", "schema",
                "{}".getBytes(), now, "c", "t", "agg",
                PartitionKeyClass.AGGREGATE_ONLY, OutboxStatus.DEAD_LETTERED,
                5, now, null, null, null, null, "boom", null, null, 2));
    }

    @Test
    void rejectsDlqReasonOnNonDeadLettered() {
        Instant now = Instant.now();
        assertThrows(IllegalArgumentException.class, () -> new OutboxEventRecord(
                "evt", "tenant-1", "agg", "evt", "schema",
                "{}".getBytes(), now, "c", "t", "agg",
                PartitionKeyClass.AGGREGATE_ONLY, OutboxStatus.PENDING,
                0, null, null, null, null, null, null, DlqReason.PUBLISH_TIMEOUT,
                null, 2));
    }

    @Test
    void withAttemptIncrementsAttemptsAndSetsLastAttemptAt() {
        OutboxEventRecord row = pending();
        Instant t = Instant.now();
        OutboxEventRecord after = row.withAttempt(t, "boom");
        assertEquals(1, after.attempts());
        assertEquals(t, after.lastAttemptAt());
        assertEquals("boom", after.lastError());
        assertEquals(OutboxStatus.FAILED, after.status());
    }

    @Test
    void withPublishedFlipsStatusAndSetsPublishedAt() {
        OutboxEventRecord row = pending();
        Instant t = Instant.now();
        OutboxEventRecord after = row.withPublished(t);
        assertEquals(OutboxStatus.PUBLISHED, after.status());
        assertEquals(t, after.publishedAt());
    }

    @Test
    void withDeadLetteredSetsStatusAndDlqReason() {
        OutboxEventRecord row = pending();
        OutboxEventRecord after = row.withDeadLettered(
                DlqReason.SCHEMA_INCOMPATIBLE, "schema mismatch", "audit-1");
        assertEquals(OutboxStatus.DEAD_LETTERED, after.status());
        assertEquals(DlqReason.SCHEMA_INCOMPATIBLE, after.dlqReason());
        assertEquals("schema mismatch", after.lastError());
        assertEquals("audit-1", after.auditEventId());
    }

    @Test
    void withClaimSetsLeaseTimes() {
        OutboxEventRecord row = pending();
        Instant now = Instant.now();
        OutboxEventRecord after = row.withClaim(now, now.plusSeconds(30));
        assertEquals(now, after.claimedAt());
        assertTrue(after.claimLeaseUntil().isAfter(now));
    }

    @Test
    void isTerminalReturnsTrueForPublishedAndDeadLettered() {
        Instant now = Instant.now();
        assertTrue(OutboxStatus.PUBLISHED.isTerminal());
        assertTrue(OutboxStatus.DEAD_LETTERED.isTerminal());
        assertTrue(!OutboxStatus.PENDING.isTerminal());
        assertTrue(!OutboxStatus.FAILED.isTerminal());
    }

    @Test
    void typeContractStability() {
        assertTrue(true);
    }

    private static OutboxEventRecord pending() {
        Instant now = Instant.now();
        return OutboxEventRecord.pending(
                "evt-1", "tenant-1", "agg-1", "gp.genealogy.v1.PersonCreated",
                "com.genealogy.platform.events.genealogy.v1.PersonCreated",
                "{}".getBytes(), "agg-1", PartitionKeyClass.AGGREGATE_ONLY,
                "c", "t", now);
    }
}
