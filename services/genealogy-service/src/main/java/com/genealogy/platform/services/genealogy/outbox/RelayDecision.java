package com.genealogy.platform.services.genealogy.outbox;

import java.time.Instant;
import java.util.Objects;

/**
 * Decision returned by {@link RelayOutboxPublisher}.
 *
 * <p>Captures the next-state {@link OutboxEventRecord}
 * the relay should persist. Three flavours:
 *
 * <ul>
 *   <li>{@link #published} — row transitioned to
 *       {@link OutboxStatus#PUBLISHED}.
 *   <li>{@link #retry} — row transitioned to
 *       {@link OutboxStatus#FAILED} with the next-attempt
 *       timestamp set.
 *   <li>{@link #deadLetter} — row transitioned to
 *       {@link OutboxStatus#DEAD_LETTERED} with a
 *       {@link DlqReason}.
 * </ul>
 */
public record RelayDecision(OutboxEventRecord nextRow, boolean published,
        boolean retry, boolean deadLettered, String error) {

    public RelayDecision {
        Objects.requireNonNull(nextRow, "nextRow");
    }

    public static RelayDecision published(OutboxEventRecord row, Instant at) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(at, "at");
        return new RelayDecision(row.withPublished(at), true, false, false, null);
    }

    public static RelayDecision retry(OutboxEventRecord row, Instant now, String error) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(now, "now");
        OutboxEventRecord updated = row.withAttempt(now, error);
        OutboxRetryPolicy policy = OutboxRetryPolicy.defaults();
        if (policy.isExhausted(updated.attempts())) {
            return RelayDecision.deadLetter(updated,
                    DlqReason.PUBLISH_TIMEOUT,
                    error == null ? "max attempts exceeded" : error);
        }
        Instant nextAttempt = now.plus(policy.backoffFor(updated.attempts()));
        return new RelayDecision(updated.withNextAttempt(nextAttempt),
                false, true, false, error);
    }

    public static RelayDecision deadLetter(OutboxEventRecord row, DlqReason reason, String error) {
        Objects.requireNonNull(row, "row");
        Objects.requireNonNull(reason, "reason");
        return new RelayDecision(
                row.withDeadLettered(reason, error, null),
                false, false, true, error);
    }
}
