package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set event-level privacy classifications. Mirrors
 * {@code contracts/genealogy/event-claim-policy.yaml::
 * spec.privacyClassifications} (E4.5) and `design.md` §6.3
 * (PRIVATE / UNLISTED / PUBLIC).
 */
public enum EventPrivacy {
    PRIVATE,
    UNLISTED,
    PUBLIC;

    public static EventPrivacy fromWire(String wire) {
        if (wire == null) {
            return PRIVATE;
        }
        return EventPrivacy.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
