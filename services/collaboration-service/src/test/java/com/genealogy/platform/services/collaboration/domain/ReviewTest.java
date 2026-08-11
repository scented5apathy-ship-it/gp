package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Review}.
 */
class ReviewTest {

    private static CollaborationAuditAttributes audit() {
        return CollaborationAuditAttributes.of("actor-1", "corr-1");
    }

    private static TenantScopedId proposalId() {
        return TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.PROPOSAL, "prop-1");
    }

    private static TenantScopedId reviewId() {
        return TenantScopedId.of("tenant-1",
                TenantScopedId.ResourceKind.REVIEW, "rev-1");
    }

    private static ReAuthorizationDecision allow() {
        return ReAuthorizationDecision.allow(
                "reviewer-1", "corr-1", Instant.now());
    }

    @Test
    void createInitialisesPendingReview() {
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.APPROVE, allow(), null, null, audit());
        assertEquals(ReviewStatus.PENDING, r.status());
        assertEquals(ReviewVerdict.APPROVED, r.verdict());
        assertTrue(r.partialMergeOperations().isEmpty());
        assertEquals(1L, r.version());
    }

    @Test
    void createRejectsReviewerEqualsProposer() {
        assertThrows(IllegalArgumentException.class,
                () -> Review.create(reviewId(), proposalId(),
                        "user-1", "user-1",
                        ProposalDecision.APPROVE, allow(), null, null, audit()));
    }

    @Test
    void createRejectsTenantMismatch() {
        TenantScopedId wrongProposal = TenantScopedId.of("tenant-OTHER",
                TenantScopedId.ResourceKind.PROPOSAL, "prop-1");
        assertThrows(IllegalArgumentException.class,
                () -> Review.create(reviewId(), wrongProposal,
                        "reviewer-1", "proposer-1",
                        ProposalDecision.APPROVE, allow(), null, null, audit()));
    }

    @Test
    void rejectWithoutCommentFails() {
        assertThrows(IllegalArgumentException.class,
                () -> Review.create(reviewId(), proposalId(),
                        "reviewer-1", "proposer-1",
                        ProposalDecision.REJECT, allow(), null, null, audit()));
    }

    @Test
    void requestChangeWithoutCommentFails() {
        assertThrows(IllegalArgumentException.class,
                () -> Review.create(reviewId(), proposalId(),
                        "reviewer-1", "proposer-1",
                        ProposalDecision.REQUEST_CHANGE, allow(), null, null, audit()));
    }

    @Test
    void partialMergeWithoutOperationsFails() {
        assertThrows(IllegalArgumentException.class,
                () -> Review.create(reviewId(), proposalId(),
                        "reviewer-1", "proposer-1",
                        ProposalDecision.PARTIAL_MERGE, allow(),
                        "merge only some", null, audit()));
    }

    @Test
    void approveWithEmptyCommentIsOk() {
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.APPROVE, allow(), null, null, audit());
        assertEquals(ReviewStatus.PENDING, r.status());
        assertNotNull(r.reAuthorization());
    }

    @Test
    void finalizeTransitionsReviewVerdict() {
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.APPROVE, allow(), null, null, audit());
        Review finalized = ReviewStateMachine.finalize(r);
        assertEquals(ReviewStatus.APPROVED, finalized.status());
        assertEquals(2L, finalized.version());
        assertNotNull(finalized.decidedAt());
    }

    @Test
    void partialMergeWithValidOperationsSucceeds() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("givenName", "Anne");
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 5L, fields);
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE, allow(),
                "merge only givenName", List.of(op), audit());
        assertEquals(1, r.partialMergeOperations().size());
    }

    @Test
    void partialMergeOperationsForbiddenForNonPartialMerge() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("givenName", "Anne");
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 5L, fields);
        assertThrows(IllegalArgumentException.class,
                () -> Review.create(reviewId(), proposalId(),
                        "reviewer-1", "proposer-1",
                        ProposalDecision.APPROVE, allow(),
                        "comment", List.of(op), audit()));
    }

    @Test
    void invariantsFlagRejectWithoutComment() {
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.REJECT, allow(), "needs context", null, audit());
        Review finalized = ReviewStateMachine.finalize(r);
        List<CollaborationInvariants.Finding> findings =
                CollaborationInvariants.check(finalized);
        assertFalse(CollaborationInvariants.hasDeny(findings));
    }
}