package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set per-field conflict classification. Mirrors
 * `contracts/collaboration/mixed-collaboration-policy.yaml
 * ::spec.conflictFieldKinds` (E6.3) and `requirements.md`
 * R10.3 (the system SHALL provide a comparison model). The
 * classification is used by the UI to render the diff
 * comparison model and by the merge command factory to
 * decide whether auto-merge is safe.
 */
public enum ConflictFieldKind {
    SAME,
    DIFFERENT,
    ONLY_BASE,
    ONLY_INCOMING,
    ONLY_LOCAL;

    public static ConflictFieldKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("conflictFieldKind must not be null");
        }
        return ConflictFieldKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
