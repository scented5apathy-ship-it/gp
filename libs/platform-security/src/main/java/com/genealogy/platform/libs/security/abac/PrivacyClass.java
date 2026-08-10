package com.genealogy.platform.libs.security.abac;

import java.util.Objects;

/**
 * Privacy class a resource carries, derived from
 * {@code privacy-and-legal-gate.md} §5 data inventory.
 *
 * <p>The closed set is intentionally narrow: every other class is
 * treated as {@link #PRIVATE} for ABAC purposes (deny-by-default)
 * until a new entry is signed by the DPO and added to the
 * corresponding {@code privacy-class} registry.
 */
public enum PrivacyClass {
    PUBLIC,
    UNLISTED,
    PRIVATE,
    SENSITIVE,
    GENETIC_RAW,
    GENETIC_DERIVED;

    /**
     * Returns {@code true} when the class is genetic data. ABAC
     * requires a dedicated DNA namespace in OpenFGA and an explicit
     * consent record before any read.
     */
    public boolean isGenetic() {
        return this == GENETIC_RAW || this == GENETIC_DERIVED;
    }

    /**
     * Returns {@code true} when the class must be redacted from any
     * log / metric / public projection, regardless of ABAC allow.
     */
    public boolean isRedactEverywhere() {
        return this == SENSITIVE || this == GENETIC_RAW;
    }

    public static PrivacyClass fromString(String value) {
        Objects.requireNonNull(value, "value");
        try {
            return PrivacyClass.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("unknown privacy class: " + value, ex);
        }
    }
}
