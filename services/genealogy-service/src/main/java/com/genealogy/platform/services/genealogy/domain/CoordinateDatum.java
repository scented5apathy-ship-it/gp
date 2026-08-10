package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set coordinate datum. Only WGS84 is supported in this
 * release; historical datums are out of scope for E4.3.
 */
public enum CoordinateDatum {
    WGS84;

    public static CoordinateDatum fromWire(String wire) {
        if (wire == null) {
            return WGS84;
        }
        return CoordinateDatum.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
