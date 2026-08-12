package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set assignment status. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.assignmentStatuses` (E6.4).
 */
public enum AssignmentStatus {
    PENDING,
    ACCEPTED,
    DECLINED,
    EXPIRED,
    REVOKED;

    public static AssignmentStatus fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("assignmentStatus must not be null");
        }
        return AssignmentStatus.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
