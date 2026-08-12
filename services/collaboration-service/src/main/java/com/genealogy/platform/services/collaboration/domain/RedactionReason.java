package com.genealogy.platform.services.collaboration.domain;

import java.util.Locale;

/**
 * Closed-set redaction reason. Mirrors
 * `contracts/collaboration/comments-activity-policy.yaml
 * ::spec.redactionReasons` (E6.4).
 */
public enum RedactionReason {
    LIVING_MINOR,
    DNA_CONSENT_REVOKED,
    RAW_PII_DETECTED,
    VISIBILITY_DEMOTED,
    SUBJECT_REMOVED,
    CORRECTION_APPLIED;

    public static RedactionReason fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("redactionReason must not be null");
        }
        return RedactionReason.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
