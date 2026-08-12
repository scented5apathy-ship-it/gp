package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set watch scope. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.watchScopes` (E6.4).
 */
public enum WatchScope {
    PROPOSAL,
    REVIEW,
    COMMENT,
    PERSON,
    RELATIONSHIP,
    TREE_VISIBILITY,
    COLLAB_THREAD;

    public static WatchScope fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("watchScope must not be null");
        }
        return WatchScope.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
