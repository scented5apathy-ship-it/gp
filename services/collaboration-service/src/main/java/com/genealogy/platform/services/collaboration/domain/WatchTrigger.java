package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set watch trigger. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.watchTriggers` (E6.4).
 */
public enum WatchTrigger {
    ANY_CHANGE,
    MENTION,
    STATUS_CHANGE,
    DIRECT_EDIT,
    APPROVAL_REQUIRED,
    DENY;

    public static WatchTrigger fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("watchTrigger must not be null");
        }
        return WatchTrigger.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
