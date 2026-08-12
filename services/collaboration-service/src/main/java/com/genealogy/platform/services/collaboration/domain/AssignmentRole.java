package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set assignment role. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.assignmentRoles` (E6.4).
 */
public enum AssignmentRole {
    WATCHER,
    REVIEWER,
    APPROVER,
    GATEKEEPER,
    MENTIONED;

    public static AssignmentRole fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("assignmentRole must not be null");
        }
        return AssignmentRole.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
