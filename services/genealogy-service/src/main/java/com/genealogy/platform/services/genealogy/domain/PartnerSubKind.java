package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of partner sub-kinds. Mirrors
 * {@code contracts/genealogy/relationship-graph-policy.yaml::
 * spec.partnerSubKinds}. The sub-kind is required when the
 * kind is {@link RelationshipKind#PARTNER} and forbidden
 * otherwise.
 */
public enum PartnerSubKind {
    MARRIED,
    CIVIL_UNION,
    COMMON_LAW,
    UNMARRIED,
    DIVORCED,
    WIDOWED,
    ANNULLED,
    UNKNOWN;

    public static PartnerSubKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("partnerSubKind is required");
        }
        return PartnerSubKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    public boolean isActive() {
        return this == MARRIED
                || this == CIVIL_UNION
                || this == COMMON_LAW
                || this == UNMARRIED;
    }
}
