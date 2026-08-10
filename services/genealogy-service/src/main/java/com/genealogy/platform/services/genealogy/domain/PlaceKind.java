package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set place kind. Mirrors {@code contracts/genealogy/
 * date-place-policy.yaml::spec.placeKinds}. The renderer routes on
 * the kind to pick the correct hierarchy depth + icon.
 */
public enum PlaceKind {
    COUNTRY,
    REGION,
    LOCALITY,
    STREET,
    BUILDING,
    CEMETERY,
    RELIGIOUS_SITE,
    HOSPITAL,
    UNKNOWN;

    public static PlaceKind fromWire(String wire) {
        if (wire == null) {
            return UNKNOWN;
        }
        return PlaceKind.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
