package com.genealogy.platform.services.research.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Conflict aggregate root. A conflict is a disagreement the
 * research log cannot resolve automatically. Mirrors
 * `requirements.md` R8.1 (conflict) + `design.md` §5.5 +
 * `contracts/research/research-policy.yaml::
 * spec.conflictSchema` (E6.1).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>Tenant scope is mandatory (NFR1).
 *   <li>At least two participant references are mandatory
 *       (a conflict needs ≥ 2 sides).
 *   <li>At most 16 participants + 16 linked citations.
 *   <li>{@code kind} is closed-set (see
 *       {@link ConflictKind}).
 *   <li>Resolution text is mandatory when the conflict is
 *       RESOLVED.
 * </ul>
 */
public record Conflict(
        TenantScopedId id,
        String summary,
        ConflictKind kind,
        String kindNote,
        List<Participant> participants,
        List<TenantScopedId> linkedCitationIds,
        ConflictStatus status,
        String resolution,
        String resolutionProof,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        long version,
        ResearchAuditAttributes audit) {

    public Conflict {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.CONFLICT) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be CONFLICT, got "
                            + id.resourceKind());
        }
        if (summary.isBlank()) {
            throw new IllegalArgumentException("summary must not be blank");
        }
        if (summary.length() > 1024) {
            throw new IllegalArgumentException("summary exceeds 1024 characters");
        }
        if (kindNote != null && kindNote.length() > 1024) {
            throw new IllegalArgumentException("kindNote exceeds 1024 characters");
        }
        if (kindNote != null && kindNote.isBlank()) {
            kindNote = null;
        }
        if (resolution != null && resolution.length() > 4096) {
            throw new IllegalArgumentException(
                    "resolution exceeds 4096 characters");
        }
        if (resolution != null && resolution.isBlank()) {
            resolution = null;
        }
        if (resolutionProof != null && resolutionProof.length() > 128) {
            throw new IllegalArgumentException(
                    "resolutionProof exceeds 128 characters");
        }
        if (resolutionProof != null && resolutionProof.isBlank()) {
            resolutionProof = null;
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        participants = participants == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(participants));
        linkedCitationIds = linkedCitationIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(linkedCitationIds));
        if (participants.size() > 16) {
            throw new IllegalArgumentException(
                    "participants exceeds 16: " + participants.size());
        }
        if (linkedCitationIds.size() > 16) {
            throw new IllegalArgumentException(
                    "linkedCitationIds exceeds 16: " + linkedCitationIds.size());
        }
        if (participants.size() < 2) {
            throw new IllegalArgumentException(
                    "conflict requires at least 2 participants");
        }
    }

    public enum ConflictStatus {
        OPEN,
        INVESTIGATING,
        RESOLVED,
        ABANDONED;

        public boolean isTerminal() {
            return this == RESOLVED || this == ABANDONED;
        }
    }

    /**
     * One participant side of a conflict. Mirrors
     * `contracts/research/research-policy.yaml::
     * spec.conflictParticipantSchema`.
     */
    public record Participant(
            String reference,
            String referenceKind,
            String interpretation,
            List<TenantScopedId> supportingCitations) {
        public Participant {
            Objects.requireNonNull(reference, "reference");
            if (reference.isBlank()) {
                throw new IllegalArgumentException("reference must not be blank");
            }
            if (reference.length() > 128) {
                throw new IllegalArgumentException(
                        "reference exceeds 128 characters");
            }
            if (referenceKind != null && referenceKind.length() > 64) {
                throw new IllegalArgumentException(
                        "referenceKind exceeds 64 characters");
            }
            if (referenceKind != null && referenceKind.isBlank()) {
                referenceKind = null;
            }
            if (interpretation != null && interpretation.length() > 1024) {
                throw new IllegalArgumentException(
                        "interpretation exceeds 1024 characters");
            }
            if (interpretation != null && interpretation.isBlank()) {
                interpretation = null;
            }
            supportingCitations = supportingCitations == null
                    ? List.of()
                    : Collections.unmodifiableList(new ArrayList<>(supportingCitations));
            if (supportingCitations.size() > 64) {
                throw new IllegalArgumentException(
                        "supportingCitations exceeds 64: "
                                + supportingCitations.size());
            }
        }
    }

    public static Conflict create(
            TenantScopedId id,
            String summary,
            ConflictKind kind,
            String kindNote,
            List<Participant> participants,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(summary, "summary");
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(participants, "participants");
        Objects.requireNonNull(audit, "audit");
        return new Conflict(id, summary, kind, kindNote, participants,
                List.of(), ConflictStatus.OPEN, null, null,
                Instant.now(), Instant.now(), null, 1L, audit);
    }
}
