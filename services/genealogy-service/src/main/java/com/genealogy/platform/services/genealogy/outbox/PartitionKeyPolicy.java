package com.genealogy.platform.services.genealogy.outbox;

import java.util.Objects;

/**
 * Partition-key derivation policy. Mirrors
 * `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.partitionKeyClasses` (E4.7) + ADR-E0.5-08
 * (partition key = `tenantId + "|" + aggregateId` when
 * ordering across aggregates is required; `aggregateId`
 * only otherwise).
 *
 * <p>Per-event-class mapping is hard-pinned by the
 * contract; the policy rejects unknown combinations so
 * the relay never silently publishes to the wrong key.
 */
public final class PartitionKeyPolicy {

    private PartitionKeyPolicy() {
    }

    /**
     * Returns the partition key for the given event type.
     * Merge events MUST be {@link PartitionKeyClass#TENANT_PLUS_AGGREGATE};
     * tree / person CRUD events MUST be {@link PartitionKeyClass#AGGREGATE_ONLY}.
     *
     * @throws IllegalArgumentException if the event type is not in the closed-set.
     */
    public static String derive(String eventType, String tenantId, String aggregateId) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        PartitionKeyClass keyClass = classify(eventType);
        return switch (keyClass) {
            case AGGREGATE_ONLY -> aggregateId;
            case TENANT_PLUS_AGGREGATE -> tenantId + "|" + aggregateId;
            case TRACE_ID -> throw new IllegalArgumentException(
                    "TRACE_ID partition class is reserved for tracing-only events");
        };
    }

    /** Closed-set mapping per the contract. */
    public static PartitionKeyClass classify(String eventType) {
        Objects.requireNonNull(eventType, "eventType");
        return switch (eventType) {
            case "gp.genealogy.v1.PersonMerged",
                 "gp.genealogy.v1.PersonMergeReverted",
                 "gp.genealogy.v1.PersonMergeRejected" ->
                PartitionKeyClass.TENANT_PLUS_AGGREGATE;
            case "gp.genealogy.v1.TraceSample" ->
                PartitionKeyClass.TRACE_ID;
            case "gp.genealogy.v1.TreeCreated",
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
                 "gp.genealogy.v1.UnlistedTokenRevoked" ->
                PartitionKeyClass.AGGREGATE_ONLY;
            default -> throw new IllegalArgumentException(
                    "unknown eventType for partition-key classification: " + eventType);
        };
    }
}
