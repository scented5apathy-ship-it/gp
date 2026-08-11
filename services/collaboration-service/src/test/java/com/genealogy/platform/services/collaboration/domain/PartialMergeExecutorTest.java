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
 * Unit tests for {@link PartialMergeExecutor}.
 */
class PartialMergeExecutorTest {

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

    private static DomainDiff diff() {
        return DomainDiff.of("person-1", 5L,
                List.of(DomainCommand.of(
                        DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                        Map.of("givenName", "Anne"))));
    }

    private static ChangeProposal inReviewProposal() {
        ChangeProposal p = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff(),
                List.of("person-1"), "proposer-1", null, audit());
        ChangeProposal submitted = ChangeProposalStateMachine.transition(
                p, ProposalStatus.SUBMITTED,
                ReAuthorizationDecision.allow("proposer-1", "corr-1", Instant.now()));
        return ChangeProposalStateMachine.transition(
                submitted, ProposalStatus.IN_REVIEW, null);
    }

    @Test
    void approveWithoutPartialMergeReturnsApproved() {
        ChangeProposal p = inReviewProposal();
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.APPROVE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                null, null, audit());
        Review finalized = ReviewStateMachine.finalize(r);
        PartialMergeExecutor.MergeOutcome outcome =
                PartialMergeExecutor.execute(p, finalized);
        assertEquals(ProposalStatus.APPROVED, outcome.proposal().status());
        assertTrue(outcome.materialisedCommands().isEmpty());
        assertEquals(PartialMergeExecutor.MergeOutcome.Kind.APPROVED, outcome.kind());
    }

    @Test
    void rejectWithoutPartialMergeReturnsRejected() {
        ChangeProposal p = inReviewProposal();
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.REJECT,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "wrong date", null, audit());
        Review finalized = ReviewStateMachine.finalize(r);
        PartialMergeExecutor.MergeOutcome outcome =
                PartialMergeExecutor.execute(p, finalized);
        assertEquals(ProposalStatus.REJECTED, outcome.proposal().status());
        assertEquals(PartialMergeExecutor.MergeOutcome.Kind.REJECTED, outcome.kind());
    }

    @Test
    void requestChangeReturnsChangesRequested() {
        ChangeProposal p = inReviewProposal();
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.REQUEST_CHANGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "add source", null, audit());
        Review finalized = ReviewStateMachine.finalize(r);
        PartialMergeExecutor.MergeOutcome outcome =
                PartialMergeExecutor.execute(p, finalized);
        assertEquals(ProposalStatus.CHANGES_REQUESTED, outcome.proposal().status());
        assertEquals(PartialMergeExecutor.MergeOutcome.Kind.CHANGES_REQUESTED, outcome.kind());
    }

    @Test
    void partialMergeMaterialisesCommands() {
        ChangeProposal p = inReviewProposal();
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("givenName", "Anne");
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 5L, fields);
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "merge only givenName", List.of(op), audit());
        Review finalized = ReviewStateMachine.finalize(r);
        PartialMergeExecutor.MergeOutcome outcome =
                PartialMergeExecutor.execute(p, finalized);
        assertEquals(ProposalStatus.PARTIALLY_MERGED, outcome.proposal().status());
        assertEquals(1, outcome.materialisedCommands().size());
        assertEquals("Anne", outcome.materialisedCommands().get(0)
                .fieldChanges().get("givenName"));
    }

    @Test
    void partialMergeRejectsBaseVersionMismatch() {
        ChangeProposal p = inReviewProposal();
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 4L,
                Map.of("givenName", "Anne"));
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "merge givenName", List.of(op), audit());
        Review finalized = ReviewStateMachine.finalize(r);
        assertThrows(IllegalStateException.class,
                () -> PartialMergeExecutor.execute(p, finalized));
    }

    @Test
    void partialMergeRejectsUndeclaredResourceId() {
        ChangeProposal p = inReviewProposal();
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-OTHER", 5L,
                Map.of("givenName", "Anne"));
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "merge givenName", List.of(op), audit());
        Review finalized = ReviewStateMachine.finalize(r);
        assertThrows(IllegalStateException.class,
                () -> PartialMergeExecutor.execute(p, finalized));
    }

    @Test
    void partialMergeRejectsForbiddenField() {
        ChangeProposal p = inReviewProposal();
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                Map.of("dnaRawData", "ACGT"));
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "merge dna", List.of(op), audit());
        Review finalized = ReviewStateMachine.finalize(r);
        assertThrows(IllegalStateException.class,
                () -> PartialMergeExecutor.execute(p, finalized));
    }

    @Test
    void partialMergeRejectsForbiddenOperation() {
        ChangeProposal p = inReviewProposal();
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.SET_TREE_VISIBILITY, "tree-1", 5L,
                Map.of("visibility", "PRIVATE"));
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "merge tree visibility", List.of(op), audit());
        Review finalized = ReviewStateMachine.finalize(r);
        assertThrows(IllegalStateException.class,
                () -> PartialMergeExecutor.execute(p, finalized));
    }

    @Test
    void denyReAuthorizationClosesProposalDuringMerge() {
        ChangeProposal p = inReviewProposal();
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.APPROVE,
                ReAuthorizationDecision.deny("reviewer-1", "corr-1",
                        "tuple_revoked", Instant.now()),
                null, null, audit());
        Review finalized = ReviewStateMachine.finalize(r);
        PartialMergeExecutor.MergeOutcome outcome =
                PartialMergeExecutor.execute(p, finalized);
        assertEquals(ProposalStatus.REJECTED, outcome.proposal().status());
        assertEquals(PartialMergeExecutor.MergeOutcome.Kind.DENY_CLOSES_PROPOSAL,
                outcome.kind());
        assertTrue(outcome.materialisedCommands().isEmpty());
    }

    @Test
    void abacDenyReAuthorizationClosesProposalDuringMerge() {
        ChangeProposal p = inReviewProposal();
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.APPROVE,
                ReAuthorizationDecision.abacDeny("reviewer-1", "corr-1",
                        "living_marker", Instant.now()),
                null, null, audit());
        Review finalized = ReviewStateMachine.finalize(r);
        PartialMergeExecutor.MergeOutcome outcome =
                PartialMergeExecutor.execute(p, finalized);
        assertEquals(ProposalStatus.REJECTED, outcome.proposal().status());
        assertEquals(PartialMergeExecutor.MergeOutcome.Kind.DENY_CLOSES_PROPOSAL,
                outcome.kind());
    }

    @Test
    void pendingReviewRefusesExecute() {
        ChangeProposal p = inReviewProposal();
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.APPROVE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                null, null, audit());
        assertEquals(ReviewStatus.PENDING, r.status());
        assertThrows(IllegalStateException.class,
                () -> PartialMergeExecutor.execute(p, r));
    }

    @Test
    void partialMergeFromApprovedProposalJumpsToMerged() {
        ChangeProposal p = inReviewProposal();
        ChangeProposal approved = ChangeProposalStateMachine.transition(
                p, ProposalStatus.APPROVED, null);
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                Map.of("givenName", "Anne"));
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "final merge", List.of(op), audit());
        Review finalized = ReviewStateMachine.finalize(r);
        PartialMergeExecutor.MergeOutcome outcome =
                PartialMergeExecutor.execute(approved, finalized);
        assertEquals(ProposalStatus.MERGED, outcome.proposal().status());
        assertEquals(1, outcome.materialisedCommands().size());
        assertNotNull(outcome.proposal().mergedAt());
    }

    @Test
    void partialMergeOnDraftProposalRefuses() {
        ChangeProposal draft = ChangeProposal.create(proposalId(), "title", null,
                "scope", "reason", "source",
                ProposalKind.PERSON, diff(),
                List.of("person-1"), "proposer-1", null, audit());
        DomainCommand op = DomainCommand.of(
                DomainCommandKind.UPDATE_PERSON, "person-1", 5L,
                Map.of("givenName", "Anne"));
        Review r = Review.create(reviewId(), proposalId(),
                "reviewer-1", "proposer-1",
                ProposalDecision.PARTIAL_MERGE,
                ReAuthorizationDecision.allow("reviewer-1", "corr-1", Instant.now()),
                "merge givenName", List.of(op), audit());
        Review finalized = ReviewStateMachine.finalize(r);
        assertThrows(IllegalStateException.class,
                () -> PartialMergeExecutor.execute(draft, finalized));
    }

    @Test
    void hasDenyFalseForEmptyFindings() {
        assertFalse(CollaborationInvariants.hasDeny(List.of()));
    }
}