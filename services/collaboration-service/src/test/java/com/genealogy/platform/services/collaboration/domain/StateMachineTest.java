package com.genealogy.platform.services.collaboration.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for the proposal + review state machines.
 */
class StateMachineTest {

    @Test
    void draftToSubmittedIsAllowed() {
        assertTrue(ChangeProposalStateMachine.canTransition(
                ProposalStatus.DRAFT, ProposalStatus.SUBMITTED));
    }

    @Test
    void draftToRejectedIsRejected() {
        assertFalse(ChangeProposalStateMachine.canTransition(
                ProposalStatus.DRAFT, ProposalStatus.REJECTED));
    }

    @Test
    void submittedToInReviewIsAllowed() {
        assertTrue(ChangeProposalStateMachine.canTransition(
                ProposalStatus.SUBMITTED, ProposalStatus.IN_REVIEW));
    }

    @Test
    void inReviewToPartiallyMergedIsAllowed() {
        assertTrue(ChangeProposalStateMachine.canTransition(
                ProposalStatus.IN_REVIEW, ProposalStatus.PARTIALLY_MERGED));
    }

    @Test
    void approvedToMergedIsAllowed() {
        assertTrue(ChangeProposalStateMachine.canTransition(
                ProposalStatus.APPROVED, ProposalStatus.MERGED));
    }

    @Test
    void approvedToPartiallyMergedIsAllowed() {
        assertTrue(ChangeProposalStateMachine.canTransition(
                ProposalStatus.APPROVED, ProposalStatus.PARTIALLY_MERGED));
    }

    @Test
    void mergedRejectsAllTransitions() {
        for (ProposalStatus next : ProposalStatus.values()) {
            assertFalse(ChangeProposalStateMachine.canTransition(
                    ProposalStatus.MERGED, next),
                    "MERGED -> " + next + " must be rejected");
        }
    }

    @Test
    void rejectedRejectsAllTransitions() {
        for (ProposalStatus next : ProposalStatus.values()) {
            assertFalse(ChangeProposalStateMachine.canTransition(
                    ProposalStatus.REJECTED, next),
                    "REJECTED -> " + next + " must be rejected");
        }
    }

    @Test
    void withdrawnRejectsAllTransitions() {
        for (ProposalStatus next : ProposalStatus.values()) {
            assertFalse(ChangeProposalStateMachine.canTransition(
                    ProposalStatus.WITHDRAWN, next),
                    "WITHDRAWN -> " + next + " must be rejected");
        }
    }

    @Test
    void expiredRejectsAllTransitions() {
        for (ProposalStatus next : ProposalStatus.values()) {
            assertFalse(ChangeProposalStateMachine.canTransition(
                    ProposalStatus.EXPIRED, next),
                    "EXPIRED -> " + next + " must be rejected");
        }
    }

    @Test
    void changesRequestedCanBeResubmitted() {
        assertTrue(ChangeProposalStateMachine.canTransition(
                ProposalStatus.CHANGES_REQUESTED, ProposalStatus.SUBMITTED));
        assertTrue(ChangeProposalStateMachine.canTransition(
                ProposalStatus.CHANGES_REQUESTED, ProposalStatus.WITHDRAWN));
    }

    @Test
    void reviewStatusPendingToApprovedIsAllowed() {
        assertTrue(ReviewStateMachine.canTransition(
                ReviewStatus.PENDING, ReviewStatus.APPROVED));
        assertTrue(ReviewStateMachine.canTransition(
                ReviewStatus.PENDING, ReviewStatus.REJECTED));
        assertTrue(ReviewStateMachine.canTransition(
                ReviewStatus.PENDING, ReviewStatus.CHANGES_REQUESTED));
        assertTrue(ReviewStateMachine.canTransition(
                ReviewStatus.PENDING, ReviewStatus.PARTIAL_MERGED));
    }

    @Test
    void reviewStatusTerminalRejectsAllTransitions() {
        for (ReviewStatus from : new ReviewStatus[] {
                ReviewStatus.APPROVED, ReviewStatus.REJECTED,
                ReviewStatus.CHANGES_REQUESTED, ReviewStatus.PARTIAL_MERGED,
        }) {
            for (ReviewStatus to : ReviewStatus.values()) {
                assertFalse(ReviewStateMachine.canTransition(from, to),
                        from + " -> " + to + " must be rejected");
            }
        }
    }

    @Test
    void illegalProposalTransitionThrows() {
        assertThrows(IllegalStateException.class,
                () -> ChangeProposalStateMachine.assertTransition(
                        ProposalStatus.DRAFT, ProposalStatus.MERGED));
    }

    @Test
    void illegalReviewTransitionThrows() {
        assertThrows(IllegalStateException.class,
                () -> ReviewStateMachine.assertTransition(
                        ReviewStatus.APPROVED, ReviewStatus.REJECTED));
    }

    @Test
    void transitionRejectsSelfLoop() {
        assertFalse(ChangeProposalStateMachine.canTransition(
                ProposalStatus.IN_REVIEW, ProposalStatus.IN_REVIEW));
    }
}