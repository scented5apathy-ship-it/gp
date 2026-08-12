package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set notification outcome. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.notificationOutcomes` (E6.4).
 */
public enum NotificationOutcome {
    DELIVERED,
    DROPPED,
    RATE_LIMITED,
    REDACTED,
    TEMPLATE_MISSING,
    CHANNEL_DISABLED,
    RECIPIENT_OPTED_OUT;

    public static NotificationOutcome fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("notificationOutcome must not be null");
        }
        return NotificationOutcome.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
