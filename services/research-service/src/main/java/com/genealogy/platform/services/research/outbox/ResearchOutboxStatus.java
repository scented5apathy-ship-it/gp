package com.genealogy.platform.services.research.outbox;

/**
 * Outbox row status. Closed-set per {@code V3__outbox_and_workspace.sql}.
 *
 * <p>By contract the relay is the only writer of the
 * non-{@link #PENDING} states; the application never flips a
 * row to {@link #FAILED} / {@link #PUBLISHED} /
 * {@link #DEAD_LETTERED} directly.
 */
public enum ResearchOutboxStatus {
    PENDING,
    FAILED,
    PUBLISHED,
    DEAD_LETTERED
}
