package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set outcome produced by the
 * {@code ReAuthorizationPort} when the executor re-checks
 * OpenFGA + ABAC at submit / approve / partial-merge time.
 * Mirrors `contracts/collaboration/collaboration-policy.yaml
 * ::spec.reAuthorizationOutcomes` (E6.2) +
 * `requirements.md` R10.6 (approved change traces to a
 * reviewer that still had permission at review time) +
 * `design.md` §8.3 (review re-checks OpenFGA + ABAC at
 * approve time).
 *
 * <ul>
 *   <li>{@link #ALLOW} — both OpenFGA and ABAC returned allow.
 *   <li>{@link #DENY} — OpenFGA returned deny (relationship
 *       tuple missing or revoked).
 *   <li>{@link #ABAC_DENY} — OpenFGA allowed but ABAC overlay
 *       (living, DNA, consent, contextual deny) returned
 *       deny.
 * </ul>
 */
public enum ReAuthorizationOutcome {
    ALLOW,
    DENY,
    ABAC_DENY;

    public static ReAuthorizationOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("reAuthorizationOutcome must not be null");
        }
        return ReAuthorizationOutcome.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}