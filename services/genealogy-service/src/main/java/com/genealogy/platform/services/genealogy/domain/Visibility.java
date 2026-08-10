package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set visibility for a {@link Tree} aggregate. Mirrors
 * {@code contracts/genealogy/tree-policy.yaml::spec.visibilities}
 * and {@code design.md} §6.3.
 */
public enum Visibility {
    PRIVATE,
    UNLISTED,
    PUBLIC;

    public static Visibility fromWire(String wire) {
        if (wire == null) {
            throw new IllegalArgumentException("visibility is required");
        }
        return Visibility.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
