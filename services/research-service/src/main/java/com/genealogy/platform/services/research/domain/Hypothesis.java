package com.genealogy.platform.services.research.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Hypothesis aggregate root. A hypothesis is "we believe X
 * because Y, but we have not verified it yet". Mirrors
 * `requirements.md` R8.1 (hypothesis + status proof) + R4.4
 * (uncertain claim co-exists with other hypotheses) +
 * `design.md` §5.5 + `contracts/research/research-policy.
 * yaml::spec.hypothesisSchema` (E6.1).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>Tenant scope is mandatory (NFR1).
 *   <li>{@code statement} is non-blank and ≤ 1024 characters.
 *   <li>Subject reference is mandatory (a Claim id, a
 *       Person id, a Source id, or a free-form note).
 *   <li>At most 64 corroborating citations + 64 refuting
 *       citations.
 *   <li>Status transitions are enforced by
 *       {@link ResearchTaskStateMachine}.
 * </ul>
 */
public record Hypothesis(
        TenantScopedId id,
        String statement,
        String subjectReference,
        String subjectKind,
        Certainty certainty,
        Double confidence,
        HypothesisStatus status,
        List<TenantScopedId> corroboratingCitations,
        List<TenantScopedId> refutingCitations,
        String supersededByHypothesisId,
        String assignedTo,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        long version,
        ResearchAuditAttributes audit) {

    public Hypothesis {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(subjectReference, "subjectReference");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.HYPOTHESIS) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be HYPOTHESIS, got "
                            + id.resourceKind());
        }
        if (statement.isBlank()) {
            throw new IllegalArgumentException("statement must not be blank");
        }
        if (statement.length() > 1024) {
            throw new IllegalArgumentException("statement exceeds 1024 characters");
        }
        if (subjectReference.isBlank()) {
            throw new IllegalArgumentException("subjectReference must not be blank");
        }
        if (subjectReference.length() > 128) {
            throw new IllegalArgumentException(
                    "subjectReference exceeds 128 characters");
        }
        if (subjectKind != null && subjectKind.length() > 64) {
            throw new IllegalArgumentException(
                    "subjectKind exceeds 64 characters");
        }
        if (subjectKind != null && subjectKind.isBlank()) {
            subjectKind = null;
        }
        confidence = Confidence.requireInRange(confidence);
        if (supersededByHypothesisId != null && supersededByHypothesisId.length() > 128) {
            throw new IllegalArgumentException(
                    "supersededByHypothesisId exceeds 128 characters");
        }
        if (supersededByHypothesisId != null && supersededByHypothesisId.isBlank()) {
            supersededByHypothesisId = null;
        }
        if (assignedTo != null && assignedTo.length() > 128) {
            throw new IllegalArgumentException("assignedTo exceeds 128 characters");
        }
        if (assignedTo != null && assignedTo.isBlank()) {
            assignedTo = null;
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        corroboratingCitations = corroboratingCitations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(corroboratingCitations));
        refutingCitations = refutingCitations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(refutingCitations));
        if (corroboratingCitations.size() > 64) {
            throw new IllegalArgumentException(
                    "corroboratingCitations exceeds 64: "
                            + corroboratingCitations.size());
        }
        if (refutingCitations.size() > 64) {
            throw new IllegalArgumentException(
                    "refutingCitations exceeds 64: " + refutingCitations.size());
        }
    }

    public static Hypothesis create(
            TenantScopedId id,
            String statement,
            String subjectReference,
            String subjectKind,
            Certainty certainty,
            Double confidence,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(statement, "statement");
        Objects.requireNonNull(subjectReference, "subjectReference");
        Objects.requireNonNull(certainty, "certainty");
        Objects.requireNonNull(audit, "audit");
        return new Hypothesis(id, statement, subjectReference, subjectKind, certainty,
                confidence, HypothesisStatus.DRAFT, List.of(), List.of(), null, null,
                Instant.now(), Instant.now(), null, 1L, audit);
    }

    public Hypothesis withStatus(HypothesisStatus next) {
        Objects.requireNonNull(next, "next");
        Instant now = Instant.now();
        Instant nextResolvedAt = next.isTerminal() ? now : null;
        return new Hypothesis(id, statement, subjectReference, subjectKind, certainty,
                confidence, next, corroboratingCitations, refutingCitations,
                supersededByHypothesisId, assignedTo,
                createdAt, now, nextResolvedAt, version + 1, audit);
    }

    public Hypothesis withCorroboratingCitation(TenantScopedId citationId) {
        Objects.requireNonNull(citationId, "citationId");
        if (corroboratingCitations.contains(citationId)) {
            return this;
        }
        java.util.List<TenantScopedId> next = new java.util.ArrayList<>(corroboratingCitations);
        next.add(citationId);
        return new Hypothesis(id, statement, subjectReference, subjectKind, certainty,
                confidence, status, next, refutingCitations,
                supersededByHypothesisId, assignedTo,
                createdAt, Instant.now(), resolvedAt, version + 1, audit);
    }

    public Hypothesis withRefutingCitation(TenantScopedId citationId) {
        Objects.requireNonNull(citationId, "citationId");
        if (refutingCitations.contains(citationId)) {
            return this;
        }
        java.util.List<TenantScopedId> next = new java.util.ArrayList<>(refutingCitations);
        next.add(citationId);
        return new Hypothesis(id, statement, subjectReference, subjectKind, certainty,
                confidence, status, corroboratingCitations, next,
                supersededByHypothesisId, assignedTo,
                createdAt, Instant.now(), resolvedAt, version + 1, audit);
    }

    public Hypothesis withSupersededBy(String newHypothesisId) {
        Objects.requireNonNull(newHypothesisId, "newHypothesisId");
        if (newHypothesisId.isBlank()) {
            throw new IllegalArgumentException(
                    "newHypothesisId must not be blank");
        }
        return new Hypothesis(id, statement, subjectReference, subjectKind, certainty,
                confidence, status, corroboratingCitations, refutingCitations,
                newHypothesisId, assignedTo,
                createdAt, Instant.now(), resolvedAt, version + 1, audit);
    }
}
