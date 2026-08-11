package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set conflict resolution outcome. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.conflictResolutions` (E6.3) and `requirements.md`
 * R10.3 (the system SHALL detect optimistic concurrency
 * conflicts and provide a comparison model). The executor
 * chooses between auto-merge (no overlapping field write),
 * manual-merge (audit reason required) and abandoned
 * (writer requested a discard).
 */
public enum ConflictResolution {
    AUTO_MERGE,
    MANUAL_MERGE,
    ABANDONED;

    public static ConflictResolution fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("conflictResolution must not be null");
        }
        return ConflictResolution.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
