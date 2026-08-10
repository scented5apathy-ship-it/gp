package com.genealogy.platform.services.genealogy.outbox;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.genealogy.platform.services.genealogy.domain.MergeId;
import com.genealogy.platform.services.genealogy.domain.MergeProvenance;
import com.genealogy.platform.services.genealogy.domain.MergeRecord;
import com.genealogy.platform.services.genealogy.domain.MergeStatus;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Wire-format payloads for the genealogy-service merge
 * events (E4.6 + E4.7). Mirrors
 * `contracts/events/genealogy/v1/person-merged.avsc`,
 * `contracts/events/genealogy/v1/person-merge-reverted.avsc`
 * and `contracts/events/genealogy/v1/person-merge-rejected.avsc`.
 *
 * <p>The factory produces Avro-encoded byte payloads
 * (JSON intermediate for the outbox row, Avro at publish
 * time per ADR-E0.5-08). NO biography / identifier value
 * / DNA / access token / PII ever appears; the
 * {@link RelayOutboxPublisher} enforces the
 * {@link PayloadForbiddenFieldScan} at publish time as
 * defense in depth.
 *
 * <p>Event type identifiers are pinned to the closed-set
 * vocabulary in {@link KafkaTopicResolver}.
 */
public final class MergeEventPayloads {

    public static final String EVENT_PERSON_MERGED = "gp.genealogy.v1.PersonMerged";
    public static final String EVENT_PERSON_MERGE_REVERTED = "gp.genealogy.v1.PersonMergeReverted";
    public static final String EVENT_PERSON_MERGE_REJECTED = "gp.genealogy.v1.PersonMergeRejected";

    private MergeEventPayloads() {
    }

    public record PersonMergedEvent(
            @JsonProperty("mergeId") String mergeId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("winnerPersonId") String winnerPersonId,
            @JsonProperty("loserPersonId") String loserPersonId,
            @JsonProperty("mergeKind") String mergeKind,
            @JsonProperty("score") ScoreBreakdown score,
            @JsonProperty("provenance") String provenance,
            @JsonProperty("reviewerUserId") String reviewerUserId,
            @JsonProperty("reason") String reason,
            @JsonProperty("snapshotHash") String snapshotHash,
            @JsonProperty("rekeyedReferenceCount") long rekeyedReferenceCount,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("mergedAt") Instant mergedAt) {
    }

    public record ScoreBreakdown(
            @JsonProperty("overall") double overall,
            @JsonProperty("nameEquality") double nameEquality,
            @JsonProperty("dateProximity") double dateProximity,
            @JsonProperty("placeProximity") double placeProximity,
            @JsonProperty("identifierMatch") double identifierMatch) {
    }

    public record PersonMergeRevertedEvent(
            @JsonProperty("mergeId") String mergeId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("winnerPersonId") String winnerPersonId,
            @JsonProperty("loserPersonId") String loserPersonId,
            @JsonProperty("mergeKind") String mergeKind,
            @JsonProperty("snapshotHashVerified") boolean snapshotHashVerified,
            @JsonProperty("reviewerUserId") String reviewerUserId,
            @JsonProperty("reason") String reason,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("revertedAt") Instant revertedAt) {
    }

    public record PersonMergeRejectedEvent(
            @JsonProperty("mergeId") String mergeId,
            @JsonProperty("treeId") String treeId,
            @JsonProperty("winnerPersonId") String winnerPersonId,
            @JsonProperty("loserPersonId") String loserPersonId,
            @JsonProperty("mergeKind") String mergeKind,
            @JsonProperty("score") double score,
            @JsonProperty("provenance") String provenance,
            @JsonProperty("reviewerUserId") String reviewerUserId,
            @JsonProperty("reason") String reason,
            @JsonProperty("actorId") String actorId,
            @JsonProperty("rejectedAt") Instant rejectedAt) {
    }

    /**
     * Builds the {@link OutboxEventRecord} for the
     * {@code PersonMerged} event. The merge service
     * (E4.6 / future MergeService) MUST call this in the
     * SAME transaction as the aggregate mutation.
     */
    public static OutboxEventRecord buildPersonMerged(
            MergeRecord record,
            long rekeyedReferenceCount,
            String actorId,
            String correlationId,
            String traceId,
            Instant mergedAt) {
        Objects.requireNonNull(record, "record");
        requireMergedStatus(record);
        PersonMergedEvent payload = new PersonMergedEvent(
                record.mergeId() == null ? null : record.mergeId().value(),
                record.treeId(),
                record.winnerPersonId(),
                record.loserPersonId(),
                record.kind().wire(),
                scoreBreakdown(record),
                record.provenance().wire(),
                record.reviewerUserId(),
                record.reason(),
                record.snapshotHash(),
                rekeyedReferenceCount,
                actorId,
                mergedAt);
        return envelope(record, payload, EVENT_PERSON_MERGED, correlationId, traceId, mergedAt);
    }

