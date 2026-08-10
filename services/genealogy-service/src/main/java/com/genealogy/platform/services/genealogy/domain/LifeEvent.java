package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Life-event aggregate root. Mirrors
 * `requirements.md` R4.1 (event links to many persons with
 * roles, date, place, source, media, privacy) + R5 +
 * `design.md` §5.3 + §5.5 + §6.3.
 *
 * <p>An event is decoupled from any single {@link Person}:
 * a wedding has at least two SUBJECT/PARTNER participants
 * plus optional WITNESS / OFFICIANT; a death record has one
 * SUBJECT + an INFORMANT. The participant list is the
 * ground truth — no denormalised subject_person_id column.
 *
 * <p>Invariants enforced by {@link LifeEventInvariants}:
 *
 * <ul>
 *   <li>1..{@code spec.maxParticipantsPerEvent} participants
 *       (default 16 — wider than Relationship because some
 *       events gather large witness groups).
 *   <li>{@link LifeEventKind#RECURRING_MEMORIAL} MUST carry
 *       a non-null {@code date} so the renderer can schedule
 *       a recurring notice.
 *   <li>{@link LifeEventKind#CUSTOM} MUST carry a non-blank
 *       {@code customLabel}.
 *   <li>{@code certainties} / {@code provenance} come from
 *       the same closed-set as Relationship (E4.4).
 *   <li>{@code privacyClassification} is a separate axis; the
 *       ABAC redaction layer (E3.4) may downgrade visibility
 *       further when a living person participates.
 *   <li>{@code version} is monotonic; every mutation
 *       increments by exactly 1 (CAS-friendly).
 * </ul>
 */
public record LifeEvent(
        LifeEventId eventId,
        String tenantId,
        String treeId,
        LifeEventKind kind,
        String customLabel,
        Certainty certainty,
        ProvenanceStatus provenance,
        EventPrivacy privacyClassification,
        List<EventParticipant> participants,
        DateValue date,
        Place place,
        String description,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version,
        Map<String, String> auditAttributes) {

    /** Mirrors {@code event-claim-policy.yaml::spec.maxParticipantsPerEvent}. */
    public static final int MAX_PARTICIPANTS = 16;

    public LifeEvent {
        Objects.requireNonNull(eventId, "eventId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(privacyClassification, "privacyClassification");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(createdBy, "createdBy");
        participants = participants == null
                ? List.of()
                : Collections.unmodifiableList(participants);
        auditAttributes = auditAttributes == null
                ? Map.of()
                : Collections.unmodifiableMap(new LinkedHashMap<>(auditAttributes));
        if (participants.isEmpty()) {
            throw new IllegalArgumentException("life-event requires at least one participant");
        }
        if (participants.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "life-event participants exceed "
                            + MAX_PARTICIPANTS + ": " + participants.size());
        }
        if (kind.requiresCustomLabel()
                && (customLabel == null || customLabel.isBlank())) {
            throw new IllegalArgumentException(
                    "kind=CUSTOM requires a non-blank customLabel");
        }
        if (!kind.requiresCustomLabel() && customLabel != null
                && !customLabel.isBlank()) {
            throw new IllegalArgumentException(
                    "customLabel is forbidden on kind=" + kind.wire());
        }
        if (customLabel != null && customLabel.length() > 256) {
            throw new IllegalArgumentException(
                    "customLabel exceeds 256 chars: " + customLabel.length());
        }
        if (description != null && description.length() > 2048) {
            throw new IllegalArgumentException(
                    "description exceeds 2048 chars: " + description.length());
        }
        if (kind.requiresAnniversaryDate() && date == null) {
            throw new IllegalArgumentException(
                    "kind=RECURRING_MEMORIAL requires a non-null date");
        }
        if (provenance == ProvenanceStatus.IMPORTED
                && certainty == Certainty.VERIFIED) {
            throw new IllegalArgumentException(
                    "provenance=IMPORTED cannot combine with certainty=VERIFIED");
        }
        long distinctPersonRole = participants.stream()
                .map(p -> p.role().wire() + "|" + (p.unknown() ? "?" : p.personId()))
                .distinct()
                .count();
        if (distinctPersonRole != participants.size()) {
            throw new IllegalArgumentException(
                    "duplicate (role, personId|unknown) is forbidden");
        }
    }

    public LifeEvent withUpdated(
            List<EventParticipant> nextParticipants,
            Certainty nextCertainty,
            ProvenanceStatus nextProvenance,
            EventPrivacy nextPrivacy,
            DateValue nextDate,
            Place nextPlace,
            String nextDescription,
            String nextCustomLabel,
            Instant at) {
        return new LifeEvent(
                eventId, tenantId, treeId,
                kind,
                nextCustomLabel == null ? customLabel : nextCustomLabel,
                nextCertainty == null ? certainty : nextCertainty,
                nextProvenance == null ? provenance : nextProvenance,
                nextPrivacy == null ? privacyClassification : nextPrivacy,
                nextParticipants == null ? participants : nextParticipants,
                nextDate == null ? date : nextDate,
                nextPlace == null ? place : nextPlace,
                nextDescription == null ? description : nextDescription,
                createdAt, at, createdBy, version + 1, auditAttributes);
    }
}
