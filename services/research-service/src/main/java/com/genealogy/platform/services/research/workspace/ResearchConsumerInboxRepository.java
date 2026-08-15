package com.genealogy.platform.services.research.workspace;

import java.util.Optional;

/**
 * Repository abstraction over the {@code research_service.consumer_inbox}
 * table. The production implementation is JdbcTemplate-backed.
 *
 * <p>The {@link #tryClaim(ResearchConsumerInboxRow)} method runs
 * {@code INSERT ... ON CONFLICT DO NOTHING} so the first
 * delivery wins the row; every subsequent delivery observes
 * the row and skips via
 * {@link ResearchConsumerInboxRow.Outcome#SKIPPED_DUPLICATE}.
 */
public interface ResearchConsumerInboxRepository {

    /**
     * Insert a fresh {@link ResearchConsumerInboxRow} in the
     * {@link ResearchConsumerInboxRow.Outcome#IN_FLIGHT} state.
     *
     * @return {@code true} when the row was inserted; {@code false}
     *         when the {@code (tenantId, sourceTopic, eventId)}
     *         triple already existed (the caller should fall back to
     *         {@link #find} and skip).
     */
    boolean tryClaim(ResearchConsumerInboxRow row);

    /** Look up an existing row by its natural key. */
    Optional<ResearchConsumerInboxRow> find(
            String tenantId, String sourceTopic, String eventId);

    /**
     * Persist the final outcome of the claim. The caller MUST
     * hold the same transaction the {@link #tryClaim} call ran
     * in so the {@code WHERE (tenant_id, source_topic, event_id)
     * = ?} predicate is safe.
     */
    void finalizeOutcome(ResearchConsumerInboxRow row);
}
