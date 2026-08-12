package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set activity feed item visibility. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.activityVisibilities` (E6.4).
 */
public enum ActivityVisibility {
    PUBLIC,
    TREE,
    BRANCH,
    PRIVATE;

    public static ActivityVisibility fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("activityVisibility must not be null");
        }
        return ActivityVisibility.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
