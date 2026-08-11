package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set lifecycle of a {@code ChangeProposal}. Mirrors
 * `contracts/collaboration/collaboration-policy.yaml::
 * spec.proposalStatuses` (E6.2) and the
 * {@code proposalStatusMatrix} transition map.
 *
 * <p>Terminal states ({@link #MERGED}, {@link #REJECTED},
 * {@link #WITHDRAWN}, {@link #EXPIRED}) never transition out.
 * {@link #APPROVED} becomes {@link #MERGED} or
 * {@link #PARTIALLY_MERGED} when the merge executor
 * materialises the decision into a domain command list.
 */
public enum ProposalStatus {
    DRAFT,
    SUBMITTED,
    IN_REVIEW,
    CHANGES_REQUESTED,
    APPROVED,
    PARTIALLY_MERGED,
    MERGED,
    REJECTED,
    WITHDRAWN,
    EXPIRED;

    public static ProposalStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("proposalStatus must not be null");
        }
        return ProposalStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isTerminal() {
        return this == MERGED
                || this == REJECTED
                || this == WITHDRAWN
                || this == EXPIRED;
    }
}