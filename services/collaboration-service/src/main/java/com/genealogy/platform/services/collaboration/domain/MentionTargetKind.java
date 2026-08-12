package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set mention target kind. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.mentionTargetKinds` (E6.4).
 */
public enum MentionTargetKind {
    USER,
    ROLE,
    TREE,
    BRANCH;

    public static MentionTargetKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("mentionTargetKind must not be null");
        }
        return MentionTargetKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
