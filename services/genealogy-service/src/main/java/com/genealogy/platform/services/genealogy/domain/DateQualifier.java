package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;

/**
 * Closed-set qualifier on a {@link DateValue}. Mirrors
 * {@code contracts/genealogy/date-place-policy.yaml::
 * spec.dateQualifiers} and `design.md` §5.3 (temporal,
 * certainty, calendar).
 *
 * <p>The renderer MUST NOT infer certainty from a missing
 * qualifier: {@link #UNKNOWN} is explicit and renders as "…"
 * rather than silently picking {@link #ABOUT} or {@link #EXACT}.
 */
public enum DateQualifier {
    EXACT,
    ABOUT,
    BEFORE,
    AFTER,
    BETWEEN,
    UNKNOWN;

    public static DateQualifier fromWire(String wire) {
        if (wire == null) {
            return UNKNOWN;
        }
        return DateQualifier.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }
}
