package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set review lifecycle. Mirrors `contracts/
 * collaboration/collaboration-policy.yaml::
 * spec.reviewStatuses` (E6.2). Only {@link #PENDING} is
 * non-terminal; every verdict moves the review to a terminal
 * state. Follow-ups require a new review record (which
 * preserves the audit chain per R16.2 + R10.6).
 */
public enum ReviewStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CHANGES_REQUESTED,
    PARTIAL_MERGED;

    public static ReviewStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("reviewStatus must not be null");
        }
        return ReviewStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isTerminal() {
        return this != PENDING;
    }
}