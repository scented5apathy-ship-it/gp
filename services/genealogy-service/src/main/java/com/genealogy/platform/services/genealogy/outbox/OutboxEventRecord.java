package com.genealogy.platform.services.genealogy.outbox;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Outbox row aggregate. Mirrors the persistence contract
 * landed in
 * `services/genealogy-service/src/main/resources/db/
 * migration/V8__outbox_relay.sql` and the wire contract in
 * `contracts/events/envelope/v1/event-envelope.avsc`.
 *
 * <p>The record is the unit the relay publishes. The
 * writer (e.g. {@link JdbcTreeOutboxWriter} and the
 * {@link MergeEventPayloads}) inserts it in the SAME
 * transaction as the aggregate mutation; the relay
 * reads it via {@code SELECT ... FOR UPDATE SKIP LOCKED}
 * and never touches the aggregate row directly.
 *
 * <p>Invariants enforced at construction time (fail fast,
 * fail loud):
 *
 * <ul>
 *   <li>{@code eventId}, {@code tenantId}, {@code eventType},
 *       {@code schemaId} are non-blank opaque ids.
 *   <li>{@code payload} is non-null and non-empty
 *       (Avro encoded bytes; the relay rejects empty
 *       payloads at insert time).
 *   <li>{@code occurredAt} is non-null.
 *   <li>{@code status} is one of {@link OutboxStatus}.
 *   <li>{@code attempts >= 0}; payload byte size is
 *       in {@code [0, 921600]} (matches
 *       {@code maxPayloadBytes}).
 *   <li>{@code partitionKeyClass} resolves to a partition
 *       key that is consistent with {@code partitionKey}.
 * </ul>
 *
 * <p>The record is intentionally framework-free (no Spring,
 * no JPA, no Lombok) per AGENTS.md.
 */
public record OutboxEventRecord(
        String eventId,
        String tenantId,
        String aggregateId,
        String eventType,
        String schemaId,
        byte[] payload,
        Instant occurredAt,
        String correlationId,
        String traceId,
        String partitionKey,
        PartitionKeyClass partitionKeyClass,
        OutboxStatus status,
        int attempts,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        Instant publishedAt,
        Instant claimedAt,
        Instant claimLeaseUntil,
        String lastError,
        DlqReason dlqReason,
        String auditEventId,
        int payloadByteSize) {

    public OutboxEventRecord {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(schemaId, "schemaId");
        Objects.requireNonNull(payload, "payload");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(partitionKey, "partitionKey");
        Objects.requireNonNull(partitionKeyClass, "partitionKeyClass");
        Objects.requireNonNull(status, "status");
        if (eventId.isBlank()) {
            throw new IllegalArgumentException("eventId must not be blank");
        }
        if (tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        if (eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (schemaId.isBlank()) {
            throw new IllegalArgumentException("schemaId must not be blank");
        }
        if (payload.length == 0) {
            throw new IllegalArgumentException("payload must not be empty");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must be >= 0, got " + attempts);
        }
        if (payloadByteSize < 0 || payloadByteSize > 921600) {
            throw new IllegalArgumentException(
                    "payloadByteSize must be in [0, 921600], got " + payloadByteSize);
        }
        if (payloadByteSize != payload.length) {
            throw new IllegalArgumentException(
                    "payloadByteSize (" + payloadByteSize + ") must equal payload.length ("
                            + payload.length + ")");
        }
        if (status == OutboxStatus.DEAD_LETTERED && dlqReason == null) {
            throw new IllegalArgumentException(
                    "DEAD_LETTERED status requires dlqReason");
        }
        if (dlqReason != null && status != OutboxStatus.DEAD_LETTERED) {
            throw new IllegalArgumentException(
                    "dlqReason is only valid when status = DEAD_LETTERED");
        }
    }

    /**
     * Factory for the {@link #PENDING} row the writer
     * inserts in the same transaction as the aggregate
     * mutation.
     */
    public static OutboxEventRecord pending(
            String eventId,
            String tenantId,
            String aggregateId,
            String eventType,
            String schemaId,
            byte[] payload,
            String partitionKey,
            PartitionKeyClass partitionKeyClass,
            String correlationId,
            String traceId,
            Instant occurredAt) {
        Objects.requireNonNull(payload, "payload");
        return new OutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                eventType,
                schemaId,
                payload.clone(),
                occurredAt,
                correlationId,
                traceId,
                partitionKey,
                partitionKeyClass,
                OutboxStatus.PENDING,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                payload.length);
    }

    /** {@code with*}-style copy that increments {@link #attempts}. */
    public OutboxEventRecord withAttempt(Instant at, String error) {
        return new OutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                eventType,
                schemaId,
                payload,
                occurredAt,
                correlationId,
                traceId,
                partitionKey,
                partitionKeyClass,
                OutboxStatus.FAILED,
                attempts + 1,
                at,
                nextAttemptAt,
                publishedAt,
                claimedAt,
                claimLeaseUntil,
                error,
                dlqReason,
                auditEventId,
                payloadByteSize);
    }

    /** {@code with*}-style copy that schedules the next retry. */
    public OutboxEventRecord withNextAttempt(Instant nextRetryAt) {
        return new OutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                eventType,
                schemaId,
                payload,
                occurredAt,
                correlationId,
                traceId,
                partitionKey,
                partitionKeyClass,
                status,
                attempts,
                lastAttemptAt,
                nextRetryAt,
                publishedAt,
                claimedAt,
                claimLeaseUntil,
                lastError,
                dlqReason,
                auditEventId,
                payloadByteSize);
    }

    /** {@code with*}-style copy that flips the row to {@link OutboxStatus#PUBLISHED}. */
    public OutboxEventRecord withPublished(Instant at) {
        return new OutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                eventType,
                schemaId,
                payload,
                occurredAt,
                correlationId,
                traceId,
                partitionKey,
                partitionKeyClass,
                OutboxStatus.PUBLISHED,
                attempts,
                lastAttemptAt,
                null,
                at,
                claimedAt,
                null,
                null,
                null,
                auditEventId,
                payloadByteSize);
    }

    /** {@code with*}-style copy that flips the row to {@link OutboxStatus#DEAD_LETTERED}. */
    public OutboxEventRecord withDeadLettered(DlqReason reason, String error, String newAuditEventId) {
        Objects.requireNonNull(reason, "reason");
        return new OutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                eventType,
                schemaId,
                payload,
                occurredAt,
                correlationId,
                traceId,
                partitionKey,
                partitionKeyClass,
                OutboxStatus.DEAD_LETTERED,
                attempts,
                lastAttemptAt,
                null,
                publishedAt,
                null,
                null,
                error,
                reason,
                newAuditEventId,
                payloadByteSize);
    }

    /** {@code with*}-style copy that claims the row (lease + status reset). */
    public OutboxEventRecord withClaim(Instant claimAt, Instant leaseUntil) {
        return new OutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                eventType,
                schemaId,
                payload,
                occurredAt,
                correlationId,
                traceId,
                partitionKey,
                partitionKeyClass,
                OutboxStatus.PENDING,
                attempts,
                lastAttemptAt,
                nextAttemptAt,
                publishedAt,
                claimAt,
                leaseUntil,
                lastError,
                dlqReason,
                auditEventId,
                payloadByteSize);
    }

    public Optional<Instant> publishedAtOpt() {
        return Optional.ofNullable(publishedAt);
    }
}
