package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set verdict produced by a {@code Review}. Mirrors
 * `contracts/collaboration/collaboration-policy.yaml::
 * spec.reviewVerdicts` (E6.2). The verdict MUST map 1-1 to
 * a {@link ProposalDecision}: APPROVE → APPROVED,
 * REJECT → REJECTED, REQUEST_CHANGE → CHANGES_REQUESTED,
 * PARTIAL_MERGE → PARTIALLY_MERGED.
 */
public enum ReviewVerdict {
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED,
    PARTIALLY_MERGED;

    public static ReviewVerdict fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("reviewVerdict must not be null");
        }
        return ReviewVerdict.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}