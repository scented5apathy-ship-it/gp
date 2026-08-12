package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set notification hook kind. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.notificationHookKinds` (E6.4).
 */
public enum NotificationHookKind {
    COMMENT_CREATED,
    MENTION,
    WATCH_TRIGGER,
    ASSIGNMENT_DUE;

    public static NotificationHookKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("notificationHookKind must not be null");
        }
        return NotificationHookKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
