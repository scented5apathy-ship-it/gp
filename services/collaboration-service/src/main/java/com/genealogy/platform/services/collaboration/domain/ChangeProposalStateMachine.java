package com.genealogy.platform.services.collaboration.domain;

import java.util.Objects;

/**
 * Pure state-transition matrix for a {@link ChangeProposal}.
 * Mirrors `contracts/collaboration/collaboration-policy.yaml
 * ::spec.proposalStatusMatrix` (E6.2) +
 * `requirements.md` R10.2 (reviewer's decision changes
 * status) + `design.md` §8.3 (proposal status drives the
 * partial-merge executor).
 *
 * <p>The matrix is intentionally restrictive:
 *
 * <ul>
 *   <li>{@code DRAFT} → {@code SUBMITTED}, {@code WITHDRAWN}.
 *   <li>{@code SUBMITTED} → {@code IN_REVIEW},
 *       {@code CHANGES_REQUESTED}, {@code REJECTED},
 *       {@code WITHDRAWN}, {@code EXPIRED}.
 *   <li>{@code IN_REVIEW} → {@code APPROVED},
 *       {@code REJECTED}, {@code CHANGES_REQUESTED},
 *       {@code PARTIALLY_MERGED}, {@code WITHDRAWN},
 *       {@code EXPIRED}.
 *   <li>{@code CHANGES_REQUESTED} → {@code SUBMITTED},
 *       {@code WITHDRAWN}.
 *   <li>{@code APPROVED} → {@code MERGED},
 *       {@code PARTIALLY_MERGED}, {@code EXPIRED}.
 *   <li>{@code PARTIALLY_MERGED} → {@code MERGED},
 *       {@code EXPIRED}.
 *   <li>{@code MERGED} / {@code REJECTED} / {@code WITHDRAWN}
 *       / {@code EXPIRED} are terminal.
 * </ul>
 */
public final class ChangeProposalStateMachine {

    private ChangeProposalStateMachine() {
    }

    public static boolean canTransition(ProposalStatus from, ProposalStatus to) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from == to) {
            return false;
        }
        return switch (from) {
            case DRAFT -> to == ProposalStatus.SUBMITTED
                    || to == ProposalStatus.WITHDRAWN;
            case SUBMITTED -> to == ProposalStatus.IN_REVIEW
                    || to == ProposalStatus.CHANGES_REQUESTED
                    || to == ProposalStatus.REJECTED
                    || to == ProposalStatus.WITHDRAWN
                    || to == ProposalStatus.EXPIRED;
            case IN_REVIEW -> to == ProposalStatus.APPROVED
                    || to == ProposalStatus.REJECTED
                    || to == ProposalStatus.CHANGES_REQUESTED
                    || to == ProposalStatus.PARTIALLY_MERGED
                    || to == ProposalStatus.WITHDRAWN
                    || to == ProposalStatus.EXPIRED;
            case CHANGES_REQUESTED -> to == ProposalStatus.SUBMITTED
                    || to == ProposalStatus.WITHDRAWN;
            case APPROVED -> to == ProposalStatus.MERGED
                    || to == ProposalStatus.PARTIALLY_MERGED
                    || to == ProposalStatus.EXPIRED;
            case PARTIALLY_MERGED -> to == ProposalStatus.MERGED
                    || to == ProposalStatus.EXPIRED;
            case MERGED, REJECTED, WITHDRAWN, EXPIRED -> false;
        };
    }

    public static void assertTransition(ProposalStatus from, ProposalStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "illegal proposalStatus transition: " + from + " -> " + to);
        }
    }

    public static ChangeProposal transition(
            ChangeProposal proposal,
            ProposalStatus to,
            ReAuthorizationDecision reAuth) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(to, "to");
        assertTransition(proposal.status(), to);
        return proposal.withStatus(to, null, null, reAuth);
    }
}