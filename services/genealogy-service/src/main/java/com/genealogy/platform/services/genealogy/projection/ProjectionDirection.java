package com.genealogy.platform.services.genealogy.projection;

import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set of cardinal directions the tree projection accepts.
 * Mirrors
 * {@code contracts/genealogy/tree-projection-policy.yaml::
 * spec.directions} and the BFF OpenAPI enum
 * {@code TreeProjectionDirection}.
 */
public enum ProjectionDirection {
    ANCESTORS,
    DESCENDANTS,
    BOTH,
    SPOUSE_FAN;

    public String wire() {
        return name();
    }

    public static ProjectionDirection fromWire(String wire) {
        Objects.requireNonNull(wire, "direction");
        return ProjectionDirection.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }
}