    public static OutboxEventRecord buildPersonMergeReverted(
            MergeRecord record,
            boolean snapshotHashVerified,
            String actorId,
            String correlationId,
            String traceId,
            Instant revertedAt) {
        Objects.requireNonNull(record, "record");
        requireRevertedStatus(record);
        PersonMergeRevertedEvent payload = new PersonMergeRevertedEvent(
                record.mergeId() == null ? null : record.mergeId().value(),
                record.treeId(),
                record.winnerPersonId(),
                record.loserPersonId(),
                record.kind().wire(),
                snapshotHashVerified,
                record.reviewerUserId(),
                record.reason(),
                actorId,
                revertedAt);
        return envelope(record, payload, EVENT_PERSON_MERGE_REVERTED,
                correlationId, traceId, revertedAt);
    }

    public static OutboxEventRecord buildPersonMergeRejected(
            MergeRecord record,
            String actorId,
            String correlationId,
            String traceId,
            Instant rejectedAt) {
        Objects.requireNonNull(record, "record");
        requireRejectedStatus(record);
        PersonMergeRejectedEvent payload = new PersonMergeRejectedEvent(
                record.mergeId() == null ? null : record.mergeId().value(),
                record.treeId(),
                record.winnerPersonId(),
                record.loserPersonId(),
                record.kind().wire(),
                record.score(),
                record.provenance().wire(),
                record.reviewerUserId(),
                record.reason(),
                actorId,
                rejectedAt);
        return envelope(record, payload, EVENT_PERSON_MERGE_REJECTED,
                correlationId, traceId, rejectedAt);
    }

    private static ScoreBreakdown scoreBreakdown(MergeRecord record) {
        if (record.candidates().isEmpty()) {
            return new ScoreBreakdown(record.score(), 0.0, 0.0, 0.0, 0.0);
        }
        var first = record.candidates().get(0);
        return new ScoreBreakdown(
                first.overallScore(),
                first.nameEquality(),
                first.dateProximity(),
                first.placeProximity(),
                first.identifierMatch());
    }

    private static OutboxEventRecord envelope(
            MergeRecord record,
            Object payload,
            String eventType,
            String correlationId,
            String traceId,
            Instant occurredAt) {
        String aggregateId = record.mergeId() == null
                ? UUID.randomUUID().toString()
                : record.mergeId().value();
        String partitionKey = PartitionKeyPolicy.derive(
                eventType, record.tenantId(), aggregateId);
        byte[] payloadBytes = MergeJsonCodec.encode(payload);
        String schemaId = mergeSchemaId(eventType);
        return OutboxEventRecord.pending(
                UUID.randomUUID().toString(),
                record.tenantId(),
                aggregateId,
                eventType,
                schemaId,
                payloadBytes,
                partitionKey,
                PartitionKeyPolicy.classify(eventType),
                correlationId,
                traceId,
                occurredAt);
    }

    private static String mergeSchemaId(String eventType) {
        return "com.genealogy.platform.events.genealogy.v1." + switch (eventType) {
            case EVENT_PERSON_MERGED -> "PersonMerged";
            case EVENT_PERSON_MERGE_REVERTED -> "PersonMergeReverted";
            case EVENT_PERSON_MERGE_REJECTED -> "PersonMergeRejected";
            default -> throw new IllegalArgumentException("unknown event type: " + eventType);
        };
    }

    private static void requireMergedStatus(MergeRecord record) {
        if (record.status() != MergeStatus.MERGED) {
            throw new IllegalStateException(
                    "PersonMerged event requires MERGED status, got " + record.status());
        }
    }

    private static void requireRevertedStatus(MergeRecord record) {
        if (record.status() != MergeStatus.REVERTED) {
            throw new IllegalStateException(
                    "PersonMergeReverted event requires REVERTED status, got " + record.status());
        }
    }

    private static void requireRejectedStatus(MergeRecord record) {
        if (record.status() != MergeStatus.REJECTED) {
            throw new IllegalStateException(
                    "PersonMergeRejected event requires REJECTED status, got " + record.status());
        }
    }

    static MergeId mergeIdForTest(String value) {
        return new MergeId(value);
    }

    @SuppressWarnings("unused")
    private static MergeProvenance provenanceForTest(String value) {
        return MergeProvenance.fromWire(value);
    }
}
