package com.genealogy.platform.services.genealogy.projection;

import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set of view kinds the BFF exposes. Mirrors
 * {@code contracts/genealogy/tree-projection-policy.yaml::
 * spec.viewKinds[*].id} and the BFF OpenAPI enum
 * {@code TreeProjection.viewKind}.
 */
public enum ProjectionViewKind {
    PEDIGREE,
    DESCENDANT,
    FAN,
    HOURGLASS,
    FAMILY;

    public String wire() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static ProjectionViewKind fromWire(String wire) {
        Objects.requireNonNull(wire, "viewKind");
        return ProjectionViewKind.valueOf(wire.trim().toUpperCase(Locale.ROOT).replace('-', '_'));
    }
}