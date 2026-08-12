package com.genealogy.platform.services.research.outbox;

/**
 * Partition-key class for the research-service outbox.
 * Mirrors the closed-set stored in the
 * {@code partition_key_class} column
 * ({@code V3__outbox_and_workspace.sql}).
 *
 * <p>Per ADR-E0.5-08 / E4.7:
 *   - Research CRUD events use {@link #AGGREGATE_ONLY}.
 *   - Events that need cross-aggregate ordering inside one
 *     tenant (currently none — the merge / redaction flow
 *     uses {@link #AGGREGATE_ONLY}) use
 *     {@link #TENANT_PLUS_AGGREGATE}.
 */
public enum ResearchPartitionKeyClass {
    AGGREGATE_ONLY,
    TENANT_PLUS_AGGREGATE
}
