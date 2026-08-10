package com.genealogy.platform.services.genealogy.domain;

import java.util.Locale;
import java.util.Objects;

/**
 * Closed-set calendar identifier. Mirrors
 * {@code contracts/genealogy/date-place-policy.yaml::spec.calendars}
 * and {@code tree-policy.yaml::spec.supportedCalendars}.
 *
 * <p>Non-Gregorian calendars (e.g. {@link #VIETNAMESE_LUNISOLAR},
 * {@link #JAPANESE}, {@link #HEBREW}) are first-class so a memorial
 * date or an imperial year is preserved verbatim per
 * `requirements.md` R3.1 + R4.1.
 */
public enum CalendarId {
    GREGORIAN,
    JAPANESE,
    VIETNAMESE_LUNISOLAR,
    KOREAN,
    CHINESE_LUNISOLAR,
    ISLAMIC_CIVIL,
    HEBREW,
    FRENCH_REPUBLICAN;

    public static CalendarId fromWire(String wire) {
        Objects.requireNonNull(wire, "calendar");
        return CalendarId.valueOf(wire.trim().toUpperCase(Locale.ROOT));
    }

    public String wire() {
        return name();
    }

    /**
     * True when the calendar's leap rules are driven by a
     * non-Gregorian rule. Recurring memorials on a non-Gregorian
     * calendar need the renderer to consult the calendar's own
     * leap table (see `date-place-policy.yaml::
     * spec.recurringMemorialEnabled`).
     */
    public boolean isLunisolar() {
        return this == VIETNAMESE_LUNISOLAR
                || this == KOREAN
                || this == CHINESE_LUNISOLAR;
    }
}
