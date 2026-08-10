package com.genealogy.platform.services.genealogy.domain;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Relationship aggregate root. Mirrors `requirements.md`
 * R3 / R4 / R4.4 / R8 / R10 / R18 and `design.md` §5.2
 * (RELATIONSHIP + participant/role model).
 *
 * <p>Invariants enforced by {@link RelationshipInvariants}:
 *
 * <ul>
 *   <li>1..{@code spec.maxParticipantsPerRelationship}
 *       participants (R4.1 cap, default 8).
 *   <li>Self-link is rejected (a participant cannot reference
 *       the same Person twice via the same role).
 *   <li>PARTNER requires a {@link PartnerSubKind}; other
 *       kinds MUST NOT carry one.
 *   <li>CUSTOM requires a non-blank label.
 *   <li>Kind ↔ role mapping: BIOLOGICAL_PARENT etc. MUST
 *       carry at least one PARENT + one CHILD; PARTNER MUST
 *       carry at least two PARTNERs; etc.
 *   <li>{@code chronologicalConflictPolicy = warn-only} means
 *       overlap with another relationship on the same
 *       participant is recorded as a soft warning, never as a
 *       hard deny.
 *   <li>{@code version} is monotonic; every mutation
 *       increments by exactly 1 (CAS-friendly).
 * </ul>
 */
public record Relationship(
        RelationshipId relationshipId,
        String tenantId,
        String treeId,
        RelationshipKind kind,
        PartnerSubKind partnerSubKind,
        String customLabel,
        Certainty certainty,
        ProvenanceStatus provenance,
        List<ParticipantRef> participants,
        TemporalValidity validity,
        Instant createdAt,
        Instant updatedAt,
        String createdBy,
        long version,
        Map<String, String> auditAttributes) {

    /** Mirrors {@code relationship-graph-policy.yaml::spec.maxParticipantsPerRelationship}. */
    public static final int MAX_PARTICIPANTS = 8;

    public Relationship {
        Objects.requireNonNull(relationshipId, "relationshipId");
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(treeId, "treeId");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(provenance, "provenance");
        Objects.requireNonNull(validity, "validity");
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
            throw new IllegalArgumentException(
                    "relationship requires at least one participant");
        }
        if (participants.size() > MAX_PARTICIPANTS) {
            throw new IllegalArgumentException(
                    "relationship participants exceed " + MAX_PARTICIPANTS
                            + ": " + participants.size());
        }
        if (kind.requiresPartnerSubKind() && partnerSubKind == null) {
            throw new IllegalArgumentException(
                    "kind=PARTNER requires partnerSubKind");
        }
        if (!kind.requiresPartnerSubKind() && partnerSubKind != null) {
            throw new IllegalArgumentException(
                    "partnerSubKind is forbidden on kind=" + kind.wire());
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
        // Self-link: a participant cannot reference the same
        // person twice in the same role.
        long distinctPersonRole = participants.stream()
                .map(p -> p.role().wire() + "|" + (p.unknown() ? "?" : p.personId()))
                .distinct()
                .count();
        if (distinctPersonRole != participants.size()) {
            throw new IllegalArgumentException(
                    "duplicate (role, personId|unknown) is forbidden");
        }
    }

    public Relationship withUpdated(
            List<ParticipantRef> nextParticipants,
            Certainty nextCertainty,
            ProvenanceStatus nextProvenance,
            TemporalValidity nextValidity,
            PartnerSubKind nextPartnerSubKind,
            String nextCustomLabel,
            Instant at) {
        return new Relationship(
                relationshipId, tenantId, treeId,
                nextPartnerSubKind != null ? RelationshipKind.PARTNER : kind,
                nextPartnerSubKind == null && partnerSubKind != null
                        ? partnerSubKind
                        : nextPartnerSubKind,
                nextCustomLabel == null ? customLabel : nextCustomLabel,
                nextCertainty == null ? certainty : nextCertainty,
                nextProvenance == null ? provenance : nextProvenance,
                nextParticipants == null ? participants : nextParticipants,
                nextValidity == null ? validity : nextValidity,
                createdAt, at, createdBy, version + 1, auditAttributes);
    }

    public Relationship withEnded(Instant at) {
        Instant endAt = at == null ? updatedAt : at;
        TemporalValidity closed = new TemporalValidity(
                validity.validFrom(), endAt);
        return new Relationship(
                relationshipId, tenantId, treeId,
                kind, partnerSubKind, customLabel,
                certainty, provenance, participants,
                closed, createdAt, endAt, createdBy, version + 1, auditAttributes);
    }

    /** Dotted-field diff between two versions. Returns closed-set field names. */
    public static java.util.LinkedHashSet<String> diff(Relationship before, Relationship after) {
        java.util.LinkedHashSet<String> fields = new java.util.LinkedHashSet<>();
        if (before.kind != after.kind) {
            fields.add("kind");
        }
        if (before.partnerSubKind != after.partnerSubKind) {
            fields.add("partnerSubKind");
        }
        if (!java.util.Objects.equals(before.customLabel, after.customLabel)) {
            fields.add("customLabel");
        }
        if (before.certainty != after.certainty) {
            fields.add("certainty");
        }
        if (before.provenance != after.provenance) {
            fields.add("provenance");
        }
        if (!before.participants.equals(after.participants)) {
            fields.add("participants[]");
        }
        if (!before.validity.equals(after.validity)) {
            fields.add("validity");
        }
        return fields;
    }
}
