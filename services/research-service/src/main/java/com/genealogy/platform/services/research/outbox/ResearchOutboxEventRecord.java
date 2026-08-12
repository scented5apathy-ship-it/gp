package com.genealogy.platform.services.research.outbox;

import java.time.Instant;
import java.util.Objects;

/**
 * Outbox row aggregate. Mirrors the
 * {@code research_service.outbox} table from
 * {@code V3__outbox_and_workspace.sql}. The writer
 * ({@link ResearchJdbcOutboxWriter}) inserts the row in the
 * SAME transaction as the aggregate mutation; the relay
 * ({@link ResearchOutboxRelay}) polls it via
 * {@code SELECT ... FOR UPDATE SKIP LOCKED} and never touches
 * the aggregate row directly.
 *
 * <p>Constructor invariants are deliberately loud (fail fast
 * vs. silently publish a malformed event).
 */
public record ResearchOutboxEventRecord(
        String eventId,
        String tenantId,
        String aggregateId,
        String eventType,
        String schemaId,
        String payloadJson,
        int payloadByteSize,
        Instant occurredAt,
        String correlationId,
        String traceId,
        String partitionKey,
        ResearchPartitionKeyClass partitionKeyClass,
        ResearchOutboxStatus status,
        int attempts,
        Instant lastAttemptAt,
        Instant nextAttemptAt,
        Instant publishedAt,
        Instant claimedAt,
        Instant claimLeaseUntil,
        String lastError,
        ResearchDlqReason dlqReason,
        String auditEventId) {

    public ResearchOutboxEventRecord {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(aggregateId, "aggregateId");
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(schemaId, "schemaId");
        Objects.requireNonNull(payloadJson, "payloadJson");
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
        if (aggregateId.isBlank()) {
            throw new IllegalArgumentException("aggregateId must not be blank");
        }
        if (!eventType.matches("^gp\\.[a-z0-9_.-]+\\.v[0-9]+\\.[A-Za-z0-9_]+$")) {
            throw new IllegalArgumentException(
                    "eventType must match gp.<area>.v<n>.<EventClass>: " + eventType);
        }
        if (payloadJson.isBlank()) {
            throw new IllegalArgumentException("payloadJson must not be blank");
        }
        if (payloadByteSize < 1 || payloadByteSize > 921600) {
            throw new IllegalArgumentException(
                    "payloadByteSize must be in [1, 921600], got " + payloadByteSize);
        }
        int actualUtf8 = payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (payloadByteSize != actualUtf8) {
            throw new IllegalArgumentException(
                    "payloadByteSize (" + payloadByteSize + ") must equal payloadJson UTF-8 length ("
                            + actualUtf8 + ")");
        }
        if (attempts < 0) {
            throw new IllegalArgumentException("attempts must be >= 0, got " + attempts);
        }
        if (status == ResearchOutboxStatus.DEAD_LETTERED && dlqReason == null) {
            throw new IllegalArgumentException(
                    "DEAD_LETTERED status requires dlqReason");
        }
        if (dlqReason != null && status != ResearchOutboxStatus.DEAD_LETTERED) {
            throw new IllegalArgumentException(
                    "dlqReason is only valid when status = DEAD_LETTERED");
        }
        if (status == ResearchOutboxStatus.PUBLISHED && publishedAt == null) {
            throw new IllegalArgumentException(
                    "PUBLISHED status requires publishedAt");
        }
    }

    /** Factory for the {@link ResearchOutboxStatus#PENDING} row the writer inserts. */
    public static ResearchOutboxEventRecord pending(
            String eventId,
            String tenantId,
            String aggregateId,
            String eventType,
            String schemaId,
            String payloadJson,
            String partitionKey,
            ResearchPartitionKeyClass partitionKeyClass,
            String correlationId,
            String traceId,
            Instant occurredAt) {
        int byteSize = payloadJson.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return new ResearchOutboxEventRecord(
                eventId,
                tenantId,
                aggregateId,
                eventType,
                schemaId,
                payloadJson,
                byteSize,
                occurredAt,
                correlationId,
                traceId,
                partitionKey,
                partitionKeyClass,
                ResearchOutboxStatus.PENDING,
                0,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null);
    }

    public ResearchOutboxEventRecord withAttempt(Instant at, String error) {
        return new ResearchOutboxEventRecord(
                eventId, tenantId, aggregateId, eventType, schemaId, payloadJson,
                payloadByteSize, occurredAt, correlationId, traceId, partitionKey,
                partitionKeyClass, ResearchOutboxStatus.FAILED, attempts + 1, at,
                nextAttemptAt, publishedAt, claimedAt, claimLeaseUntil, error,
                dlqReason, auditEventId);
    }

    public ResearchOutboxEventRecord withNextAttempt(Instant nextRetryAt) {
        return new ResearchOutboxEventRecord(
                eventId, tenantId, aggregateId, eventType, schemaId, payloadJson,
                payloadByteSize, occurredAt, correlationId, traceId, partitionKey,
                partitionKeyClass, status, attempts, lastAttemptAt, nextRetryAt,
                publishedAt, claimedAt, claimLeaseUntil, lastError, dlqReason, auditEventId);
    }

    public ResearchOutboxEventRecord withPublished(Instant at) {
        return new ResearchOutboxEventRecord(
                eventId, tenantId, aggregateId, eventType, schemaId, payloadJson,
                payloadByteSize, occurredAt, correlationId, traceId, partitionKey,
                partitionKeyClass, ResearchOutboxStatus.PUBLISHED, attempts, lastAttemptAt,
                null, at, claimedAt, null, null, null, auditEventId);
    }

    public ResearchOutboxEventRecord withDeadLettered(
            ResearchDlqReason reason, String error, String newAuditEventId) {
        Objects.requireNonNull(reason, "reason");
        return new ResearchOutboxEventRecord(
                eventId, tenantId, aggregateId, eventType, schemaId, payloadJson,
                payloadByteSize, occurredAt, correlationId, traceId, partitionKey,
                partitionKeyClass, ResearchOutboxStatus.DEAD_LETTERED, attempts,
                lastAttemptAt, null, publishedAt, null, null, error, reason,
                newAuditEventId);
    }

    public ResearchOutboxEventRecord withClaim(Instant claimAt, Instant leaseUntil) {
        return new ResearchOutboxEventRecord(
                eventId, tenantId, aggregateId, eventType, schemaId, payloadJson,
                payloadByteSize, occurredAt, correlationId, traceId, partitionKey,
                partitionKeyClass, ResearchOutboxStatus.PENDING, attempts,
                lastAttemptAt, nextAttemptAt, publishedAt, claimAt, leaseUntil,
                lastError, dlqReason, auditEventId);
    }
}
