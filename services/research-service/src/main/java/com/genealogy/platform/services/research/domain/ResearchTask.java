package com.genealogy.platform.services.research.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Research task aggregate root. A research task is a unit of
 * work in the research log: "verify date of birth",
 * "interview witness", "request archive scan". Mirrors
 * `requirements.md` R8.1 (research log task + assignment)
 * + `design.md` §5.5 + `contracts/research/research-policy.
 * yaml::spec.researchTaskSchema` (E6.1).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>Tenant scope is mandatory (NFR1).
 *   <li>{@code title} is non-blank and ≤ 256 characters.
 *   <li>Subject reference is mandatory (a Claim id, a
 *       Person id, or a free-form note).
 *   <li>At most 16 assignments + 16 linked citations.
 *   <li>Status transitions are enforced by
 *       {@link ResearchTaskStateMachine}.
 * </ul>
 */
public record ResearchTask(
        TenantScopedId id,
        String title,
        String description,
        String subjectReference,
        String subjectKind,
        ResearchTaskStatus status,
        List<Assignment> assignments,
        List<TenantScopedId> linkedCitationIds,
        String blockedReason,
        String resolvedProof,
        Instant createdAt,
        Instant updatedAt,
        Instant resolvedAt,
        long version,
        ResearchAuditAttributes audit) {

    public ResearchTask {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subjectReference, "subjectReference");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(updatedAt, "updatedAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.RESEARCH_TASK) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be RESEARCH_TASK, got "
                            + id.resourceKind());
        }
        if (title.isBlank()) {
            throw new IllegalArgumentException("title must not be blank");
        }
        if (title.length() > 256) {
            throw new IllegalArgumentException("title exceeds 256 characters");
        }
        if (description != null && description.length() > 4096) {
            throw new IllegalArgumentException(
                    "description exceeds 4096 characters");
        }
        if (description != null && description.isBlank()) {
            description = null;
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
        if (blockedReason != null && blockedReason.length() > 1024) {
            throw new IllegalArgumentException(
                    "blockedReason exceeds 1024 characters");
        }
        if (blockedReason != null && blockedReason.isBlank()) {
            blockedReason = null;
        }
        if (resolvedProof != null && resolvedProof.length() > 128) {
            throw new IllegalArgumentException(
                    "resolvedProof exceeds 128 characters");
        }
        if (resolvedProof != null && resolvedProof.isBlank()) {
            resolvedProof = null;
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
        assignments = assignments == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(assignments));
        linkedCitationIds = linkedCitationIds == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(linkedCitationIds));
        if (assignments.size() > 16) {
            throw new IllegalArgumentException(
                    "assignments exceeds 16: " + assignments.size());
        }
        if (linkedCitationIds.size() > 16) {
            throw new IllegalArgumentException(
                    "linkedCitationIds exceeds 16: " + linkedCitationIds.size());
        }
    }

    /**
     * One assignment of a research task to a platform user.
     * Mirrors `contracts/research/research-policy.yaml::
     * spec.assignmentSchema`.
     */
    public record Assignment(
            String assigneePseudoId,
            String assigneeRole,
            Instant assignedAt,
            Instant releasedAt,
            String note) {
        public Assignment {
            Objects.requireNonNull(assigneePseudoId, "assigneePseudoId");
            Objects.requireNonNull(assignedAt, "assignedAt");
            if (assigneePseudoId.isBlank()) {
                throw new IllegalArgumentException(
                        "assigneePseudoId must not be blank");
            }
            if (assigneePseudoId.length() > 128) {
                throw new IllegalArgumentException(
                        "assigneePseudoId exceeds 128 characters");
            }
            if (assigneeRole != null && assigneeRole.length() > 64) {
                throw new IllegalArgumentException(
                        "assigneeRole exceeds 64 characters");
            }
            if (assigneeRole != null && assigneeRole.isBlank()) {
                assigneeRole = null;
            }
            if (note != null && note.length() > 1024) {
                throw new IllegalArgumentException(
                        "note exceeds 1024 characters");
            }
            if (note != null && note.isBlank()) {
                note = null;
            }
        }
    }

    public static ResearchTask create(
            TenantScopedId id,
            String title,
            String description,
            String subjectReference,
            String subjectKind,
            ResearchAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(subjectReference, "subjectReference");
        Objects.requireNonNull(audit, "audit");
        return new ResearchTask(id, title, description, subjectReference, subjectKind,
                ResearchTaskStatus.OPEN, List.of(), List.of(), null, null,
                Instant.now(), Instant.now(), null, 1L, audit);
    }

    public ResearchTask withStatus(
            ResearchTaskStatus next,
            String nextBlockedReason,
            String nextResolvedProof) {
        Objects.requireNonNull(next, "next");
        Instant now = Instant.now();
        Instant nextResolvedAt = next.isTerminal() ? now : null;
        String appliedBlockedReason = next == ResearchTaskStatus.BLOCKED ? nextBlockedReason : null;
        String appliedResolvedProof = next == ResearchTaskStatus.RESOLVED ? nextResolvedProof : null;
        return new ResearchTask(id, title, description, subjectReference, subjectKind,
                next, assignments, linkedCitationIds, appliedBlockedReason, appliedResolvedProof,
                createdAt, now, nextResolvedAt, version + 1, audit);
    }

    public ResearchTask withAssignment(Assignment assignment) {
        Objects.requireNonNull(assignment, "assignment");
        java.util.List<Assignment> next = new ArrayList<>(assignments);
        next.add(assignment);
        return new ResearchTask(id, title, description, subjectReference, subjectKind,
                status, next, linkedCitationIds, blockedReason, resolvedProof,
                createdAt, Instant.now(), resolvedAt, version + 1, audit);
    }

    public ResearchTask withLinkedCitation(TenantScopedId citationId) {
        Objects.requireNonNull(citationId, "citationId");
        if (linkedCitationIds.contains(citationId)) {
            return this;
        }
        java.util.List<TenantScopedId> next = new ArrayList<>(linkedCitationIds);
        next.add(citationId);
        return new ResearchTask(id, title, description, subjectReference, subjectKind,
                status, assignments, next, blockedReason, resolvedProof,
                createdAt, Instant.now(), resolvedAt, version + 1, audit);
    }
}
