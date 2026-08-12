package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set activity feed item kind. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.activityKinds` (E6.4).
 */
public enum ActivityKind {
    COMMENT_CREATED,
    COMMENT_EDITED,
    COMMENT_REDACTED,
    COMMENT_DELETED,
    MENTION_NOTIFIED,
    MENTION_DROPPED,
    WATCH_SUBSCRIBED,
    WATCH_UNSUBSCRIBED,
    ASSIGNMENT_OPENED,
    ASSIGNMENT_ACCEPTED,
    ASSIGNMENT_DECLINED,
    ASSIGNMENT_REVOKED,
    ASSIGNMENT_EXPIRED,
    NOTIFICATION_DELIVERED,
    NOTIFICATION_DROPPED;

    public static ActivityKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("activityKind must not be null");
        }
        return ActivityKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
