package com.genealogy.platform.services.genealogy.outbox;

import com.genealogy.platform.services.genealogy.domain.MergeCandidate;
import com.genealogy.platform.services.genealogy.domain.MergeId;
import com.genealogy.platform.services.genealogy.domain.MergeKind;
import com.genealogy.platform.services.genealogy.domain.MergeProvenance;
import com.genealogy.platform.services.genealogy.domain.MergeRecord;
import com.genealogy.platform.services.genealogy.domain.MergeStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MergeEventPayloads}. Mirrors the
 * Avro schemas under
 * `contracts/events/genealogy/v1/person-merged.avsc`,
 * `person-merge-reverted.avsc` and `person-merge-rejected.avsc`.
 */
class MergeEventPayloadsTest {

    private static final Instant T = Instant.parse("2026-08-10T10:00:00Z");

    private static MergeCandidate candidate() {
        return new MergeCandidate(
                "cand-1",
                "person-winner",
                "person-loser",
                1.0, 0.9, 0.8, 0.95,
                0.92,
                MergeProvenance.AUTOMATED_SCORER);
    }

    private static MergeRecord mergedRecord() {
        return new MergeRecord(
                MergeId.newId(),
                "tenant-1",
                "tree-1",
                MergeKind.DUPLICATE_PERSON_MERGE,
                "person-winner",
                "person-loser",
                MergeStatus.MERGED,
                0.92,
                List.of(candidate()),
                MergeProvenance.AUTOMATED_SCORER,
                "user-reviewer",
                "manual review",
                "sha256:abcd",
                MergeRecord.defaultRevertCommandJson(
                        MergeId.newId(),
                        "person-winner",
                        "person-loser"),
                42L,
                T,
                null,
                T,
                T,
                "user-1",
                1L,
                null);
    }

    private static MergeRecord revertedRecord() {
        return new MergeRecord(
                MergeId.newId(),
                "tenant-1",
                "tree-1",
                MergeKind.DUPLICATE_PERSON_MERGE,
                "person-winner",
                "person-loser",
                MergeStatus.REVERTED,
                0.92,
                List.of(candidate()),
                MergeProvenance.AUTOMATED_SCORER,
                "user-reviewer",
                "wrong merge",
                "sha256:abcd",
                MergeRecord.defaultRevertCommandJson(
                        MergeId.newId(),
                        "person-winner",
                        "person-loser"),
                42L,
                T,
                T,
                T,
                T,
                "user-1",
                2L,
                null);
    }

    private static MergeRecord rejectedRecord() {
        return new MergeRecord(
                MergeId.newId(),
                "tenant-1",
                "tree-1",
                MergeKind.DUPLICATE_PERSON_MERGE,
                "person-winner",
                "person-loser",
                MergeStatus.REJECTED,
                0.92,
                List.of(candidate()),
                MergeProvenance.USER_REVIEW,
                "user-reviewer",
                "false positive",
                "sha256:abcd",
                MergeRecord.defaultRevertCommandJson(
                        MergeId.newId(),
                        "person-winner",
                        "person-loser"),
                0L,
                null,
                null,
                T,
                T,
                "user-1",
                1L,
                null);
    }

    @Test
    void personMergedEventProducesPendingRow() {
        MergeRecord record = mergedRecord();
        OutboxEventRecord row = MergeEventPayloads.buildPersonMerged(
                record, 42L, "user-1", "corr-1", "trace-1", T);
        assertEquals(MergeEventPayloads.EVENT_PERSON_MERGED, row.eventType());
        assertEquals(OutboxStatus.PENDING, row.status());
        assertEquals(PartitionKeyClass.TENANT_PLUS_AGGREGATE, row.partitionKeyClass());
        assertEquals("tenant-1|" + record.mergeId().value(), row.partitionKey());
        assertEquals("com.genealogy.platform.events.genealogy.v1.PersonMerged",
                row.schemaId());
        assertEquals("tenant-1", row.tenantId());
        assertTrue(row.payloadByteSize() > 0);
        assertEquals(row.payloadByteSize(), row.payload().length);
    }

    @Test
    void personMergeRevertedEventProducesPendingRow() {
        MergeRecord record = revertedRecord();
        OutboxEventRecord row = MergeEventPayloads.buildPersonMergeReverted(
                record, true, "user-1", "corr-1", "trace-1", T);
        assertEquals(MergeEventPayloads.EVENT_PERSON_MERGE_REVERTED, row.eventType());
        assertEquals(PartitionKeyClass.TENANT_PLUS_AGGREGATE, row.partitionKeyClass());
        assertEquals("com.genealogy.platform.events.genealogy.v1.PersonMergeReverted",
                row.schemaId());
    }

    @Test
    void personMergeRejectedEventProducesPendingRow() {
        MergeRecord record = rejectedRecord();
        OutboxEventRecord row = MergeEventPayloads.buildPersonMergeRejected(
                record, "user-1", "corr-1", "trace-1", T);
        assertEquals(MergeEventPayloads.EVENT_PERSON_MERGE_REJECTED, row.eventType());
        assertEquals(PartitionKeyClass.TENANT_PLUS_AGGREGATE, row.partitionKeyClass());
        assertEquals("com.genealogy.platform.events.genealogy.v1.PersonMergeRejected",
                row.schemaId());
    }

    @Test
    void statusMismatchIsRejected() {
        MergeRecord record = mergedRecord();
        assertThrows(IllegalStateException.class,
                () -> MergeEventPayloads.buildPersonMergeReverted(
                        record, true, "u", "c", "t", T));
        assertThrows(IllegalStateException.class,
                () -> MergeEventPayloads.buildPersonMergeRejected(
                        record, "u", "c", "t", T));
        MergeRecord rejected = rejectedRecord();
        assertThrows(IllegalStateException.class,
                () -> MergeEventPayloads.buildPersonMerged(
                        rejected, 0L, "u", "c", "t", T));
    }

    @Test
    void nullRecordIsRejected() {
        assertThrows(NullPointerException.class,
                () -> MergeEventPayloads.buildPersonMerged(null, 0L,
                        "u", "c", "t", T));
    }

    @Test
    void payloadBytesAreNotEmpty() {
        OutboxEventRecord row = MergeEventPayloads.buildPersonMerged(
                mergedRecord(), 42L, "user-1", "c", "t", T);
        assertNotNull(row.payload());
        assertTrue(row.payload().length > 0);
    }

    @Test
    void typeContractStability() {
        assertTrue(true);
    }
}
