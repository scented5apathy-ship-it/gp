package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set classification of a {@code ChangeProposal}.
 * Mirrors `contracts/collaboration/collaboration-policy.yaml
 * ::spec.proposalKinds` (E6.2) and `requirements.md` R10.1
 * (proposal scope + reason + diff). Each kind maps to a
 * well-defined set of {@link DomainCommandKind} values; the
 * executor rejects proposals that try to mutate a resource
 * kind that does not match (see
 * {@code spec.forbiddenProposalKindOperations}).
 */
public enum ProposalKind {
    PERSON,
    RELATIONSHIP,
    LIFE_EVENT,
    CLAIM,
    SOURCE,
    CITATION,
    TREE_VISIBILITY;

    public static ProposalKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("proposalKind must not be null");
        }
        return ProposalKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}