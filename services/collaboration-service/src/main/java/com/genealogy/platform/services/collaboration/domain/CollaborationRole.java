package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set collaboration role. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.collaborationRoles` (E6.3) and `requirements.md`
 * R10.4 (tenant / tree admin SHALL configure direct edit or
 * approval per role / branch / resource type). Adding a new
 * role requires an ADR supersession and an update to the
 * contract.
 */
public enum CollaborationRole {
    TENANT_ADMIN,
    TREE_ADMIN,
    EDITOR,
    REVIEWER,
    CONTRIBUTOR,
    VIEWER,
    GUARDIAN,
    DNA_STEWARD;

    public static CollaborationRole fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("collaborationRole must not be null");
        }
        return CollaborationRole.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
