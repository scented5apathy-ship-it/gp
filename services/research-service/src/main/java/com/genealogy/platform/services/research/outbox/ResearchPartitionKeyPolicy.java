package com.genealogy.platform.services.research.outbox;

import com.genealogy.platform.services.research.events.ResearchEventPayloads;
import java.util.Objects;

/**
 * Partition-key derivation for the research outbox. Mirrors the
 * closed-set pinned in
 * {@code contracts/events/research/v1/README.md} (ADR-E0.5-08).
 *
 * <p>E6.1d is intentionally narrow: every published event uses
 * {@link ResearchPartitionKeyClass#AGGREGATE_ONLY} because the
 * producer-side aggregate id is unique per tenant (the {@code
 * Hibernate-style} UUID v4 stamped by the {@code IdGenerator})
 * and the consumer-side dedupe is keyed by
 * {@code (tenantId, eventId)}.
 *
 * <p>Merge-style events that need cross-aggregate ordering
 * (none today) would land in
 * {@link ResearchPartitionKeyClass#TENANT_PLUS_AGGREGATE}; the
 * helper is in place so the contract stays backward-compatible.
 */
public final class ResearchPartitionKeyPolicy {

    private ResearchPartitionKeyPolicy() {
    }

    public static String derive(String eventType, String tenantId, String aggregateId) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        ResearchPartitionKeyClass keyClass = classify(eventType);
        return switch (keyClass) {
            case AGGREGATE_ONLY -> aggregateId;
            case TENANT_PLUS_AGGREGATE -> tenantId + "|" + aggregateId;
        };
    }

    public static ResearchPartitionKeyClass classify(String eventType) {
        Objects.requireNonNull(eventType, "eventType");
        return switch (eventType) {
            case ResearchEventPayloads.EVENT_CITATION_CREATED,
                 ResearchEventPayloads.EVENT_CLAIM_VERIFIED,
                 ResearchEventPayloads.EVENT_CONFLICT_DETECTED ->
                ResearchPartitionKeyClass.AGGREGATE_ONLY;
            default -> throw new IllegalArgumentException(
                    "unknown eventType for research partition-key classification: " + eventType);
        };
    }

    public static String schemaId(String eventType) {
        Objects.requireNonNull(eventType, "eventType");
        return switch (eventType) {
            case ResearchEventPayloads.EVENT_CITATION_CREATED ->
                ResearchEventPayloads.SCHEMA_CITATION_CREATED;
            case ResearchEventPayloads.EVENT_CLAIM_VERIFIED ->
                ResearchEventPayloads.SCHEMA_CLAIM_VERIFIED;
            case ResearchEventPayloads.EVENT_CONFLICT_DETECTED ->
                ResearchEventPayloads.SCHEMA_CONFLICT_DETECTED;
            default -> throw new IllegalArgumentException(
                    "unknown eventType for research schema id: " + eventType);
        };
    }
}
