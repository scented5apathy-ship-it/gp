package com.genealogy.platform.services.genealogy.outbox;

import java.util.Objects;

/**
 * Result of attempting to publish a single outbox row.
 * Mirrors the relay contract
 * (`contracts/genealogy/outbox-relay-policy.yaml`):
 *
 * <ul>
 *   <li>{@link Outcome#PUBLISHED} — broker ack OK;
 *       caller flips row to {@link OutboxStatus#PUBLISHED}.
 *   <li>{@link Outcome#RETRY} — transient failure;
 *       caller schedules a retry.
 *   <li>{@link Outcome#DEAD_LETTER} — terminal failure;
 *       caller flips row to
 *       {@link OutboxStatus#DEAD_LETTERED} with the
 *       attached {@link DlqReason}.
 * </ul>
 */
public record PublishResult(Outcome outcome, RetryClass retryClass,
        DlqReason dlqReason, String error) {

    public PublishResult {
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(retryClass, "retryClass");
    }

    public static PublishResult published() {
        return new PublishResult(Outcome.PUBLISHED, RetryClass.TRANSIENT, null, null);
    }

    public static PublishResult retry(String error) {
        return new PublishResult(Outcome.RETRY, RetryClass.TRANSIENT, null, error);
    }

    public static PublishResult rateLimited(String error) {
        return new PublishResult(Outcome.RETRY, RetryClass.RATE_LIMITED, null, error);
    }

    public static PublishResult deadLetter(DlqReason reason, String error) {
        Objects.requireNonNull(reason, "reason");
        return new PublishResult(Outcome.DEAD_LETTER, RetryClass.PERMANENT, reason, error);
    }

    public enum Outcome {
        PUBLISHED,
        RETRY,
        DEAD_LETTER
    }
}
