package com.genealogy.platform.services.collaboration.domain;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Review aggregate root. A review is "an editor's decision
 * on a ChangeProposal: approve, reject, request change, or
 * partial-merge". Mirrors `requirements.md` R10.2 + R10.6 +
 * `design.md` §8.3 + `contracts/collaboration/collaboration-
 * policy.yaml::spec.reviewSchema` (E6.2).
 *
 * <p>Invariants enforced by the compact constructor:
 *
 * <ul>
 *   <li>Tenant scope is mandatory (NFR1).
 *   <li>The reviewer MUST be different from the proposer
 *       (separation of duties, R10.6).
 *   <li>Reject / request-change / partial-merge MUST carry
 *       a non-blank {@code comment} that the UI surfaces to
 *       the proposer.
 *   <li>Partial-merge MUST carry at least one
 *       {@code partialMergeOperation} — the executor
 *       materialises a new {@code DomainCommand} list from
 *       them.
 *   <li>Status transitions are enforced by
 *       {@link ReviewStateMachine}.
 *   <li>Total {@code partialMergeOperations} ≤
 *       {@link #MAX_PARTIAL_MERGE_OPERATIONS}.
 * </ul>
 */
public record Review(
        TenantScopedId id,
        TenantScopedId proposalId,
        String reviewerPseudoId,
        String proposerPseudoId,
        ProposalDecision decision,
        ReviewStatus status,
        ReviewVerdict verdict,
        ReAuthorizationDecision reAuthorization,
        String comment,
        List<DomainCommand> partialMergeOperations,
        Instant createdAt,
        Instant decidedAt,
        long version,
        CollaborationAuditAttributes audit) {

    public static final int MAX_COMMENT_LENGTH = 4096;
    public static final int MAX_PARTIAL_MERGE_OPERATIONS = 256;

    public Review {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(reviewerPseudoId, "reviewerPseudoId");
        Objects.requireNonNull(proposerPseudoId, "proposerPseudoId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(verdict, "verdict");
        Objects.requireNonNull(reAuthorization, "reAuthorization");
        Objects.requireNonNull(createdAt, "createdAt");
        Objects.requireNonNull(audit, "audit");
        if (id.resourceKind() != TenantScopedId.ResourceKind.REVIEW) {
            throw new IllegalArgumentException(
                    "TenantScopedId resourceKind must be REVIEW, got "
                            + id.resourceKind());
        }
        if (proposalId.resourceKind() != TenantScopedId.ResourceKind.PROPOSAL) {
            throw new IllegalArgumentException(
                    "proposalId resourceKind must be PROPOSAL, got "
                            + proposalId.resourceKind());
        }
        if (!id.tenantId().equals(proposalId.tenantId())) {
            throw new IllegalArgumentException(
                    "review tenant must match proposal tenant");
        }
        if (reviewerPseudoId.isBlank()) {
            throw new IllegalArgumentException(
                    "reviewerPseudoId must not be blank");
        }
        if (reviewerPseudoId.length() > 128) {
            throw new IllegalArgumentException(
                    "reviewerPseudoId exceeds 128 characters");
        }
        if (proposerPseudoId.isBlank()) {
            throw new IllegalArgumentException(
                    "proposerPseudoId must not be blank");
        }
        if (proposerPseudoId.length() > 128) {
            throw new IllegalArgumentException(
                    "proposerPseudoId exceeds 128 characters");
        }
        if (reviewerPseudoId.equals(proposerPseudoId)) {
            throw new IllegalArgumentException(
                    "reviewerPseudoId must differ from proposerPseudoId (separation of duties)");
        }
        if (comment != null && comment.length() > MAX_COMMENT_LENGTH) {
            throw new IllegalArgumentException(
                    "comment exceeds " + MAX_COMMENT_LENGTH + " characters");
        }
        if (comment != null && comment.isBlank()) {
            comment = null;
        }
        if ((decision == ProposalDecision.REJECT
                || decision == ProposalDecision.REQUEST_CHANGE
                || decision == ProposalDecision.PARTIAL_MERGE)
                && (comment == null)) {
            throw new IllegalArgumentException(
                    "decision=" + decision + " requires a non-blank comment");
        }
        partialMergeOperations = partialMergeOperations == null
                ? List.of()
                : Collections.unmodifiableList(new ArrayList<>(partialMergeOperations));
        if (partialMergeOperations.size() > MAX_PARTIAL_MERGE_OPERATIONS) {
            throw new IllegalArgumentException(
                    "partialMergeOperations exceeds "
                            + MAX_PARTIAL_MERGE_OPERATIONS + ": "
                            + partialMergeOperations.size());
        }
        if (decision == ProposalDecision.PARTIAL_MERGE
                && partialMergeOperations.isEmpty()) {
            throw new IllegalArgumentException(
                    "decision=PARTIAL_MERGE requires at least one partialMergeOperation");
        }
        if (decision != ProposalDecision.PARTIAL_MERGE
                && !partialMergeOperations.isEmpty()) {
            throw new IllegalArgumentException(
                    "partialMergeOperations only allowed for decision=PARTIAL_MERGE");
        }
        if (version <= 0) {
            throw new IllegalArgumentException("version must be positive");
        }
    }

    public static Review create(
            TenantScopedId id,
            TenantScopedId proposalId,
            String reviewerPseudoId,
            String proposerPseudoId,
            ProposalDecision decision,
            ReAuthorizationDecision reAuthorization,
            String comment,
            List<DomainCommand> partialMergeOperations,
            CollaborationAuditAttributes audit) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(proposalId, "proposalId");
        Objects.requireNonNull(reviewerPseudoId, "reviewerPseudoId");
        Objects.requireNonNull(proposerPseudoId, "proposerPseudoId");
        Objects.requireNonNull(decision, "decision");
        Objects.requireNonNull(reAuthorization, "reAuthorization");
        Objects.requireNonNull(audit, "audit");
        ReviewVerdict verdict = mapVerdict(decision);
        ReviewStatus status = ReviewStatus.PENDING;
        return new Review(id, proposalId, reviewerPseudoId, proposerPseudoId,
                decision, status, verdict, reAuthorization, comment,
                partialMergeOperations == null ? List.of() : List.copyOf(partialMergeOperations),
                Instant.now(), null, 1L, audit);
    }

    public Review withVerdict() {
        return new Review(id, proposalId, reviewerPseudoId, proposerPseudoId,
                decision, mapStatus(decision), verdict, reAuthorization,
                comment, partialMergeOperations,
                createdAt, Instant.now(), version + 1, audit);
    }

    private static ReviewVerdict mapVerdict(ProposalDecision decision) {
        return switch (decision) {
            case APPROVE -> ReviewVerdict.APPROVED;
            case REJECT -> ReviewVerdict.REJECTED;
            case REQUEST_CHANGE -> ReviewVerdict.CHANGES_REQUESTED;
            case PARTIAL_MERGE -> ReviewVerdict.PARTIALLY_MERGED;
            case WITHDRAW -> throw new IllegalArgumentException(
                    "WITHDRAW is not a review verdict");
        };
    }

    private static ReviewStatus mapStatus(ProposalDecision decision) {
        return switch (decision) {
            case APPROVE -> ReviewStatus.APPROVED;
            case REJECT -> ReviewStatus.REJECTED;
            case REQUEST_CHANGE -> ReviewStatus.CHANGES_REQUESTED;
            case PARTIAL_MERGE -> ReviewStatus.PARTIAL_MERGED;
            case WITHDRAW -> throw new IllegalArgumentException(
                    "WITHDRAW is not a review verdict");
        };
    }
}