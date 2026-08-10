package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set certainty applied to a {@link DateValue} or
 * {@link Place}. Mirrors `design.md` §5.3 (HYPOTHESIS /
 * ASSERTED / VERIFIED / DISPUTED) and
 * {@code contracts/genealogy/date-place-policy.yaml::
 * spec.certainties}.
 */
public enum Certainty {
    HYPOTHESIS,
    ASSERTED,
    VERIFIED,
    DISPUTED;

    public static Certainty fromWire(String wire) {
        if (wire == null) {
            return HYPOTHESIS;
        }
        return Certainty.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
