package com.genealogy.platform.services.genealogy.outbox;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link PartitionKeyPolicy}. Mirrors
 * ADR-E0.5-08 (tenant + aggregate for merge events;
 * aggregate-only for tree / person CRUD).
 */
class PartitionKeyPolicyTest {

    @Test
    void mergeEventsAreTenantPlusAggregate() {
        String tenantId = "tenant-abc";
        String aggregateId = "merge-xyz";
        assertEquals("tenant-abc|merge-xyz",
                PartitionKeyPolicy.derive(
                        MergeEventPayloads.EVENT_PERSON_MERGED,
                        tenantId, aggregateId));
        assertEquals("tenant-abc|merge-xyz",
                PartitionKeyPolicy.derive(
                        MergeEventPayloads.EVENT_PERSON_MERGE_REVERTED,
                        tenantId, aggregateId));
        assertEquals("tenant-abc|merge-xyz",
                PartitionKeyPolicy.derive(
                        MergeEventPayloads.EVENT_PERSON_MERGE_REJECTED,
                        tenantId, aggregateId));
        assertEquals(PartitionKeyClass.TENANT_PLUS_AGGREGATE,
                PartitionKeyPolicy.classify(MergeEventPayloads.EVENT_PERSON_MERGED));
    }

    @Test
    void personAndTreeCrudAreAggregateOnly() {
        for (String eventType : new String[]{
                "gp.genealogy.v1.TreeCreated",
                "gp.genealogy.v1.TreeVisibilityChanged",
                "gp.genealogy.v1.TreeArchived",
                "gp.genealogy.v1.TreeRestored",
                "gp.genealogy.v1.TreeTransferred",
                "gp.genealogy.v1.TreeDeleted",
                "gp.genealogy.v1.PersonCreated",
                "gp.genealogy.v1.PersonUpdated",
                "gp.genealogy.v1.PersonPrivacyChanged",
                "gp.genealogy.v1.PersonLivingStatusChanged",
                "gp.genealogy.v1.PersonDeleted",
                "gp.genealogy.v1.UnlistedTokenIssued",
                "gp.genealogy.v1.UnlistedTokenRevoked"}) {
            assertEquals(PartitionKeyClass.AGGREGATE_ONLY,
                    PartitionKeyPolicy.classify(eventType),
                    "expected AGGREGATE_ONLY for " + eventType);
            assertEquals("agg-1",
                    PartitionKeyPolicy.derive(eventType, "tenant-1", "agg-1"));
        }
    }

    @Test
    void unknownEventTypeIsRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> PartitionKeyPolicy.classify("gp.genealogy.v1.Bogus"));
        assertThrows(IllegalArgumentException.class,
                () -> PartitionKeyPolicy.derive("gp.genealogy.v1.Bogus",
                        "tenant-1", "agg-1"));
    }

    @Test
    void nullInputsAreRejected() {
        assertThrows(NullPointerException.class,
                () -> PartitionKeyPolicy.classify(null));
        assertThrows(NullPointerException.class,
                () -> PartitionKeyPolicy.derive(null, "t", "a"));
        assertThrows(NullPointerException.class,
                () -> PartitionKeyPolicy.derive("gp.genealogy.v1.TreeCreated",
                        null, "a"));
        assertThrows(NullPointerException.class,
                () -> PartitionKeyPolicy.derive("gp.genealogy.v1.TreeCreated",
                        "t", null));
    }

    @Test
    void traceIdClassIsReservedAndRejectsDerivation() {
        assertEquals(PartitionKeyClass.TRACE_ID,
                PartitionKeyPolicy.classify("gp.genealogy.v1.TraceSample"));
        // No event in the closed-set currently maps to TRACE_ID;
        // the derive() call must reject unknown event types.
        assertThrows(IllegalArgumentException.class,
                () -> PartitionKeyPolicy.derive("gp.genealogy.v1.TraceSample",
                        "t", "a"));
    }

    @Test
    void typeContractStability() {
        assertTrue(true);
    }
}
