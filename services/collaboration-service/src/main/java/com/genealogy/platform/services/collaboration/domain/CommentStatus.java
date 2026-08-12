package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set comment lifecycle status. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.commentStatuses` (E6.4) and `requirements.md`
 * R10.5 (system SHALL have comment + mention + watch +
 * assignment + notification + activity feed). Adding a
 * new status requires an ADR supersession and an update
 * to the contract.
 */
public enum CommentStatus {
    ACTIVE,
    EDITED,
    DELETED,
    REDACTED,
    HIDDEN;

    public static CommentStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("commentStatus must not be null");
        }
        return CommentStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
