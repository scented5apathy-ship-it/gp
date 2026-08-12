package com.genealogy.platform.services.research.outbox;

import java.time.Instant;
import java.util.Objects;

/**
 * Adapter port the {@link ResearchOutboxRelay} uses to publish
 * a record to Kafka. The production implementation delegates
 * to a managed Kafka producer (configured via the platform
 * secrets + the {@code spring-kafka} auto-configuration); the
 * unit tests use an in-memory fake that records the calls.
 *
 * <p>Returning a {@link Result} instead of throwing keeps the
 * relay loop ergonomic — the relay decides whether to retry
 * vs. dead-letter without coupling the port to a checked
 * exception type.
 */
public interface ResearchKafkaProducerPort {

    Result publish(ResearchOutboxEventRecord row, Instant now);

    /**
     * Outcome of a publish call. Either the row was published
     * (terminal), the producer failed transiently (retry
     * suggested), or the payload was structurally invalid and
     * must be dead-lettered immediately.
     */
    final class Result {
        public enum Outcome {
            PUBLISHED,
            TRANSIENT_FAILURE,
            PERMANENT_FAILURE
        }

        private final Outcome outcome;
        private final String error;

        public Result(Outcome outcome, String error) {
            this.outcome = Objects.requireNonNull(outcome, "outcome");
            this.error = error;
        }

        public static Result published() {
            return new Result(Outcome.PUBLISHED, null);
        }

        public static Result transientFailure(String error) {
            return new Result(Outcome.TRANSIENT_FAILURE, error);
        }

        public static Result permanentFailure(String error) {
            return new Result(Outcome.PERMANENT_FAILURE, error);
        }

        public Outcome outcome() {
            return outcome;
        }

        public String error() {
            return error;
        }

        public boolean isPublished() {
            return outcome == Outcome.PUBLISHED;
        }

        public boolean isPermanentFailure() {
            return outcome == Outcome.PERMANENT_FAILURE;
        }
    }
}
