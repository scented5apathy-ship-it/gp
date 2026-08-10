package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of participant roles attached to a
 * {@link Relationship}. Mirrors
 * {@code contracts/genealogy/relationship-graph-policy.yaml::
 * spec.participantRoles}.
 *
 * <p>Role ↔ kind mapping is enforced by
 * {@link RelationshipInvariants}: e.g. BIOLOGICAL_PARENT
 * MUST have at least one PARENT and at least one CHILD;
 * PARTNER MUST have at least two PARTNER participants.
 */
public enum ParticipantRole {
    PARENT,
    CHILD,
    SIBLING,
    PARTNER,
    SUBJECT,
    GUARDIAN,
    WARD;

    public static ParticipantRole fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("participantRole is required");
        }
        return ParticipantRole.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
