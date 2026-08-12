package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set comment authorization outcome. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.reAuthorizationDenyClosesSubscription +
 * reAuthorizationAbacDenyClosesSubscription` (E6.4).
 */
public enum CommentAuthorizationOutcome {
    ALLOW,
    DENY,
    ABAC_DENY;

    public static CommentAuthorizationOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("commentAuthorizationOutcome must not be null");
        }
        return CommentAuthorizationOutcome.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
