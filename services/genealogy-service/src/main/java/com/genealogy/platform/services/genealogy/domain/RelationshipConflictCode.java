package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of conflict reason codes emitted by
 * {@link RelationshipInvariants}. The renderer / audit
 * consumer routes on this symbol so the closed-set is the
 * single source of truth for chronological warnings.
 */
public enum RelationshipConflictCode {
    OVERLAPPING_PARENTAL_VALIDITY,
    PARTNER_OVERLAP_WITH_ACTIVE,
    SELF_LINK,
    CYCLE,
    PARTICIPANT_ROLE_MISMATCH,
    PARTNER_REQUIRES_TWO_PARTICIPANTS,
    PARENT_REQUIRES_AT_LEAST_ONE_CHILD,
    PARENT_REQUIRES_AT_LEAST_ONE_PARENT,
    CUSTOM_REQUIRES_LABEL,
    PARTNER_REQUIRES_SUB_KIND,
    SUB_KIND_FORBIDDEN_ON_NON_PARTNER,
    UNKNOWN_RESERVED_FOR_UNKNOWN_PARTICIPANT;

    public String wire() {
        return name();
    }

    public static RelationshipConflictCode fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("conflictCode is required");
        }
        return RelationshipConflictCode.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }
}
