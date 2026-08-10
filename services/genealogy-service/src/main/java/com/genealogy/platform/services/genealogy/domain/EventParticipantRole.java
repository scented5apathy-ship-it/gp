package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set event-participant roles. Mirrors
 * {@code contracts/genealogy/event-claim-policy.yaml::
 * spec.eventParticipantRoles} (E4.5).
 *
 * <p>For events the platform adds {@link #WITNESS},
 * {@link #OFFICIANT} and {@link #INFORMANT} on top of the
 * Relationship vocabulary (E4.4). A wedding has WITNESS + the
 * PARTNER couple + OFFICIANT; a christening has SUBJECT +
 * PARENT + GODPARENT-WITNESS; a death record carries an
 * INFORMANT (often a relative who told the registrar).
 */
public enum EventParticipantRole {
    SUBJECT,
    PARENT,
    CHILD,
    SIBLING,
    PARTNER,
    GUARDIAN,
    WARD,
    WITNESS,
    OFFICIANT,
    INFORMANT;

    public static EventParticipantRole fromWire(String wire) {
        if (wire == null) {
            return SUBJECT;
        }
        return EventParticipantRole.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
