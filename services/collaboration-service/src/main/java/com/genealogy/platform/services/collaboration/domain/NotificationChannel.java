package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set notification channel. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.notificationChannels` (E6.4).
 */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    PUSH,
    WEBHOOK;

    public static NotificationChannel fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("notificationChannel must not be null");
        }
        return NotificationChannel.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
