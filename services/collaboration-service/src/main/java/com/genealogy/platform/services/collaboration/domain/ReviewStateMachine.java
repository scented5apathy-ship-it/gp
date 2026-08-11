package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Pure state-transition matrix for a {@link Review}. Mirrors
 * `contracts/collaboration/collaboration-policy.yaml::
 * spec.reviewStatusMatrix` (E6.2) + `requirements.md` R10.2
 * (every reviewer decision is a state change).
 *
 * <p>Only {@link ReviewStatus#PENDING} is non-terminal; every
 * verdict moves the review to a terminal state. Follow-ups
 * require a new review record (preserves the audit chain
 * per R16.2 + R10.6).
 */
public final class ReviewStateMachine {

    private ReviewStateMachine() {
    }

    public static boolean canTransition(ReviewStatus from, ReviewStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return false;
        }
        if (from != ReviewStatus.PENDING) {
            return false;
        }
        return to == ReviewStatus.APPROVED
                || to == ReviewStatus.REJECTED
                || to == ReviewStatus.CHANGES_REQUESTED
                || to == ReviewStatus.PARTIAL_MERGED;
    }

    public static void assertTransition(ReviewStatus from, ReviewStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "illegal reviewStatus transition: " + from + " -> " + to);
        }
    }

    public static Review finalize(Review review) {
        Objects.requireNonNull(review, "review");
        assertTransition(review.status(), mapTarget(review.decision()));
        return review.withVerdict();
    }

    private static ReviewStatus mapTarget(ProposalDecision decision) {
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