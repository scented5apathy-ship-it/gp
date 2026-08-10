package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set of family relationship kinds. Mirrors
 * {@code contracts/genealogy/relationship-graph-policy.yaml::
 * spec.relationshipKinds}.
 *
 * <p>Replaces the legacy denormalised {@code father_id} /
 * {@code mother_id} columns. A relationship MAY carry multiple
 * participants of explicit role (R4.4 / design.md §5.2).
 */
public enum RelationshipKind {
    BIOLOGICAL_PARENT,
    ADOPTIVE_PARENT,
    FOSTER_PARENT,
    STEP_PARENT,
    SURROGATE_PARENT,
    GUARDIAN,
    GODPARENT,
    PARTNER,
    SIBLING,
    HALF_SIBLING,
    STEP_SIBLING,
    CUSTOM;

    public static RelationshipKind fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("relationshipKind is required");
        }
        return RelationshipKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    /** {@code true} when this kind requires a partner sub-kind. */
    public boolean requiresPartnerSubKind() {
        return this == PARTNER;
    }

    /** {@code true} when this kind requires a custom label. */
    public boolean requiresCustomLabel() {
        return this == CUSTOM;
    }
}
