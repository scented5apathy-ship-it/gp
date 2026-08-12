package com.genealogy.platform.services.research.outbox;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository abstraction over the outbox table. The
 * production implementation is JdbcTemplate-backed; the
 * unit tests use an in-memory fake. The interface is
 * intentionally narrow so the relay stays framework-free
 * (per AGENTS.md).
 */
public interface ResearchOutboxRepository {

    /**
     * Atomically claims up to {@code limit} pending rows for
     * the given tenant. The implementation MUST use
     * {@code SELECT ... FOR UPDATE SKIP LOCKED} so two relay
     * processes never see the same row.
     */
    List<ResearchOutboxEventRecord> claimPending(String tenantId, int limit, Instant now);

    /** Persists the new state of the row. */
    void save(ResearchOutboxEventRecord row);

    /** Reads a single row by event id (used by the replay path). */
    Optional<ResearchOutboxEventRecord> findById(String eventId);
}
