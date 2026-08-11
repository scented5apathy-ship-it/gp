package com.genealogy.platform.services.collaboration.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Pure executor that materialises a {@code Review} decision
 * into a new {@link ChangeProposal} state. Mirrors
 * `requirements.md` R10.2 (partial merge) + R10.6 (approved
 * change traces back to the proposal + reviewer) +
 * `design.md` §8.3 (merge creates a new domain command and
 * links audit + proposal).
 *
 * <p>The executor refuses to:
 *
 * <ul>
 *   <li>Apply a decision whose {@link ReAuthorizationDecision}
 *       is not {@link ReAuthorizationOutcome#ALLOW} (the
 *       proposal is closed by the {@code reAuthorizationDeny
 *       ClosesProposal} policy).
 *   <li>Apply a partial merge that targets a forbidden
 *       field or a forbidden operation for the proposal's
 *       {@link ProposalKind}.
 *   <li>Apply a partial merge whose materialised commands
 *       don't reference the proposal's {@code baseResourceVersion}
 *       — that would silently bypass optimistic concurrency.
 *   <li>Apply a partial merge whose materialised command
 *       targets a {@code resourceId} not declared in the
 *       proposal's {@code affectedResourceIds}.
 * </ul>
 */
public final class PartialMergeExecutor {

    private PartialMergeExecutor() {
    }

    /**
     * Materialises a {@code Review} decision into a new
     * {@code ChangeProposal} state. Returns a
     * {@link MergeOutcome} describing the next status +
     * the materialised {@link DomainCommand} list (empty
     * for APPROVE / REJECT / REQUEST_CHANGE).
     *
     * @param proposal the proposal the reviewer decided on.
     * @param review the review decision (PENDING reviews are
     *               refused).
     */
    public static MergeOutcome execute(ChangeProposal proposal, Review review) {
        Objects.requireNonNull(proposal, "proposal");
        Objects.requireNonNull(review, "review");
        if (review.status() == ReviewStatus.PENDING) {
            throw new IllegalStateException(
                    "review must be finalised before merge (status=PENDING)");
        }
        if (!review.reAuthorization().isAllow()) {
            return new MergeOutcome(
                    proposal.withStatus(ProposalStatus.REJECTED, null, null,
                            review.reAuthorization()),
                    List.of(),
                    MergeOutcome.Kind.DENY_CLOSES_PROPOSAL);
        }
        return switch (review.decision()) {
            case APPROVE -> new MergeOutcome(
                    proposal.withStatus(ProposalStatus.APPROVED, null, null, null),
                    List.of(),
                    MergeOutcome.Kind.APPROVED);
            case REJECT -> new MergeOutcome(
                    proposal.withStatus(ProposalStatus.REJECTED, null, null, null),
                    List.of(),
                    MergeOutcome.Kind.REJECTED);
            case REQUEST_CHANGE -> new MergeOutcome(
                    proposal.withStatus(ProposalStatus.CHANGES_REQUESTED, null, null, null),
                    List.of(),
                    MergeOutcome.Kind.CHANGES_REQUESTED);
            case PARTIAL_MERGE -> materialisePartialMerge(proposal, review);
            case WITHDRAW -> throw new IllegalArgumentException(
                    "WITHDRAW is not a review verdict");
        };
    }

    private static MergeOutcome materialisePartialMerge(
            ChangeProposal proposal, Review review) {
        if (proposal.status() != ProposalStatus.IN_REVIEW
                && proposal.status() != ProposalStatus.APPROVED) {
            throw new IllegalStateException(
                    "proposal must be IN_REVIEW or APPROVED to partial-merge, got "
                            + proposal.status());
        }
        List<DomainCommand> materialised = new ArrayList<>();
        for (DomainCommand op : review.partialMergeOperations()) {
            if (op.baseVersion() != proposal.baseResourceVersion()) {
                throw new IllegalStateException(
                        "partialMergeOperation baseVersion must equal proposal "
                                + "baseResourceVersion for resourceId=" + op.resourceId()
                                + ", got " + op.baseVersion()
                                + " expected " + proposal.baseResourceVersion());
            }
            if (!proposal.affectedResourceIds().contains(op.resourceId())) {
                throw new IllegalStateException(
                        "partialMergeOperation targets resourceId="
                                + op.resourceId()
                                + " which is not declared in proposal.affectedResourceIds");
            }
            CollaborationInvariants.Finding deniedField = hasForbiddenField(op);
            if (deniedField != null) {
                throw new IllegalStateException(deniedField.message());
            }
            CollaborationInvariants.Finding deniedOp = hasForbiddenOperation(proposal, op);
            if (deniedOp != null) {
                throw new IllegalStateException(deniedOp.message());
            }
            materialised.add(op);
        }
        ProposalStatus nextStatus = proposal.status() == ProposalStatus.IN_REVIEW
                ? ProposalStatus.PARTIALLY_MERGED
                : ProposalStatus.MERGED;
        ChangeProposal next = proposal.withStatus(nextStatus, null, null, null);
        return new MergeOutcome(next, List.copyOf(materialised),
                MergeOutcome.Kind.PARTIAL_MERGE);
    }

    private static CollaborationInvariants.Finding hasForbiddenField(DomainCommand command) {
        for (String field : command.fieldChanges().keySet()) {
            if (CollaborationInvariants.FORBIDDEN_DOMAIN_COMMAND_FIELDS.contains(field)) {
                return new CollaborationInvariants.Finding(
                        CollaborationInvariants.Severity.DENY,
                        CollaborationInvariants.ConflictCode
                                .PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_FIELD,
                        "partialMergeOperation targets forbidden field '" + field + "'");
            }
        }
        return null;
    }

    private static CollaborationInvariants.Finding hasForbiddenOperation(
            ChangeProposal proposal, DomainCommand command) {
        var forbidden = CollaborationInvariants.FORBIDDEN_PROPOSAL_KIND_OPERATIONS
                .getOrDefault(proposal.kind(), java.util.Set.of());
        if (forbidden.contains(command.kind())) {
            return new CollaborationInvariants.Finding(
                    CollaborationInvariants.Severity.DENY,
                    CollaborationInvariants.ConflictCode
                            .PROPOSAL_DOMAIN_COMMAND_FORBIDDEN_OPERATION,
                    "partialMergeOperation kind=" + command.kind()
                            + " forbidden for proposalKind=" + proposal.kind());
        }
        return null;
    }

    public record MergeOutcome(
            ChangeProposal proposal,
            List<DomainCommand> materialisedCommands,
            Kind kind) {

        public MergeOutcome {
            Objects.requireNonNull(proposal, "proposal");
            Objects.requireNonNull(materialisedCommands, "materialisedCommands");
            Objects.requireNonNull(kind, "kind");
            materialisedCommands = List.copyOf(materialisedCommands);
        }

        public enum Kind {
            APPROVED,
            REJECTED,
            CHANGES_REQUESTED,
            PARTIAL_MERGE,
            DENY_CLOSES_PROPOSAL
        }
    }
}