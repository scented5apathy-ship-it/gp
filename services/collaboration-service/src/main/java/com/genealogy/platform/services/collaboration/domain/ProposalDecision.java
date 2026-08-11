package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set decision the editor / reviewer records on a
 * {@code ChangeProposal}. Mirrors `contracts/collaboration/
 * collaboration-policy.yaml::spec.proposalDecisions` (E6.2).
 *
 * <p>{@link #APPROVE} + {@link #REJECT} +
 * {@link #REQUEST_CHANGE} + {@link #PARTIAL_MERGE} are
 * review decisions; {@link #WITHDRAW} is the proposer's
 * decision to retract the proposal.
 */
public enum ProposalDecision {
    APPROVE,
    REJECT,
    REQUEST_CHANGE,
    PARTIAL_MERGE,
    WITHDRAW;

    public static ProposalDecision fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("proposalDecision must not be null");
        }
        return ProposalDecision.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}