package com.genealogy.platform.services.genealogy.outbox;

import java.util.Locale;

/**
 * Closed-set partition-key strategy. Mirrors
 * `contracts/genealogy/outbox-relay-policy.yaml::
 * spec.partitionKeyClasses` (E4.7) + ADR-E0.5-08
 * (partition key = `tenantId + aggregateId` when
 * ordering across aggregates is required; `aggregateId`
 * only otherwise).
 *
 * <ul>
 *   <li>{@link #AGGREGATE_ONLY} — partition by
 *       aggregate id. Used for tree / person CRUD where
 *       cross-aggregate causal ordering is not required.
 *   <li>{@link #TENANT_PLUS_AGGREGATE} — partition by
 *       {@code tenantId + "|" + aggregateId}. Used for
 *       merge events (E4.6) so every consumer sees the
 *       winner + loser transitions in causal order.
 *   <li>{@link #TRACE_ID} — partition by trace id.
 *       Reserved for tracing-only events.
 * </ul>
 */
public enum PartitionKeyClass {
    AGGREGATE_ONLY,
    TENANT_PLUS_AGGREGATE,
    TRACE_ID;

    public static PartitionKeyClass fromWire(String wire) {
        if (wire == null) {
            return AGGREGATE_ONLY;
        }
        return PartitionKeyClass.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
