package com.genealogy.platform.services.research.workspace;

import java.time.Instant;
import java.util.Objects;

/**
 * Consumer-side idempotency row. One row per
 * {@code (tenantId, sourceTopic, eventId)} claim attempt.
 *
 * <p>The {@link ResearchConsumerInboxService} writes the row
 * in the same transaction as the projection mutation, so the
 * re-delivery of the same Kafka record never forks the
 * projection. The row's lifecycle is:
 *
 * <ol>
 *   <li>{@code INSERT ... ON CONFLICT DO NOTHING} — the first
 *       delivery wins the row in {@link Outcome#IN_FLIGHT};
 *       any concurrent delivery finds the row and skips.</li>
 *   <li>The projection mutation commits, then the row is
 *       flipped to {@link Outcome#PROCESSED} (or {@link
 *       Outcome#FAILED} when the mutation throws).</li>
 * </ol>
 *
 * <p>Mirrors {@code research_service.consumer_inbox} from
 * {@code V4__consumer_inbox.sql}.
 */
public record ResearchConsumerInboxRow(
        String tenantId,
        String sourceTopic,
        String eventId,
        String eventType,
        String payloadHash,
        Instant receivedAt,
        Instant processedAt,
        Outcome outcome,
        String lastError,
        String actorPseudoId,
        String correlationId) {

    public ResearchConsumerInboxRow {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(sourceTopic, "sourceTopic");
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(payloadHash, "payloadHash");
        Objects.requireNonNull(receivedAt, "receivedAt");
        Objects.requireNonNull(outcome, "outcome");
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (sourceTopic.isBlank()) {
            throw new IllegalArgumentException("sourceTopic must not be blank");
        }
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (!payloadHash.matches("^[A-Fa-f0-9]{64}$")) {
            throw new IllegalArgumentException(
                    "payloadHash must be a 64-char hex SHA-256 digest, got " + payloadHash);
        }
    }

    public enum Outcome {
        IN_FLIGHT,
        PROCESSED,
        FAILED,
        SKIPPED_DUPLICATE
    }

    public ResearchConsumerInboxRow withOutcome(Outcome next, Instant processedAt, String error) {
        return new ResearchConsumerInboxRow(
                tenantId, sourceTopic, eventId, eventType, payloadHash,
                receivedAt, processedAt, next, error,
                actorPseudoId, correlationId);
    }
}
